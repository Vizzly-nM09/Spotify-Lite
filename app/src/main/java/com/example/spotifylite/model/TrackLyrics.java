package com.example.spotifylite.model;

public class TrackLyrics {
    public String trackId;
    public String syncedLyrics;
    public String plainLyrics;
    public boolean notFound;
    public long fetchedAtMs;

    public TrackLyrics() {
    }

    public TrackLyrics(
            String trackId,
            String syncedLyrics,
            String plainLyrics,
            boolean notFound,
            long fetchedAtMs
    ) {
        this.trackId = trackId;
        this.syncedLyrics = syncedLyrics;
        this.plainLyrics = plainLyrics;
        this.notFound = notFound;
        this.fetchedAtMs = fetchedAtMs;
    }

    public boolean hasSynced() {
        return syncedLyrics != null && !syncedLyrics.trim().isEmpty();
    }

    public boolean hasPlain() {
        return plainLyrics != null && !plainLyrics.trim().isEmpty();
    }
}

