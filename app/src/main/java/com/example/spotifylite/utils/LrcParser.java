package com.example.spotifylite.utils;

import androidx.annotation.Nullable;

import com.example.spotifylite.model.LrcLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\]");

    private LrcParser() {
    }

    public static List<LrcLine> parse(@Nullable String rawLrc) {
        if (rawLrc == null || rawLrc.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<LrcLine> result = new ArrayList<>();
        String[] rawLines = rawLrc.split("\\r?\\n");
        for (String rawLine : rawLines) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            int lastMatchEnd = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = parseFractionMs(fraction);
                timestamps.add((minutes * 60_000L) + (seconds * 1_000L) + millis);
                lastMatchEnd = matcher.end();
            }
            if (timestamps.isEmpty()) {
                continue;
            }
            String text = rawLine.substring(lastMatchEnd).trim();
            if (text.isEmpty()) {
                continue;
            }
            for (long ts : timestamps) {
                result.add(new LrcLine(ts, text));
            }
        }
        result.sort(Comparator.comparingLong(line -> line.timeMs));
        return result;
    }

    public static int findActiveLineIndex(@Nullable List<LrcLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty() || positionMs < lines.get(0).timeMs) {
            return -1;
        }
        int low = 0;
        int high = lines.size() - 1;
        int active = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) {
                active = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return active;
    }

    private static long parseFractionMs(@Nullable String fraction) {
        if (fraction == null || fraction.isEmpty()) {
            return 0L;
        }
        if (fraction.length() == 1) {
            return Long.parseLong(fraction) * 100L;
        }
        if (fraction.length() == 2) {
            return Long.parseLong(fraction) * 10L;
        }
        return Long.parseLong(fraction.substring(0, 3));
    }
}

