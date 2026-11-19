package com.example.spotifylite.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.spotifylite.data.TrackRepository;
import com.example.spotifylite.model.Track;
import com.example.spotifylite.utils.AudioFileUtils;
import com.example.spotifylite.utils.LinkResolver;
import com.example.spotifylite.utils.NotificationHelper;

import java.util.UUID;

public class MusicDownloadWorker extends Worker {
    private static final String TAG = "MusicDownloadWorker";

    public static final String WORK_TAG = "music_download";
    public static final String KEY_URL = "key_url";
    public static final String KEY_CUSTOM_TITLE = "key_custom_title";
    public static final String KEY_WIFI_ONLY = "key_wifi_only";

    public static final String KEY_PROGRESS = "key_progress";
    public static final String KEY_STAGE = "key_stage";
    public static final String KEY_TITLE = "key_title";
    public static final String KEY_TRACK_ID = "key_track_id";
    public static final String KEY_SAVED_URI = "key_saved_uri";
    public static final String KEY_ERROR_MESSAGE = "key_error_message";

    public MusicDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context appContext = getApplicationContext();
        int notificationId = Math.abs(getId().hashCode() % 50000) + 2000;

        String url = getInputData().getString(KEY_URL);
        String customTitle = getInputData().getString(KEY_CUSTOM_TITLE);
        String displayLabel = (customTitle != null && !customTitle.trim().isEmpty())
                ? customTitle.trim()
                : "Music track";

        if (!LinkResolver.isValidHttpUrl(url)) {
            String msg = "Invalid music URL.";
            NotificationHelper.showDownloadFailed(appContext, notificationId, displayLabel, msg);
            return Result.failure(errorData(displayLabel, msg));
        }

        try {
            setProgressAsync(new Data.Builder()
                    .putInt(KEY_PROGRESS, 0)
                    .putString(KEY_STAGE, "Resolving audio link...")
                    .putString(KEY_TITLE, displayLabel)
                    .build());
            NotificationHelper.showDownloadProgress(appContext, notificationId, displayLabel, 0, true);

            LinkResolver.ResolvedAudio resolved = LinkResolver.resolve(url);
            if (isStopped()) {
                return Result.failure(errorData(displayLabel, "Download cancelled."));
            }

            String finalTitle = (customTitle != null && !customTitle.trim().isEmpty())
                    ? customTitle.trim()
                    : resolved.title;

            final long[] lastNotifyTime = {0L};
            final int[] lastNotifiedPercent = {-1};

            AudioFileUtils.SavedAudioResult saved = AudioFileUtils.downloadAndSaveAudio(
                    appContext,
                    resolved.streamUrl,
                    finalTitle,
                    resolved.artist,
                    resolved.extension,
                    resolved.mimeType,
                    resolved.durationMs,
                    percent -> {
                        long now = System.currentTimeMillis();
                        // Throttle notification updates so Android does not drop rapid posts.
                        if (percent == 100 || (percent - lastNotifiedPercent[0] >= 4 && now - lastNotifyTime[0] >= 350)) {
                            lastNotifyTime[0] = now;
                            lastNotifiedPercent[0] = percent;
                            setProgressAsync(new Data.Builder()
                                    .putInt(KEY_PROGRESS, percent)
                                    .putString(KEY_STAGE, "Downloading " + percent + "%")
                                    .putString(KEY_TITLE, finalTitle)
                                    .build());
                            NotificationHelper.showDownloadProgress(
                                    appContext,
                                    notificationId,
                                    finalTitle,
                                    percent,
                                    false
                            );
                        }
                    }
            );

            Track track = new Track(
                    UUID.randomUUID().toString(),
                    finalTitle,
                    resolved.artist,
                    resolved.platform,
                    url,
                    saved.contentUri,
                    saved.fileName,
                    resolved.mimeType,
                    saved.durationMs,
                    saved.fileSizeBytes,
                    resolved.thumbnailUrl,
                    System.currentTimeMillis()
            );

            TrackRepository.getInstance(appContext).addTrack(track);
            NotificationHelper.showDownloadComplete(appContext, notificationId, finalTitle);

            Data output = new Data.Builder()
                    .putString(KEY_TRACK_ID, track.id)
                    .putString(KEY_TITLE, track.title)
                    .putString(KEY_SAVED_URI, track.contentUri)
                    .build();
            return Result.success(output);
        } catch (Exception e) {
            Log.e(TAG, "Download failed for " + url, e);
            String message = e.getMessage() != null && !e.getMessage().trim().isEmpty()
                    ? e.getMessage().trim()
                    : "Unable to extract or download audio.";
            NotificationHelper.showDownloadFailed(appContext, notificationId, displayLabel, message);
            return Result.failure(errorData(displayLabel, message));
        }
    }

    private Data errorData(String title, String message) {
        return new Data.Builder()
                .putString(KEY_TITLE, title)
                .putString(KEY_ERROR_MESSAGE, message)
                .build();
    }
}

