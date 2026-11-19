package com.example.spotifylite.utils;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class LinkResolver {
    public static final String PLATFORM_DIRECT = "DIRECT";
    public static final String PLATFORM_YOUTUBE = "YOUTUBE";
    public static final String PLATFORM_SPOTIFY = "SPOTIFY";
    public static final String PLATFORM_SOUNDCLOUD = "SOUNDCLOUD";
    public static final String PLATFORM_OTHER = "OTHER";

    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".m4a", ".wav", ".ogg", ".opus", ".aac", ".flac", ".webm"
    };

    private static final Pattern META_DESC_PATTERN = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:description|description)[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private LinkResolver() {
    }

    public static final class ResolvedAudio {
        public final String streamUrl;
        public final String title;
        public final String artist;
        @Nullable
        public final String thumbnailUrl;
        public final String extension;
        public final String mimeType;
        public final long durationMs;
        public final String platform;

        public ResolvedAudio(
                String streamUrl,
                String title,
                String artist,
                @Nullable String thumbnailUrl,
                String extension,
                String mimeType,
                long durationMs,
                String platform
        ) {
            this.streamUrl = streamUrl;
            this.title = title;
            this.artist = artist;
            this.thumbnailUrl = thumbnailUrl;
            this.extension = extension;
            this.mimeType = mimeType;
            this.durationMs = durationMs;
            this.platform = platform;
        }
    }

    public static String detectPlatform(@Nullable String rawUrl) {
        if (rawUrl == null) {
            return PLATFORM_OTHER;
        }
        String trimmed = rawUrl.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return PLATFORM_OTHER;
        }
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.US) : "";
            String path = uri.getPath() != null ? uri.getPath().toLowerCase(Locale.US) : "";

            if (host.contains("youtube.com") || host.equals("youtu.be") || host.endsWith(".youtu.be")) {
                return PLATFORM_YOUTUBE;
            }
            if (host.contains("spotify.com") || host.equals("spotify.link") || host.endsWith(".spotify.link")) {
                return PLATFORM_SPOTIFY;
            }
            if (host.contains("soundcloud.com") || host.equals("snd.sc")) {
                return PLATFORM_SOUNDCLOUD;
            }
            for (String ext : AUDIO_EXTENSIONS) {
                if (path.endsWith(ext)) {
                    return PLATFORM_DIRECT;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return PLATFORM_OTHER;
        }
        return PLATFORM_OTHER;
    }

    public static boolean isValidHttpUrl(@Nullable String rawUrl) {
        if (rawUrl == null) {
            return false;
        }
        String trimmed = rawUrl.trim().toLowerCase(Locale.US);
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                && trimmed.length() > 10;
    }

    public static String sanitizeFileName(@Nullable String name, String fallbackExtension) {
        String base = (name == null || name.trim().isEmpty())
                ? "track_" + System.currentTimeMillis()
                : name.trim();
        base = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        if (base.isEmpty()) {
            base = "track_" + System.currentTimeMillis();
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).trim();
        }
        String normalizedExt = fallbackExtension.startsWith(".")
                ? fallbackExtension.toLowerCase(Locale.US)
                : "." + fallbackExtension.toLowerCase(Locale.US);
        if (!base.toLowerCase(Locale.US).endsWith(normalizedExt)) {
            base = base + normalizedExt;
        }
        return base;
    }

    public static String mimeTypeForExtension(String extension) {
        String ext = extension.toLowerCase(Locale.US);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        switch (ext) {
            case "mp3":
                return "audio/mpeg";
            case "m4a":
            case "mp4":
                return "audio/mp4";
            case "wav":
                return "audio/wav";
            case "ogg":
                return "audio/ogg";
            case "opus":
                return "audio/opus";
            case "aac":
                return "audio/aac";
            case "flac":
                return "audio/flac";
            case "webm":
                return "audio/webm";
            default:
                return "audio/mpeg";
        }
    }

    public static String extensionFromMime(@Nullable String mimeType, String fallback) {
        if (mimeType == null) {
            return fallback;
        }
        String lower = mimeType.toLowerCase(Locale.US);
        if (lower.contains("mpeg") || lower.contains("mp3")) return "mp3";
        if (lower.contains("mp4") || lower.contains("m4a")) return "m4a";
        if (lower.contains("webm")) return "webm";
        if (lower.contains("ogg")) return "ogg";
        if (lower.contains("opus")) return "opus";
        if (lower.contains("wav")) return "wav";
        if (lower.contains("aac")) return "aac";
        if (lower.contains("flac")) return "flac";
        return fallback;
    }

    public static String extensionFromUrl(String url, String fallback) {
        try {
            String path = URI.create(url).getPath();
            if (path != null) {
                String lower = path.toLowerCase(Locale.US);
                for (String ext : AUDIO_EXTENSIONS) {
                    if (lower.endsWith(ext)) {
                        return ext.substring(1);
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
        return fallback;
    }

    public static String titleFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && !path.isEmpty()) {
                int slash = path.lastIndexOf('/');
                String segment = slash >= 0 ? path.substring(slash + 1) : path;
                segment = URLDecoder.decode(segment, StandardCharsets.UTF_8.name());
                int dot = segment.lastIndexOf('.');
                if (dot > 0) {
                    segment = segment.substring(0, dot);
                }
                segment = segment.replace('_', ' ').replace('-', ' ').trim();
                if (!segment.isEmpty()) {
                    return segment;
                }
            }
        } catch (Exception ignored) {
            return "Audio Track";
        }
        return "Audio Track";
    }

    public static ResolvedAudio resolve(String rawUrl) throws Exception {
        String url = rawUrl.trim();
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("youtube.com/playlist")
                || lower.contains("spotify.com/playlist")
                || lower.contains("spotify.com/album")) {
            throw new IOException("Playlist and album links are not supported. Paste a single track link.");
        }

        String platform = detectPlatform(url);

        if (PLATFORM_DIRECT.equals(platform)) {
            return resolveDirect(url, null);
        }
        if (PLATFORM_SPOTIFY.equals(platform)) {
            return resolveSpotify(url);
        }
        if (PLATFORM_YOUTUBE.equals(platform) || PLATFORM_SOUNDCLOUD.equals(platform)) {
            return resolveWithNewPipe(url, platform, null, null, null);
        }

        // Probe unknown links: download directly if the server serves audio/*, otherwise try NewPipeExtractor.
        String probedMime = probeAudioContentType(url);
        if (probedMime != null) {
            return resolveDirect(url, probedMime);
        }
        return resolveWithNewPipe(url, PLATFORM_OTHER, null, null, null);
    }

    private static ResolvedAudio resolveDirect(String url, @Nullable String probedMime) {
        String ext = extensionFromUrl(url, extensionFromMime(probedMime, "mp3"));
        String mime = probedMime != null ? probedMime : mimeTypeForExtension(ext);
        String title = titleFromUrl(url);
        return new ResolvedAudio(
                url,
                title,
                "Direct Link",
                null,
                ext,
                mime,
                0L,
                PLATFORM_DIRECT
        );
    }

    private static ResolvedAudio resolveSpotify(String spotifyUrl) throws Exception {
        OkHttpClient client = OkHttpNewPipeDownloader.getInstance().getHttpClient();
        String encoded = URLEncoder.encode(spotifyUrl, StandardCharsets.UTF_8.name());
        String oembedUrl = "https://open.spotify.com/oembed?url=" + encoded;

        String trackTitle;
        String thumbnailUrl = null;
        Request oembedRequest = new Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", OkHttpNewPipeDownloader.USER_AGENT)
                .build();

        try (Response response = client.newCall(oembedRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Could not read Spotify track metadata (HTTP " + response.code() + ").");
            }
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (!json.has("title") || json.get("title").isJsonNull()) {
                throw new IOException("Spotify link did not return a track title.");
            }
            trackTitle = json.get("title").getAsString().trim();
            if (json.has("thumbnail_url") && !json.get("thumbnail_url").isJsonNull()) {
                thumbnailUrl = json.get("thumbnail_url").getAsString();
            }
        }

        String artist = fetchSpotifyArtistBestEffort(client, spotifyUrl);
        String searchQuery = artist != null && !artist.isEmpty()
                ? trackTitle + " " + artist + " audio"
                : trackTitle + " official audio";

        SearchInfo searchInfo = SearchInfo.getInfo(
                ServiceList.YouTube,
                ServiceList.YouTube.getSearchQHFactory().fromQuery(searchQuery)
        );
        String matchedYoutubeUrl = null;
        for (InfoItem item : searchInfo.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                matchedYoutubeUrl = item.getUrl();
                break;
            }
        }
        if (matchedYoutubeUrl == null) {
            throw new IOException("No matching audio stream found for Spotify track: " + trackTitle);
        }

        return resolveWithNewPipe(
                matchedYoutubeUrl,
                PLATFORM_SPOTIFY,
                trackTitle,
                artist,
                thumbnailUrl
        );
    }

    @Nullable
    private static String fetchSpotifyArtistBestEffort(OkHttpClient client, String spotifyUrl) {
        Request pageRequest = new Request.Builder()
                .url(spotifyUrl)
                .header("User-Agent", OkHttpNewPipeDownloader.USER_AGENT)
                .build();
        try (Response response = client.newCall(pageRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            String html = response.body().string();
            Matcher matcher = META_DESC_PATTERN.matcher(html);
            if (matcher.find()) {
                // Spotify og:description follows "Artist · Song · Year".
                String desc = matcher.group(1);
                if (desc != null && desc.contains("·")) {
                    return desc.split("·")[0].trim();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static ResolvedAudio resolveWithNewPipe(
            String url,
            String platform,
            @Nullable String overrideTitle,
            @Nullable String overrideArtist,
            @Nullable String overrideThumb
    ) throws Exception {
        StreamInfo info = StreamInfo.getInfo(url);
        String streamUrl = null;
        String extension = "m4a";
        String mimeType = "audio/mp4";

        List<AudioStream> audioStreams = info.getAudioStreams();
        AudioStream selectedAudio = (audioStreams != null && !audioStreams.isEmpty())
                ? pickBestAudioStream(audioStreams)
                : null;

        if (selectedAudio != null && selectedAudio.getContent() != null && !selectedAudio.getContent().isEmpty()) {
            streamUrl = selectedAudio.getContent();
            MediaFormat format = selectedAudio.getFormat();
            if (format != null && format.suffix != null && !format.suffix.isEmpty()) {
                extension = format.suffix.toLowerCase(Locale.US);
            }
            mimeType = (format != null && format.mimeType != null && !format.mimeType.isEmpty())
                    ? format.mimeType
                    : mimeTypeForExtension(extension);
        } else {
            // Fall back to progressive muxed MP4 streams (e.g. YouTube itag 18) when audio-only streams are blocked.
            List<VideoStream> videoStreams = info.getVideoStreams();
            VideoStream fallbackMuxed = (videoStreams != null && !videoStreams.isEmpty())
                    ? pickSmallestMuxedStream(videoStreams)
                    : null;
            if (fallbackMuxed != null && fallbackMuxed.getContent() != null && !fallbackMuxed.getContent().isEmpty()) {
                streamUrl = fallbackMuxed.getContent();
                extension = "m4a";
                mimeType = "audio/mp4";
            }
        }

        if (streamUrl == null) {
            String detail = firstExtractorError(info);
            if (detail != null) {
                throw new IOException("No downloadable audio streams found (" + detail + ").");
            }
            throw new IOException("No downloadable audio streams found for this link.");
        }

        String title = (overrideTitle != null && !overrideTitle.isEmpty())
                ? overrideTitle
                : (info.getName() != null && !info.getName().isEmpty() ? info.getName() : "Audio Track");
        String rawArtist = (overrideArtist != null && !overrideArtist.isEmpty())
                ? overrideArtist
                : (info.getUploaderName() != null && !info.getUploaderName().isEmpty()
                ? info.getUploaderName()
                : "Unknown Artist");
        String artist = FormatUtils.cleanArtistName(rawArtist);
        if (artist.isEmpty()) {
            artist = "Unknown Artist";
        }

        String thumbnailUrl = overrideThumb;
        if ((thumbnailUrl == null || thumbnailUrl.isEmpty()) && info.getThumbnails() != null) {
            List<Image> thumbs = info.getThumbnails();
            if (!thumbs.isEmpty()) {
                thumbnailUrl = thumbs.get(thumbs.size() - 1).getUrl();
            }
        }

        long durationMs = info.getDuration() > 0 ? info.getDuration() * 1000L : 0L;
        return new ResolvedAudio(
                streamUrl,
                title,
                artist,
                thumbnailUrl,
                extension,
                mimeType,
                durationMs,
                platform
        );
    }

    @Nullable
    private static VideoStream pickSmallestMuxedStream(List<VideoStream> streams) {
        VideoStream mp4 = streams.stream()
                .filter(s -> s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP)
                .filter(s -> s.getFormat() == MediaFormat.MPEG_4)
                .min(Comparator.comparingInt(s -> parseResolutionHeight(s.getResolution())))
                .orElse(null);
        if (mp4 != null) {
            return mp4;
        }
        return streams.stream()
                .filter(s -> s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP)
                .min(Comparator.comparingInt(s -> parseResolutionHeight(s.getResolution())))
                .orElse(null);
    }

    private static int parseResolutionHeight(@Nullable String resolution) {
        if (resolution == null) {
            return Integer.MAX_VALUE;
        }
        String digits = resolution.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    @Nullable
    private static String firstExtractorError(StreamInfo info) {
        List<Throwable> errors = info.getErrors();
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        for (Throwable error : errors) {
            if (error != null && error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                return error.getMessage().trim();
            }
        }
        return errors.get(0).getClass().getSimpleName();
    }

    @Nullable
    private static AudioStream pickBestAudioStream(List<AudioStream> streams) {
        // Prefer progressive HTTP M4A streams so Android MediaStore and ExoPlayer play without remuxing.
        AudioStream bestM4a = streams.stream()
                .filter(s -> s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP)
                .filter(s -> s.getFormat() == MediaFormat.M4A)
                .max(Comparator.comparingInt(AudioStream::getAverageBitrate))
                .orElse(null);
        if (bestM4a != null) {
            return bestM4a;
        }

        AudioStream bestProgressive = streams.stream()
                .filter(s -> s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP)
                .max(Comparator.comparingInt(AudioStream::getAverageBitrate))
                .orElse(null);
        if (bestProgressive != null) {
            return bestProgressive;
        }

        return streams.stream()
                .max(Comparator.comparingInt(AudioStream::getAverageBitrate))
                .orElse(null);
    }

    @Nullable
    private static String probeAudioContentType(String url) {
        OkHttpClient client = OkHttpNewPipeDownloader.getInstance().getHttpClient();
        Request headRequest = new Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", OkHttpNewPipeDownloader.USER_AGENT)
                .build();
        try (Response response = client.newCall(headRequest).execute()) {
            if (response.isSuccessful()) {
                String contentType = response.header("Content-Type");
                if (contentType != null && contentType.toLowerCase(Locale.US).startsWith("audio/")) {
                    int semi = contentType.indexOf(';');
                    return semi > 0 ? contentType.substring(0, semi).trim() : contentType.trim();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}

