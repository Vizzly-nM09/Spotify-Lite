package com.example.spotifylite.model;

import java.util.Objects;

public class LrcLine {
    public final long timeMs;
    public final String text;

    public LrcLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LrcLine)) return false;
        LrcLine lrcLine = (LrcLine) o;
        return timeMs == lrcLine.timeMs && Objects.equals(text, lrcLine.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeMs, text);
    }
}

