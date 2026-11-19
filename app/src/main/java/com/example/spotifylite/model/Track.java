package com.example.spotifylite.model;

import java.util.Objects;

public class Track {
    public String id;
    public String title;
    public String artist;
    public String sourcePlatform;
    public String sourceUrl;
    public String contentUri;
    public String fileName;
    public String mimeType;
    public long durationMs;
    public long fileSizeBytes;
    public String thumbnailUrl;
    public long downloadedAtMs;

    public Track() {
    }

    public Track(
            String id,
            String title,
            String artist,
            String sourcePlatform,
            String sourceUrl,
            String contentUri,
            String fileName,
            String mimeType,
            long durationMs,
            long fileSizeBytes,
            String thumbnailUrl,
            long downloadedAtMs
    ) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.sourcePlatform = sourcePlatform;
        this.sourceUrl = sourceUrl;
        this.contentUri = contentUri;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.durationMs = durationMs;
        this.fileSizeBytes = fileSizeBytes;
        this.thumbnailUrl = thumbnailUrl;
        this.downloadedAtMs = downloadedAtMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Track)) return false;
        Track track = (Track) o;
        return durationMs == track.durationMs
                && fileSizeBytes == track.fileSizeBytes
                && downloadedAtMs == track.downloadedAtMs
                && Objects.equals(id, track.id)
                && Objects.equals(title, track.title)
                && Objects.equals(artist, track.artist)
                && Objects.equals(sourcePlatform, track.sourcePlatform)
                && Objects.equals(contentUri, track.contentUri)
                && Objects.equals(thumbnailUrl, track.thumbnailUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, artist, contentUri, durationMs, fileSizeBytes);
    }
}

