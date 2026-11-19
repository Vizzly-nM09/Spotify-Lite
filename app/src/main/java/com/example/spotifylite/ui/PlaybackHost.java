package com.example.spotifylite.ui;

import androidx.annotation.Nullable;
import androidx.media3.session.MediaController;

import com.example.spotifylite.model.Track;

import java.util.List;

public interface PlaybackHost {
    void playTrackFromLibrary(Track track, List<Track> playlist);

    void playTrackById(String trackId);

    void onTrackDeleted(Track track);

    void switchToDownloadTab();

    void switchToLibraryTab();

    @Nullable
    MediaController getPlaybackController();

    @Nullable
    String getCurrentPlayingTrackId();
}
