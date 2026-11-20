package com.example.spotifylite.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.spotifylite.model.DownloadJobRecord;
import com.example.spotifylite.model.Playlist;
import com.example.spotifylite.model.Track;
import com.example.spotifylite.utils.AudioFileUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrackRepository {
    private static final String PREFS_NAME = "spotifylite_library_store";
    private static final String KEY_TRACKS = "tracks_json";
    private static final String KEY_JOBS = "download_jobs_json";
    private static final String KEY_PLAYLISTS = "playlists_json";
    private static final int MAX_JOB_HISTORY = 30;

    private static volatile TrackRepository instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final MutableLiveData<List<Track>> tracksLiveData = new MutableLiveData<>();
    private final MutableLiveData<Map<String, DownloadJobRecord>> jobsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Playlist>> playlistsLiveData = new MutableLiveData<>();

    private TrackRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        tracksLiveData.postValue(loadTracksInternal());
        jobsLiveData.postValue(loadJobsMapInternal());
        playlistsLiveData.postValue(loadPlaylistsInternal());
    }

    public static TrackRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (TrackRepository.class) {
                if (instance == null) {
                    instance = new TrackRepository(context);
                }
            }
        }
        return instance;
    }

    public LiveData<List<Track>> getTracksLiveData() {
        return tracksLiveData;
    }

    public LiveData<Map<String, DownloadJobRecord>> getJobsLiveData() {
        return jobsLiveData;
    }

    public synchronized List<Track> getTracksSnapshot() {
        return loadTracksInternal();
    }

    @Nullable
    public synchronized Track getTrackById(String trackId) {
        if (trackId == null) {
            return null;
        }
        for (Track track : loadTracksInternal()) {
            if (trackId.equals(track.id)) {
                return track;
            }
        }
        return null;
    }

    public synchronized void addTrack(Track track) {
        List<Track> list = loadTracksInternal();
        list.add(0, track);
        saveTracksInternal(list);
        tracksLiveData.postValue(new ArrayList<>(list));
    }

    public synchronized void deleteTrack(Track track) {
        AudioFileUtils.deleteAudio(appContext, track.contentUri);
        List<Track> list = loadTracksInternal();
        Iterator<Track> iterator = list.iterator();
        while (iterator.hasNext()) {
            Track item = iterator.next();
            if (track.id != null && track.id.equals(item.id)) {
                iterator.remove();
            }
        }
        saveTracksInternal(list);
        tracksLiveData.postValue(new ArrayList<>(list));
        if (track.id != null) {
            removeTrackFromAllPlaylistsInternal(track.id);
            LyricsRepository.getInstance(appContext).clearLyricsForTrack(track.id);
        }
    }

    public synchronized void pruneMissingFiles() {
        List<Track> list = loadTracksInternal();
        boolean changed = false;
        List<String> removedIds = new ArrayList<>();
        Iterator<Track> iterator = list.iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            if (!AudioFileUtils.audioExists(appContext, track.contentUri)) {
                iterator.remove();
                changed = true;
                if (track.id != null) {
                    removedIds.add(track.id);
                }
            }
        }
        if (changed) {
            saveTracksInternal(list);
            tracksLiveData.postValue(new ArrayList<>(list));
            for (String removedId : removedIds) {
                removeTrackFromAllPlaylistsInternal(removedId);
                LyricsRepository.getInstance(appContext).clearLyricsForTrack(removedId);
            }
        }
    }

    public LiveData<List<Playlist>> getPlaylistsLiveData() {
        return playlistsLiveData;
    }

    public synchronized List<Playlist> getPlaylistsSnapshot() {
        return loadPlaylistsInternal();
    }

    @Nullable
    public synchronized Playlist getPlaylistById(@Nullable String playlistId) {
        if (playlistId == null) {
            return null;
        }
        for (Playlist playlist : loadPlaylistsInternal()) {
            if (playlistId.equals(playlist.id)) {
                return playlist;
            }
        }
        return null;
    }

    public synchronized Playlist createPlaylist(String name) {
        String trimmed = name != null ? name.trim() : "";
        if (trimmed.isEmpty()) {
            trimmed = "Untitled Playlist";
        }
        long now = System.currentTimeMillis();
        Playlist playlist = new Playlist(
                UUID.randomUUID().toString(),
                trimmed,
                new ArrayList<>(),
                now,
                now
        );
        List<Playlist> list = loadPlaylistsInternal();
        list.add(0, playlist);
        savePlaylistsInternal(list);
        playlistsLiveData.postValue(new ArrayList<>(list));
        return playlist;
    }

    public synchronized void renamePlaylist(String playlistId, String newName) {
        String trimmed = newName != null ? newName.trim() : "";
        if (playlistId == null || trimmed.isEmpty()) {
            return;
        }
        List<Playlist> list = loadPlaylistsInternal();
        boolean changed = false;
        for (Playlist playlist : list) {
            if (playlistId.equals(playlist.id)) {
                playlist.name = trimmed;
                playlist.updatedAtMs = System.currentTimeMillis();
                changed = true;
                break;
            }
        }
        if (changed) {
            savePlaylistsInternal(list);
            playlistsLiveData.postValue(new ArrayList<>(list));
        }
    }

    public synchronized void deletePlaylist(String playlistId) {
        if (playlistId == null) {
            return;
        }
        List<Playlist> list = loadPlaylistsInternal();
        boolean changed = list.removeIf(p -> playlistId.equals(p.id));
        if (changed) {
            savePlaylistsInternal(list);
            playlistsLiveData.postValue(new ArrayList<>(list));
        }
    }

    public synchronized void reorderPlaylists(List<String> orderedPlaylistIds) {
        if (orderedPlaylistIds == null || orderedPlaylistIds.isEmpty()) {
            return;
        }
        List<Playlist> current = loadPlaylistsInternal();
        Map<String, Playlist> byId = new HashMap<>();
        for (Playlist p : current) {
            if (p.id != null) {
                byId.put(p.id, p);
            }
        }
        List<Playlist> reordered = new ArrayList<>();
        for (String id : orderedPlaylistIds) {
            Playlist p = byId.remove(id);
            if (p != null) {
                reordered.add(p);
            }
        }
        reordered.addAll(byId.values());
        savePlaylistsInternal(reordered);
        playlistsLiveData.postValue(new ArrayList<>(reordered));
    }

    public synchronized void reorderPlaylistTracks(String playlistId, List<String> orderedTrackIds) {
        if (playlistId == null || orderedTrackIds == null) {
            return;
        }
        List<Playlist> list = loadPlaylistsInternal();
        boolean changed = false;
        for (Playlist playlist : list) {
            if (playlistId.equals(playlist.id)) {
                playlist.trackIds = new ArrayList<>(orderedTrackIds);
                playlist.updatedAtMs = System.currentTimeMillis();
                changed = true;
                break;
            }
        }
        if (changed) {
            savePlaylistsInternal(list);
            playlistsLiveData.postValue(new ArrayList<>(list));
        }
    }

    public synchronized boolean addTrackToPlaylist(String playlistId, String trackId) {
        if (playlistId == null || trackId == null) {
            return false;
        }
        List<Playlist> list = loadPlaylistsInternal();
        for (Playlist playlist : list) {
            if (playlistId.equals(playlist.id)) {
                if (playlist.trackIds == null) {
                    playlist.trackIds = new ArrayList<>();
                }
                if (playlist.trackIds.contains(trackId)) {
                    return false;
                }
                playlist.trackIds.add(trackId);
                playlist.updatedAtMs = System.currentTimeMillis();
                savePlaylistsInternal(list);
                playlistsLiveData.postValue(new ArrayList<>(list));
                return true;
            }
        }
        return false;
    }

    public synchronized void removeTrackFromPlaylist(String playlistId, String trackId) {
        if (playlistId == null || trackId == null) {
            return;
        }
        List<Playlist> list = loadPlaylistsInternal();
        boolean changed = false;
        for (Playlist playlist : list) {
            if (playlistId.equals(playlist.id) && playlist.trackIds != null) {
                if (playlist.trackIds.remove(trackId)) {
                    playlist.updatedAtMs = System.currentTimeMillis();
                    changed = true;
                }
                break;
            }
        }
        if (changed) {
            savePlaylistsInternal(list);
            playlistsLiveData.postValue(new ArrayList<>(list));
        }
    }

    public synchronized List<Track> resolvePlaylistTracks(@Nullable Playlist playlist) {
        if (playlist == null || playlist.trackIds == null || playlist.trackIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Track> byId = new HashMap<>();
        for (Track track : loadTracksInternal()) {
            if (track.id != null) {
                byId.put(track.id, track);
            }
        }
        List<Track> resolved = new ArrayList<>();
        for (String id : playlist.trackIds) {
            Track t = byId.get(id);
            if (t != null) {
                resolved.add(t);
            }
        }
        return resolved;
    }

    public synchronized void saveJobRecord(DownloadJobRecord record) {
        List<DownloadJobRecord> list = loadJobsListInternal();
        list.removeIf(existing -> record.workId.equals(existing.workId));
        list.add(0, record);
        if (list.size() > MAX_JOB_HISTORY) {
            list = new ArrayList<>(list.subList(0, MAX_JOB_HISTORY));
        }
        saveJobsListInternal(list);
        jobsLiveData.postValue(toMap(list));
    }

    public synchronized void removeJobRecords(List<String> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return;
        }
        List<DownloadJobRecord> list = loadJobsListInternal();
        list.removeIf(existing -> workIds.contains(existing.workId));
        saveJobsListInternal(list);
        jobsLiveData.postValue(toMap(list));
    }

    @Nullable
    public synchronized DownloadJobRecord getJobRecord(String workId) {
        for (DownloadJobRecord record : loadJobsListInternal()) {
            if (workId.equals(record.workId)) {
                return record;
            }
        }
        return null;
    }

    private List<Track> loadTracksInternal() {
        String json = prefs.getString(KEY_TRACKS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<Track>>() {
        }.getType();
        List<Track> parsed = gson.fromJson(json, type);
        return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
    }

    private void saveTracksInternal(List<Track> tracks) {
        prefs.edit().putString(KEY_TRACKS, gson.toJson(tracks)).apply();
    }

    private List<DownloadJobRecord> loadJobsListInternal() {
        String json = prefs.getString(KEY_JOBS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<DownloadJobRecord>>() {
        }.getType();
        List<DownloadJobRecord> parsed = gson.fromJson(json, type);
        return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
    }

    private Map<String, DownloadJobRecord> loadJobsMapInternal() {
        return toMap(loadJobsListInternal());
    }

    private Map<String, DownloadJobRecord> toMap(List<DownloadJobRecord> list) {
        Map<String, DownloadJobRecord> map = new HashMap<>();
        for (DownloadJobRecord record : list) {
            if (record.workId != null) {
                map.put(record.workId, record);
            }
        }
        return map;
    }

    private void saveJobsListInternal(List<DownloadJobRecord> jobs) {
        prefs.edit().putString(KEY_JOBS, gson.toJson(jobs)).apply();
    }

    private List<Playlist> loadPlaylistsInternal() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<Playlist>>() {
        }.getType();
        List<Playlist> parsed = gson.fromJson(json, type);
        return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
    }

    private void savePlaylistsInternal(List<Playlist> playlists) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply();
    }

    private void removeTrackFromAllPlaylistsInternal(String trackId) {
        List<Playlist> playlists = loadPlaylistsInternal();
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Playlist playlist : playlists) {
            if (playlist.trackIds != null && playlist.trackIds.remove(trackId)) {
                playlist.updatedAtMs = now;
                changed = true;
            }
        }
        if (changed) {
            savePlaylistsInternal(playlists);
            playlistsLiveData.postValue(new ArrayList<>(playlists));
        }
    }
}

