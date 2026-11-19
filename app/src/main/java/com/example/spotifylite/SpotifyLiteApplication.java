package com.example.spotifylite;

import android.app.Application;

import com.example.spotifylite.utils.NotificationHelper;
import com.example.spotifylite.utils.OkHttpNewPipeDownloader;
import com.example.spotifylite.utils.ThemePreferences;

import org.schabi.newpipe.extractor.NewPipe;

public class SpotifyLiteApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePreferences.applySavedTheme(this);
        NotificationHelper.createNotificationChannels(this);
        NewPipe.init(OkHttpNewPipeDownloader.getInstance());
    }
}

