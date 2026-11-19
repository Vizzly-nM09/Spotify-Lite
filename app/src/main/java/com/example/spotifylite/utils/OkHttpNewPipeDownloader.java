package com.example.spotifylite.utils;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class OkHttpNewPipeDownloader extends Downloader {
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    private static volatile OkHttpNewPipeDownloader instance;
    private final OkHttpClient client;

    private OkHttpNewPipeDownloader() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public static OkHttpNewPipeDownloader getInstance() {
        if (instance == null) {
            synchronized (OkHttpNewPipeDownloader.class) {
                if (instance == null) {
                    instance = new OkHttpNewPipeDownloader();
                }
            }
        }
        return instance;
    }

    public OkHttpClient getHttpClient() {
        return client;
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        String httpMethod = request.httpMethod();
        String url = request.url();
        Map<String, List<String>> headers = request.headers();
        byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend, null);
        } else if ("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)) {
            requestBody = RequestBody.create(new byte[0], MediaType.parse("application/octet-stream"));
        }

        okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9");

        boolean hasCookieHeader = false;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            if ("cookie".equalsIgnoreCase(headerName)) {
                hasCookieHeader = true;
            }
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            if (values.size() == 1) {
                builder.header(headerName, values.get(0));
            } else {
                builder.removeHeader(headerName);
                for (String value : values) {
                    builder.addHeader(headerName, value);
                }
            }
        }

        // Bypass EU/global consent interstitials on YouTube pages.
        if (!hasCookieHeader && url.contains("youtube.com")) {
            builder.header("Cookie", "SOCS=CAI");
        }

        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 429) {
                throw new ReCaptchaException("Rate limited by remote host", url);
            }
            ResponseBody body = response.body();
            String bodyString = body != null ? body.string() : "";
            String latestUrl = response.request().url().toString();
            return new Response(
                    response.code(),
                    response.message(),
                    response.headers().toMultimap(),
                    bodyString,
                    latestUrl
            );
        }
    }
}

