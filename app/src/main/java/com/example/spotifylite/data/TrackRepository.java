package com.example.spotifylite.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.spotifylite.model.DownloadJobRecord;
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

public final class TrackRepository {
    private static final String PREFS_NAME = "spotifylite_library_store";
    private static final String KEY_TRACKS = "tracks_json";
    private static final String KEY_JOBS = "download_jobs_json";
    private static final int MAX_JOB_HISTORY = 30;

    private static volatile TrackRepository instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final MutableLiveData<List<Track>> tracksLiveData = new MutableLiveData<>();
    private final MutableLiveData<Map<String, DownloadJobRecord>> jobsLiveData = new MutableLiveData<>();

    private TrackRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        tracksLiveData.postValue(loadTracksInternal());
        jobsLiveData.postValue(loadJobsMapInternal());
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
    }

    public synchronized void pruneMissingFiles() {
        List<Track> list = loadTracksInternal();
        boolean changed = false;
        Iterator<Track> iterator = list.iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            if (!AudioFileUtils.audioExists(appContext, track.contentUri)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            saveTracksInternal(list);
            tracksLiveData.postValue(new ArrayList<>(list));
        }
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
}

