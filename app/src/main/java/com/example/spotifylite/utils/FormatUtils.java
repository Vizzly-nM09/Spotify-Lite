package com.example.spotifylite.utils;

import androidx.annotation.Nullable;

import java.util.Locale;

public final class FormatUtils {

    private FormatUtils() {
    }

    public static String cleanArtistName(@Nullable String rawArtist) {
        if (rawArtist == null) {
            return "";
        }
        String cleaned = rawArtist.trim();
        String lower = cleaned.toLowerCase(Locale.US);
        String[] suffixes = {" - topic", " \u2013 topic", " \u2014 topic"};
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
                break;
            }
        }
        return cleaned;
    }

    public static String formatDuration(long durationMs) {
        if (durationMs <= 0) {
            return "0:00";
        }
        long totalSeconds = durationMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }
}

