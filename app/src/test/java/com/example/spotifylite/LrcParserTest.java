package com.example.spotifylite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.spotifylite.model.LrcLine;
import com.example.spotifylite.utils.LrcParser;

import org.junit.Test;

import java.util.List;

public class LrcParserTest {

    @Test
    public void parse_standardAndMillisecondTimestamps_sortsAndStripsMetadata() {
        String lrc = "[ar:Coldplay]\n"
                + "[ti:The Scientist]\n"
                + "[00:12.50]Come up to meet you\n"
                + "[00:16.125]Tell you I'm sorry\n"
                + "[00:20.00]\n";

        List<LrcLine> lines = LrcParser.parse(lrc);

        assertEquals(2, lines.size());
        assertEquals(12_500L, lines.get(0).timeMs);
        assertEquals("Come up to meet you", lines.get(0).text);
        assertEquals(16_125L, lines.get(1).timeMs);
        assertEquals("Tell you I'm sorry", lines.get(1).text);
    }

    @Test
    public void parse_multiTimestampLine_expandsAndSortsChronologically() {
        String lrc = "[00:10.00]Verse one\n"
                + "[00:20.00][01:05.50]Chorus line\n"
                + "[00:40.00]Verse two";

        List<LrcLine> lines = LrcParser.parse(lrc);

        assertEquals(4, lines.size());
        assertEquals(10_000L, lines.get(0).timeMs);
        assertEquals(20_000L, lines.get(1).timeMs);
        assertEquals("Chorus line", lines.get(1).text);
        assertEquals(40_000L, lines.get(2).timeMs);
        assertEquals(65_500L, lines.get(3).timeMs);
        assertEquals("Chorus line", lines.get(3).text);
    }

    @Test
    public void parse_nullOrEmpty_returnsEmptyList() {
        assertTrue(LrcParser.parse(null).isEmpty());
        assertTrue(LrcParser.parse("   \n  ").isEmpty());
    }

    @Test
    public void findActiveLineIndex_returnsExpectedIndices() {
        List<LrcLine> lines = LrcParser.parse(
                "[00:05.00]Line 0\n"
                + "[00:10.00]Line 1\n"
                + "[00:20.00]Line 2\n"
        );

        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0L));
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 4_999L));
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 5_000L));
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 9_999L));
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 10_000L));
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 15_400L));
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 20_000L));
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 99_000L));
    }
}

