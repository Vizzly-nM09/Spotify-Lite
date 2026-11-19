package com.example.spotifylite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.spotifylite.utils.LinkResolver;

import org.junit.Test;

public class LinkResolverTest {

    @Test
    public void detectPlatform_identifiesSupportedSources() {
        assertEquals(
                LinkResolver.PLATFORM_YOUTUBE,
                LinkResolver.detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        );
        assertEquals(
                LinkResolver.PLATFORM_YOUTUBE,
                LinkResolver.detectPlatform("https://youtu.be/dQw4w9WgXcQ")
        );
        assertEquals(
                LinkResolver.PLATFORM_YOUTUBE,
                LinkResolver.detectPlatform("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        );
        assertEquals(
                LinkResolver.PLATFORM_SPOTIFY,
                LinkResolver.detectPlatform("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        );
        assertEquals(
                LinkResolver.PLATFORM_SOUNDCLOUD,
                LinkResolver.detectPlatform("https://soundcloud.com/artist/demo-track")
        );
        assertEquals(
                LinkResolver.PLATFORM_DIRECT,
                LinkResolver.detectPlatform("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
        );
        assertEquals(
                LinkResolver.PLATFORM_OTHER,
                LinkResolver.detectPlatform("https://example.com/listen/42")
        );
    }

    @Test
    public void isValidHttpUrl_validatesSchemeAndLength() {
        assertTrue(LinkResolver.isValidHttpUrl("https://youtu.be/abc123"));
        assertTrue(LinkResolver.isValidHttpUrl("http://example.com/song.mp3"));
        assertFalse(LinkResolver.isValidHttpUrl(""));
        assertFalse(LinkResolver.isValidHttpUrl("ftp://example.com/song.mp3"));
        assertFalse(LinkResolver.isValidHttpUrl("not a url"));
    }

    @Test
    public void sanitizeFileName_stripsInvalidCharsAndAppendsExtension() {
        assertEquals(
                "Lo-fi_Beats_2026.m4a",
                LinkResolver.sanitizeFileName("Lo-fi:Beats/2026", "m4a")
        );
        assertEquals(
                "track.mp3",
                LinkResolver.sanitizeFileName("track.mp3", "mp3")
        );
    }

    @Test
    public void mimeAndExtensionMapping_matchesAudioFormats() {
        assertEquals("audio/mpeg", LinkResolver.mimeTypeForExtension("mp3"));
        assertEquals("audio/mp4", LinkResolver.mimeTypeForExtension(".m4a"));
        assertEquals("audio/webm", LinkResolver.mimeTypeForExtension("webm"));
        assertEquals("mp3", LinkResolver.extensionFromUrl("https://cdn.test/a/Song_1.MP3", "m4a"));
        assertEquals("SoundHelix Song 1", LinkResolver.titleFromUrl("https://example.com/SoundHelix-Song_1.mp3"));
    }
}

