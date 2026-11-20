# Spotify Lite

An offline Android music downloader and background audio player built in Java for a Mobile Programming coursework assignment (Chapter 12: Services and Background Processing).

## Disclaimer

This project is an independent student assignment created strictly for educational purposes. Despite the project name "Spotify Lite", this app is **not** affiliated with, endorsed by, or copied from Spotify AB:

- **Different purpose and architecture:** Spotify is a commercial cloud music streaming service. This app is a local background file downloader and offline audio player built to demonstrate Android `WorkManager`, network constraints, notifications, and foreground `MediaSessionService` playback.
- **Original UI:** The interface uses its own editorial studio visual system (warm charcoal/paper surfaces, deep teal accent, and bundled Outfit + Work Sans typography) rather than replicating Spotify's branding or layout.
- **No DRM bypass:** Spotify audio streams are DRM-protected and are never downloaded directly from Spotify's servers. When a Spotify track URL is pasted, the app reads only public track metadata via Spotify's `oEmbed` endpoint and searches for a public audio match via YouTube.

## Features

- **Background downloads with WorkManager:** `MusicDownloadWorker` resolves links and saves audio files reliably in the background, surviving app exits and network switches.
- **Wi-Fi-only constraint:** Includes a Wi-Fi-only switch (enabled by default) backed by `NetworkType.UNMETERED`. When enqueued without an unmetered Wi-Fi connection, the Download tab shows a waiting banner and WorkManager starts automatically once Wi-Fi connects.
- **Download notifications:** Posts ongoing progress alerts, a completion notification (tap to open the Library tab), and failure notifications with error details.
- **Multi-source link support:**
  - Direct audio URLs (`.mp3`, `.m4a`, `.wav`, `.ogg`, `.aac`, `.flac`, or `audio/*` HTTP responses)
  - Single YouTube, YouTube Music, YouTube Shorts, and SoundCloud links via `NewPipeExtractor`
  - Single Spotify track links (public oEmbed title/artist lookup matched against YouTube audio)
- **Offline playback in the background:** `MusicPlaybackService` extends AndroidX Media3 `MediaSessionService` with `ExoPlayer`, keeping audio playing when leaving the app or locking the screen with system notification controls.
- **Three-tab navigation with Mini-Player:**
  - **Download:** Supported-source indicator strip, clipboard paste/clear URL input, optional custom title, Wi-Fi constraint switch, status check, and live WorkManager queue (cancel, retry, and play).
  - **Library:** Searchable offline track list saved under `Music/SpotifyLite` via `MediaStore.Audio` (no runtime storage permission prompt on Android 10+), with a `⋮` track menu to add tracks to playlists or delete them.
  - **Playlists:** Create, rename, and delete custom playlists, add or remove tracks from your library, reorder both playlists and playlist tracks via long-press drag-and-drop, and play the full playlist queue.
  - **Mini-Player & Now Playing sheet:** Persistent player bar above the bottom navigation that expands into a sheet with artwork, a **Lyrics** toggle, seek bar, elapsed/total timers, previous/next, shuffle, and repeat modes.
- **Synced Lyrics (with offline cache):** Automatically fetches time-synced (`.lrc`) and plain lyrics from the public **LRCLIB** API and caches them locally for offline playback. Features a crossfade Artwork/Lyrics toggle, smooth active-line color/scale transitions, auto-scrolling, and tap-to-seek on any synced line.
- **Light and Dark themes:** Includes a live theme toggle in the top bar with WCAG AA-verified contrast in both modes.

## Limitations

- Single-track links only (playlist and album URLs are rejected with an inline error).
- Active live streams, private videos, members-only uploads, and region-locked links cannot be downloaded anonymously.
- YouTube extraction depends on `NewPipeExtractor` and may require updating the library version if YouTube changes its web API. Direct audio URLs always work independently of third-party extractors.

## Tech Stack

- **Language & SDK:** Java 17, `minSdk 24`, `targetSdk 37`, Material 3 Views with ViewBinding
- **Background Processing:** AndroidX `WorkManager` (`2.11.0`)
- **Audio Playback:** AndroidX `Media3 ExoPlayer` & `MediaSessionService` (`1.9.0`)
- **Networking & Extraction:** `OkHttp` (`4.12.0`), `NewPipeExtractor` (`v0.27.1` via JitPack), `Gson` (`2.12.1`)
- **Images & Typography:** `Glide` (`4.16.0`), bundled `Outfit` and `Work Sans` fonts

## Project Structure

```text
app/src/main/java/com/example/spotifylite/
├── SpotifyLiteApplication.java      # Theme, notification channels, NewPipe init
├── MainActivity.java                # Navigation, insets, MediaController & Mini-Player
├── data/
│   ├── LyricsRepository.java        # LRCLIB fetcher & offline lyrics cache
│   └── TrackRepository.java         # Local JSON track, playlist & download job registry
├── model/
│   ├── DownloadJobRecord.java
│   ├── LrcLine.java
│   ├── Playlist.java
│   ├── Track.java
│   └── TrackLyrics.java
├── service/
│   └── MusicPlaybackService.java    # Media3 MediaSessionService foreground player
├── ui/
│   ├── PlaybackHost.java
│   ├── download/                    # DownloadFragment & DownloadQueueAdapter
│   ├── library/                     # LibraryFragment & TrackAdapter
│   ├── player/                      # NowPlayingBottomSheet & LyricsAdapter
│   └── playlists/                   # PlaylistsFragment, PlaylistAdapter & PlaylistDialogs
├── utils/
│   ├── AudioFileUtils.java          # MediaStore / app Music storage + progress copy
│   ├── FormatUtils.java             # Duration, file size, and artist name formatting
│   ├── LinkResolver.java            # Direct, YouTube/SoundCloud, and Spotify resolver
│   ├── LrcParser.java               # .lrc timestamp parser & active-line lookup
│   ├── NotificationHelper.java      # Download notification channels and builders
│   ├── OkHttpNewPipeDownloader.java # NewPipeExtractor HTTP adapter
│   └── ThemePreferences.java        # Light/Dark mode persistence
└── workers/
    └── MusicDownloadWorker.java     # WorkManager background download task
```

## Getting Started

1. Clone this repository and open it in **Android Studio**.
2. Click **Sync Project with Gradle Files** so Gradle fetches dependencies from Google Maven, Maven Central, and JitPack.
3. Connect an Android device (Android 7.0 / API 24 or higher) or start an emulator, then run the `app` configuration.
4. On Android 13+, allow the notification permission prompt to receive download status and playback notifications.

## Running Tests

Run unit tests from Android Studio or via Gradle:

```bash
./gradlew testDebugUnitTest
```

Unit tests cover link platform classification, filename sanitization, MIME/extension mapping ([LinkResolverTest.java](app/src/test/java/com/example/spotifylite/LinkResolverTest.java)), duration/file-size formatting and artist name cleanup ([FormatUtilsTest.java](app/src/test/java/com/example/spotifylite/FormatUtilsTest.java)), and `.lrc` timestamp parsing and active-line lookup ([LrcParserTest.java](app/src/test/java/com/example/spotifylite/LrcParserTest.java)).
