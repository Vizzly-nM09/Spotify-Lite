package com.example.spotifylite;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.bumptech.glide.Glide;
import com.example.spotifylite.data.TrackRepository;
import com.example.spotifylite.databinding.ActivityMainBinding;
import com.example.spotifylite.model.Track;
import com.example.spotifylite.service.MusicPlaybackService;
import com.example.spotifylite.ui.PlaybackHost;
import com.example.spotifylite.ui.download.DownloadFragment;
import com.example.spotifylite.ui.library.LibraryFragment;
import com.example.spotifylite.ui.player.NowPlayingBottomSheet;
import com.example.spotifylite.ui.playlists.PlaylistsFragment;
import com.example.spotifylite.utils.FormatUtils;
import com.example.spotifylite.utils.NotificationHelper;
import com.example.spotifylite.utils.ThemePreferences;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements PlaybackHost {
    private static final String TAG_DOWNLOAD = "tab_download";
    private static final String TAG_LIBRARY = "tab_library";
    private static final String TAG_PLAYLISTS = "tab_playlists";
    private static final String STATE_ACTIVE_TAB = "active_tab_id";

    private ActivityMainBinding binding;
    private DownloadFragment downloadFragment;
    private LibraryFragment libraryFragment;
    private PlaylistsFragment playlistsFragment;
    private int activeTabId = R.id.nav_download;

    @Nullable
    private ListenableFuture<MediaController> controllerFuture;
    @Nullable
    private MediaController mediaController;

    private final Handler miniProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable miniProgressTicker = new Runnable() {
        @Override
        public void run() {
            updateMiniPlayerProgress();
            miniProgressHandler.postDelayed(this, 500L);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            syncMiniPlayer();
            String playingId = getCurrentPlayingTrackId();
            if (libraryFragment != null) {
                libraryFragment.updatePlayingTrackId(playingId);
            }
            if (playlistsFragment != null) {
                playlistsFragment.updatePlayingTrackId(playingId);
            }
        }
    };

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.toast_notification_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyWindowInsets();
        setupThemeToggle();
        setupFragments(savedInstanceState);
        setupBottomNavigation();
        setupMiniPlayer();
        setupBackNavigation();
        requestNotificationPermissionIfNeeded();
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(
                this,
                new ComponentName(this, MusicPlaybackService.class)
        );
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                if (controllerFuture != null) {
                    mediaController = controllerFuture.get();
                    mediaController.addListener(playerListener);
                    syncMiniPlayer();
                    String playingId = getCurrentPlayingTrackId();
                    if (libraryFragment != null) {
                        libraryFragment.updatePlayingTrackId(playingId);
                    }
                    if (playlistsFragment != null) {
                        playlistsFragment.updatePlayingTrackId(playingId);
                    }
                    notifyNowPlayingControllerChanged(mediaController);
                    miniProgressHandler.post(miniProgressTicker);
                }
            } catch (ExecutionException | InterruptedException ignored) {
                // Service binding failed or was interrupted before activity stopped.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        super.onStop();
        miniProgressHandler.removeCallbacks(miniProgressTicker);
        notifyNowPlayingControllerChanged(null);
        if (mediaController != null) {
            mediaController.removeListener(playerListener);
            mediaController = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }

    private void notifyNowPlayingControllerChanged(@Nullable MediaController controller) {
        Fragment sheet = getSupportFragmentManager().findFragmentByTag(NowPlayingBottomSheet.TAG);
        if (sheet instanceof NowPlayingBottomSheet) {
            ((NowPlayingBottomSheet) sheet).onControllerChanged(controller);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ACTIVE_TAB, activeTabId);
    }

    private void applyWindowInsets() {
        // Prevent BottomNavigationView from applying duplicate navigation bar padding.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav, (v, insets) -> insets);

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            binding.topHeaderBar.setPadding(
                    dpToPx(16) + systemBars.left,
                    systemBars.top,
                    dpToPx(8) + systemBars.right,
                    0
            );

            if (imeVisible) {
                binding.bottomDock.setVisibility(View.GONE);
                binding.fragmentContainer.setPadding(
                        systemBars.left,
                        0,
                        systemBars.right,
                        ime.bottom
                );
            } else {
                binding.bottomDock.setVisibility(View.VISIBLE);
                binding.bottomDock.setPadding(
                        systemBars.left,
                        0,
                        systemBars.right,
                        systemBars.bottom
                );
                binding.fragmentContainer.setPadding(
                        systemBars.left,
                        0,
                        systemBars.right,
                        0
                );
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupThemeToggle() {
        boolean isNight = ThemePreferences.isNightModeActive(this);
        binding.btnToggleTheme.setImageResource(
                isNight ? R.drawable.ic_theme_light : R.drawable.ic_theme_dark
        );
        binding.btnToggleTheme.setOnClickListener(v -> ThemePreferences.toggleTheme(this));
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (activeTabId == R.id.nav_playlists
                        && playlistsFragment != null
                        && playlistsFragment.handleBackPress()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void setupFragments(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            activeTabId = savedInstanceState.getInt(STATE_ACTIVE_TAB, R.id.nav_download);
            downloadFragment = (DownloadFragment) getSupportFragmentManager().findFragmentByTag(TAG_DOWNLOAD);
            libraryFragment = (LibraryFragment) getSupportFragmentManager().findFragmentByTag(TAG_LIBRARY);
            playlistsFragment = (PlaylistsFragment) getSupportFragmentManager().findFragmentByTag(TAG_PLAYLISTS);
        }

        if (downloadFragment == null) {
            downloadFragment = new DownloadFragment();
        }
        if (libraryFragment == null) {
            libraryFragment = new LibraryFragment();
        }
        if (playlistsFragment == null) {
            playlistsFragment = new PlaylistsFragment();
        }

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (!downloadFragment.isAdded()) {
            tx.add(R.id.fragmentContainer, downloadFragment, TAG_DOWNLOAD);
        }
        if (!libraryFragment.isAdded()) {
            tx.add(R.id.fragmentContainer, libraryFragment, TAG_LIBRARY);
        }
        if (!playlistsFragment.isAdded()) {
            tx.add(R.id.fragmentContainer, playlistsFragment, TAG_PLAYLISTS);
        }
        if (activeTabId == R.id.nav_library) {
            tx.hide(downloadFragment).hide(playlistsFragment).show(libraryFragment);
        } else if (activeTabId == R.id.nav_playlists) {
            tx.hide(downloadFragment).hide(libraryFragment).show(playlistsFragment);
        } else {
            tx.hide(libraryFragment).hide(playlistsFragment).show(downloadFragment);
        }
        tx.commitNow();
    }

    private void setupBottomNavigation() {
        binding.bottomNav.setSelectedItemId(activeTabId);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_download) {
                showTab(downloadFragment, R.id.nav_download);
                return true;
            }
            if (id == R.id.nav_library) {
                showTab(libraryFragment, R.id.nav_library);
                return true;
            }
            if (id == R.id.nav_playlists) {
                showTab(playlistsFragment, R.id.nav_playlists);
                return true;
            }
            return false;
        });
    }

    private void showTab(Fragment toShow, int tabId) {
        activeTabId = tabId;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (toShow != downloadFragment) tx.hide(downloadFragment);
        if (toShow != libraryFragment) tx.hide(libraryFragment);
        if (toShow != playlistsFragment) tx.hide(playlistsFragment);
        tx.show(toShow).commit();
    }

    private void setupMiniPlayer() {
        binding.miniPlayerContainer.setOnClickListener(v -> {
            if (mediaController != null && mediaController.getCurrentMediaItem() != null) {
                if (getSupportFragmentManager().findFragmentByTag(NowPlayingBottomSheet.TAG) == null) {
                    new NowPlayingBottomSheet().show(getSupportFragmentManager(), NowPlayingBottomSheet.TAG);
                }
            }
        });

        binding.btnMiniPlayPause.setOnClickListener(v -> {
            if (mediaController == null) return;
            if (mediaController.isPlaying()) {
                mediaController.pause();
            } else {
                mediaController.play();
            }
        });

        binding.btnMiniNext.setOnClickListener(v -> {
            if (mediaController != null && mediaController.hasNextMediaItem()) {
                mediaController.seekToNextMediaItem();
            }
        });
    }

    private void syncMiniPlayer() {
        if (binding == null) {
            return;
        }
        if (mediaController == null || mediaController.getCurrentMediaItem() == null) {
            binding.miniPlayerContainer.setVisibility(View.GONE);
            return;
        }

        MediaItem item = mediaController.getCurrentMediaItem();
        MediaMetadata meta = item.mediaMetadata;
        binding.miniPlayerContainer.setVisibility(View.VISIBLE);
        binding.tvMiniTitle.setText(meta.title != null ? meta.title : "Audio Track");
        String cleanedArtist = meta.artist != null ? FormatUtils.cleanArtistName(meta.artist.toString()) : "";
        binding.tvMiniArtist.setText(!cleanedArtist.isEmpty() ? cleanedArtist : getString(R.string.unknown_artist));
        binding.btnMiniPlayPause.setImageResource(
                mediaController.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play
        );
        binding.btnMiniNext.setEnabled(mediaController.hasNextMediaItem());
        binding.btnMiniNext.setAlpha(mediaController.hasNextMediaItem() ? 1.0f : 0.4f);

        Track storedTrack = TrackRepository.getInstance(this).getTrackById(item.mediaId);
        if (storedTrack != null && storedTrack.thumbnailUrl != null && !storedTrack.thumbnailUrl.isEmpty()) {
            Glide.with(binding.ivMiniArt)
                    .load(storedTrack.thumbnailUrl)
                    .placeholder(R.drawable.bg_artwork_placeholder)
                    .error(R.drawable.bg_artwork_placeholder)
                    .centerCrop()
                    .into(binding.ivMiniArt);
        } else {
            Glide.with(binding.ivMiniArt).clear(binding.ivMiniArt);
            binding.ivMiniArt.setImageResource(R.drawable.bg_artwork_placeholder);
        }

        updateMiniPlayerProgress();
    }

    private void updateMiniPlayerProgress() {
        if (binding == null || mediaController == null) {
            return;
        }
        long duration = mediaController.getDuration();
        long position = mediaController.getCurrentPosition();
        if (duration > 0 && position >= 0) {
            int progress = (int) Math.min(1000L, (position * 1000L) / duration);
            binding.miniPlayerProgress.setProgress(progress);
        } else {
            binding.miniPlayerProgress.setProgress(0);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void handleLaunchIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String tab = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB);
        if (NotificationHelper.TAB_LIBRARY.equals(tab)) {
            switchToLibraryTab();
        }
    }

    @Override
    public void playTrackFromLibrary(Track track, List<Track> playlist) {
        if (mediaController == null) {
            Toast.makeText(this, "Audio player is starting, please tap again.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<MediaItem> mediaItems = new ArrayList<>();
        int startIndex = 0;
        for (int i = 0; i < playlist.size(); i++) {
            Track item = playlist.get(i);
            mediaItems.add(toMediaItem(item));
            if (item.id != null && item.id.equals(track.id)) {
                startIndex = i;
            }
        }
        if (mediaItems.isEmpty()) {
            mediaItems.add(toMediaItem(track));
            startIndex = 0;
        }

        mediaController.setMediaItems(mediaItems, startIndex, 0L);
        mediaController.prepare();
        mediaController.play();
        syncMiniPlayer();
        if (libraryFragment != null) {
            libraryFragment.updatePlayingTrackId(track.id);
        }
        if (playlistsFragment != null) {
            playlistsFragment.updatePlayingTrackId(track.id);
        }
    }

    @Override
    public void playTrackById(String trackId) {
        TrackRepository repo = TrackRepository.getInstance(this);
        Track target = repo.getTrackById(trackId);
        if (target == null) {
            switchToLibraryTab();
            return;
        }
        List<Track> all = repo.getTracksSnapshot();
        playTrackFromLibrary(target, all);
        switchToLibraryTab();
    }

    @Override
    public void onTrackDeleted(Track track) {
        if (mediaController == null || track.id == null) {
            return;
        }
        int count = mediaController.getMediaItemCount();
        for (int i = count - 1; i >= 0; i--) {
            if (track.id.equals(mediaController.getMediaItemAt(i).mediaId)) {
                mediaController.removeMediaItem(i);
            }
        }
        syncMiniPlayer();
        String playingId = getCurrentPlayingTrackId();
        if (libraryFragment != null) {
            libraryFragment.updatePlayingTrackId(playingId);
        }
        if (playlistsFragment != null) {
            playlistsFragment.updatePlayingTrackId(playingId);
        }
    }

    @Override
    public void switchToDownloadTab() {
        binding.bottomNav.setSelectedItemId(R.id.nav_download);
    }

    @Override
    public void switchToLibraryTab() {
        binding.bottomNav.setSelectedItemId(R.id.nav_library);
    }

    @Nullable
    @Override
    public MediaController getPlaybackController() {
        return mediaController;
    }

    @Nullable
    @Override
    public String getCurrentPlayingTrackId() {
        if (mediaController == null || mediaController.getCurrentMediaItem() == null) {
            return null;
        }
        return mediaController.getCurrentMediaItem().mediaId;
    }

    private MediaItem toMediaItem(Track track) {
        String cleanedArtist = FormatUtils.cleanArtistName(track.artist);
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(!cleanedArtist.isEmpty() ? cleanedArtist : getString(R.string.unknown_artist));
        if (track.thumbnailUrl != null && !track.thumbnailUrl.isEmpty()) {
            metadata.setArtworkUri(Uri.parse(track.thumbnailUrl));
        }
        return new MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(track.contentUri))
                .setMediaMetadata(metadata.build())
                .build();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
