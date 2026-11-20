package com.example.spotifylite.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Playlist {
    public String id;
    public String name;
    public List<String> trackIds;
    public long createdAtMs;
    public long updatedAtMs;

    public Playlist() {
        this.trackIds = new ArrayList<>();
    }

    public Playlist(String id, String name, List<String> trackIds, long createdAtMs, long updatedAtMs) {
        this.id = id;
        this.name = name;
        this.trackIds = trackIds != null ? new ArrayList<>(trackIds) : new ArrayList<>();
        this.createdAtMs = createdAtMs;
        this.updatedAtMs = updatedAtMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Playlist)) return false;
        Playlist playlist = (Playlist) o;
        return createdAtMs == playlist.createdAtMs
                && updatedAtMs == playlist.updatedAtMs
                && Objects.equals(id, playlist.id)
                && Objects.equals(name, playlist.name)
                && Objects.equals(trackIds, playlist.trackIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, trackIds, createdAtMs, updatedAtMs);
    }
}

