package com.example.spotifylite;

import static org.junit.Assert.assertEquals;

import com.example.spotifylite.utils.FormatUtils;

import org.junit.Test;

public class FormatUtilsTest {

    @Test
    public void formatDuration_formatsMinutesAndHours() {
        assertEquals("0:00", FormatUtils.formatDuration(0L));
        assertEquals("0:09", FormatUtils.formatDuration(9_000L));
        assertEquals("3:42", FormatUtils.formatDuration(222_000L));
        assertEquals("1:05:03", FormatUtils.formatDuration(3_903_000L));
    }

    @Test
    public void formatFileSize_formatsBytesKbAndMb() {
        assertEquals("", FormatUtils.formatFileSize(0L));
        assertEquals("512 B", FormatUtils.formatFileSize(512L));
        assertEquals("256 KB", FormatUtils.formatFileSize(256 * 1024L));
        assertEquals("4.5 MB", FormatUtils.formatFileSize((long) (4.5 * 1024 * 1024)));
    }

    @Test
    public void cleanArtistName_stripsYoutubeTopicSuffix() {
        assertEquals("Coldplay", FormatUtils.cleanArtistName("Coldplay - Topic"));
        assertEquals("Coldplay", FormatUtils.cleanArtistName("Coldplay \u2013 Topic"));
        assertEquals("Radiohead", FormatUtils.cleanArtistName("Radiohead"));
        assertEquals("", FormatUtils.cleanArtistName(null));
    }
}
