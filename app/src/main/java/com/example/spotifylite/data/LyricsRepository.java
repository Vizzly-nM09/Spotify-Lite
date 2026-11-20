package com.example.spotifylite.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.spotifylite.model.Track;
import com.example.spotifylite.model.TrackLyrics;
import com.example.spotifylite.utils.FormatUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class LyricsRepository {
    private static final String PREFS_NAME = "spotifylite_lyrics_cache";
    private static final String KEY_PREFIX = "lyrics_";
    private static final String LRCLIB_BASE = "https://lrclib.net/api";
    private static final String USER_AGENT = "SpotifyLite/1.0 (Android Coursework)";

    private static volatile LyricsRepository instance;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final OkHttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LyricsCallback {
        void onResult(@Nullable TrackLyrics lyrics, boolean networkError);
    }

    private LyricsRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static LyricsRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (LyricsRepository.class) {
                if (instance == null) {
                    instance = new LyricsRepository(context);
                }
            }
        }
        return instance;
    }

    @Nullable
    public synchronized TrackLyrics getCachedLyrics(@Nullable String trackId) {
        if (trackId == null) {
            return null;
        }
        String json = prefs.getString(KEY_PREFIX + trackId, null);
        if (json == null || json.isEmpty()) {
            return null;
        }
        return gson.fromJson(json, TrackLyrics.class);
    }

    public synchronized void clearLyricsForTrack(@Nullable String trackId) {
        if (trackId != null) {
            prefs.edit().remove(KEY_PREFIX + trackId).apply();
        }
    }

    public void fetchLyrics(Track track, boolean forceRefresh, LyricsCallback callback) {
        if (track == null || track.id == null) {
            mainHandler.post(() -> callback.onResult(null, false));
            return;
        }
        if (!forceRefresh) {
            TrackLyrics cached = getCachedLyrics(track.id);
            if (cached != null) {
                mainHandler.post(() -> callback.onResult(cached, false));
                return;
            }
        }

        executor.execute(() -> {
            try {
                TrackLyrics fetched = queryLrclib(track);
                saveCachedLyrics(fetched);
                mainHandler.post(() -> callback.onResult(fetched, false));
            } catch (IOException e) {
                // Keep any previous cache untouched on transient network failure.
                TrackLyrics fallback = getCachedLyrics(track.id);
                mainHandler.post(() -> callback.onResult(fallback, fallback == null));
            }
        });
    }

    private TrackLyrics queryLrclib(Track track) throws IOException {
        String title = track.title != null ? track.title.trim() : "";
        String artist = FormatUtils.cleanArtistName(track.artist);
        if (title.isEmpty()) {
            return new TrackLyrics(track.id, null, null, true, System.currentTimeMillis());
        }

        if (!artist.isEmpty()) {
            StringBuilder getUrl = new StringBuilder(LRCLIB_BASE)
                    .append("/get?track_name=").append(encode(title))
                    .append("&artist_name=").append(encode(artist));
            if (track.durationMs > 0) {
                getUrl.append("&duration=").append(track.durationMs / 1000L);
            }
            TrackLyrics exact = executeSingleGet(track.id, getUrl.toString());
            if (exact != null && (exact.hasSynced() || exact.hasPlain())) {
                return exact;
            }
        }

        String query = artist.isEmpty() ? title : (title + " " + artist);
        String searchUrl = LRCLIB_BASE + "/search?q=" + encode(query);
        TrackLyrics searched = executeSearchGet(track.id, searchUrl);
        if (searched != null && (searched.hasSynced() || searched.hasPlain())) {
            return searched;
        }

        return new TrackLyrics(track.id, null, null, true, System.currentTimeMillis());
    }

    @Nullable
    private TrackLyrics executeSingleGet(String trackId, String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404 || !response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            JsonObject obj = JsonParser.parseString(body.string()).getAsJsonObject();
            return fromJsonObject(trackId, obj);
        }
    }

    @Nullable
    private TrackLyrics executeSearchGet(String trackId, String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            JsonElement parsed = JsonParser.parseString(body.string());
            if (!parsed.isJsonArray()) {
                return null;
            }
            JsonArray arr = parsed.getAsJsonArray();
            TrackLyrics plainFallback = null;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                TrackLyrics candidate = fromJsonObject(trackId, el.getAsJsonObject());
                if (candidate.hasSynced()) {
                    return candidate;
                }
                if (plainFallback == null && candidate.hasPlain()) {
                    plainFallback = candidate;
                }
            }
            return plainFallback;
        }
    }

    private TrackLyrics fromJsonObject(String trackId, JsonObject obj) {
        String synced = getStringOrNull(obj, "syncedLyrics");
        String plain = getStringOrNull(obj, "plainLyrics");
        boolean empty = (synced == null || synced.trim().isEmpty())
                && (plain == null || plain.trim().isEmpty());
        return new TrackLyrics(trackId, synced, plain, empty, System.currentTimeMillis());
    }

    @Nullable
    private String getStringOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private synchronized void saveCachedLyrics(TrackLyrics lyrics) {
        if (lyrics.trackId != null) {
            prefs.edit().putString(KEY_PREFIX + lyrics.trackId, gson.toJson(lyrics)).apply();
        }
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }
}

