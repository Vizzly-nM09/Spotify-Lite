package com.example.spotifylite.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.spotifylite.MainActivity;
import com.example.spotifylite.R;

public final class NotificationHelper {
    public static final String CHANNEL_DOWNLOADS = "music_download_channel";
    public static final String EXTRA_OPEN_TAB = "extra_open_tab";
    public static final String TAB_LIBRARY = "library";

    private NotificationHelper() {
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel downloadChannel = new NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    context.getString(R.string.notif_channel_downloads_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            downloadChannel.setDescription(context.getString(R.string.notif_channel_downloads_desc));
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(downloadChannel);
            }
        }
    }

    public static void showDownloadProgress(
            Context context,
            int notificationId,
            String title,
            int progressPercent,
            boolean indeterminate
    ) {
        if (!canPostNotifications(context)) {
            return;
        }
        String contentText = indeterminate
                ? context.getString(R.string.notif_resolving_text)
                : title + " (" + progressPercent + "%)";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.notif_downloading_title))
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, Math.max(0, Math.min(100, progressPercent)), indeterminate);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    public static void showDownloadComplete(Context context, int notificationId, String trackTitle) {
        if (!canPostNotifications(context)) {
            return;
        }
        Intent openLibrary = new Intent(context, MainActivity.class);
        openLibrary.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openLibrary.putExtra(EXTRA_OPEN_TAB, TAB_LIBRARY);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openLibrary,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle(context.getString(R.string.notif_complete_title))
                .setContentText(context.getString(R.string.notif_complete_text, trackTitle))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    public static void showDownloadFailed(
            Context context,
            int notificationId,
            String label,
            String reason
    ) {
        if (!canPostNotifications(context)) {
            return;
        }
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String safeReason = (reason == null || reason.trim().isEmpty())
                ? "Could not download audio."
                : reason.trim();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_error)
                .setContentTitle(context.getString(R.string.notif_failed_title))
                .setContentText(context.getString(R.string.notif_failed_text, label, safeReason))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notif_failed_text, label, safeReason)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    private static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}

