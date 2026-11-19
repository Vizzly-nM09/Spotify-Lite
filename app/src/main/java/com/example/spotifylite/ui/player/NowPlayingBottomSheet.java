package com.example.spotifylite.ui.player;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import com.bumptech.glide.Glide;
import com.example.spotifylite.R;
import com.example.spotifylite.data.TrackRepository;
import com.example.spotifylite.databinding.BottomSheetNowPlayingBinding;
import com.example.spotifylite.model.Track;
import com.example.spotifylite.ui.PlaybackHost;
import com.example.spotifylite.utils.FormatUtils;
import com.example.spotifylite.utils.LinkResolver;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;

public class NowPlayingBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = "NowPlayingBottomSheet";

    private BottomSheetNowPlayingBinding binding;
    @Nullable
    private MediaController controller;
    @Nullable
    private Track cachedTrack;
    private boolean userScrubbing = false;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            updateProgressUi();
            progressHandler.postDelayed(this, 500L);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            syncAllUi();
        }
    };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = BottomSheetNowPlayingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnCloseSheet.setOnClickListener(v -> dismiss());

        binding.btnSheetPlayPause.setOnClickListener(v -> {
            MediaController live = resolveController();
            if (live == null) return;
            if (live.isPlaying()) {
                live.pause();
            } else {
                live.play();
            }
        });

        binding.btnSheetNext.setOnClickListener(v -> {
            MediaController live = resolveController();
            if (live != null && live.hasNextMediaItem()) {
                live.seekToNextMediaItem();
            }
        });

        binding.btnSheetPrev.setOnClickListener(v -> {
            MediaController live = resolveController();
            if (live != null) {
                if (live.getCurrentPosition() > 3000L) {
                    live.seekTo(0L);
                } else if (live.hasPreviousMediaItem()) {
                    live.seekToPreviousMediaItem();
                } else {
                    live.seekTo(0L);
                }
            }
        });

        binding.btnSheetShuffle.setOnClickListener(v -> {
            MediaController live = resolveController();
            if (live != null) {
                live.setShuffleModeEnabled(!live.getShuffleModeEnabled());
                syncTransportStates();
            }
        });

        binding.btnSheetRepeat.setOnClickListener(v -> {
            MediaController live = resolveController();
            if (live != null) {
                int current = live.getRepeatMode();
                int next;
                if (current == Player.REPEAT_MODE_OFF) {
                    next = Player.REPEAT_MODE_ALL;
                } else if (current == Player.REPEAT_MODE_ALL) {
                    next = Player.REPEAT_MODE_ONE;
                } else {
                    next = Player.REPEAT_MODE_OFF;
                }
                live.setRepeatMode(next);
                syncTransportStates();
            }
        });

        binding.sliderProgress.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                userScrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                userScrubbing = false;
                MediaController live = resolveController();
                if (live != null) {
                    long duration = getEffectiveDurationMs(live);
                    if (duration > 0) {
                        long targetMs = (long) ((slider.getValue() / 100f) * duration);
                        live.seekTo(targetMs);
                    }
                }
            }
        });

        binding.sliderProgress.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                MediaController live = resolveController();
                long duration = live != null ? getEffectiveDurationMs(live) : 0L;
                if (duration > 0) {
                    long previewMs = (long) ((value / 100f) * duration);
                    binding.tvElapsedTime.setText(FormatUtils.formatDuration(previewMs));
                }
            }
        });

        onControllerChanged(fetchHostController());
    }

    @Override
    public void onStart() {
        super.onStart();
        // Rebind to the fresh MediaController created when MainActivity resumes from background.
        onControllerChanged(fetchHostController());
        startProgressTicker();
    }

    @Override
    public void onStop() {
        super.onStop();
        progressHandler.removeCallbacks(progressTicker);
    }

    @Override
    public void onDestroyView() {
        detachCurrentController();
        progressHandler.removeCallbacks(progressTicker);
        super.onDestroyView();
        binding = null;
    }

    public void onControllerChanged(@Nullable MediaController newController) {
        if (controller == newController) {
            if (controller != null && binding != null) {
                syncAllUi();
            }
            return;
        }
        detachCurrentController();
        controller = newController;
        if (controller != null) {
            controller.addListener(playerListener);
            if (binding != null && isAdded()) {
                syncAllUi();
                startProgressTicker();
            }
        }
    }

    @Nullable
    private MediaController fetchHostController() {
        if (getActivity() instanceof PlaybackHost) {
            return ((PlaybackHost) getActivity()).getPlaybackController();
        }
        return null;
    }

    @Nullable
    private MediaController resolveController() {
        MediaController hostController = fetchHostController();
        if (hostController != null && hostController != controller) {
            onControllerChanged(hostController);
        }
        return controller;
    }

    private void detachCurrentController() {
        if (controller != null) {
            controller.removeListener(playerListener);
            controller = null;
        }
    }

    private void startProgressTicker() {
        progressHandler.removeCallbacks(progressTicker);
        if (isAdded() && binding != null) {
            progressHandler.post(progressTicker);
        }
    }

    private void syncAllUi() {
        if (binding == null || !isAdded() || controller == null) {
            return;
        }
        MediaItem item = controller.getCurrentMediaItem();
        if (item == null) {
            dismissAllowingStateLoss();
            return;
        }
        MediaMetadata meta = item.mediaMetadata;
        CharSequence title = meta.title != null ? meta.title : "Audio Track";
        String rawArtist = meta.artist != null ? meta.artist.toString() : "";
        String cleanedArtist = FormatUtils.cleanArtistName(rawArtist);

        binding.tvSheetTitle.setText(title);
        binding.tvSheetArtist.setText(!cleanedArtist.isEmpty() ? cleanedArtist : getString(R.string.unknown_artist));

        cachedTrack = TrackRepository.getInstance(requireContext()).getTrackById(item.mediaId);
        if (cachedTrack != null) {
            binding.tvSheetSource.setVisibility(View.VISIBLE);
            binding.tvSheetSource.setText(formatPlatform(cachedTrack.sourcePlatform));
            if (cachedTrack.thumbnailUrl != null && !cachedTrack.thumbnailUrl.isEmpty()) {
                Glide.with(binding.ivSheetArt)
                        .load(cachedTrack.thumbnailUrl)
                        .placeholder(R.drawable.bg_artwork_placeholder)
                        .error(R.drawable.bg_artwork_placeholder)
                        .centerCrop()
                        .into(binding.ivSheetArt);
            } else {
                Glide.with(binding.ivSheetArt).clear(binding.ivSheetArt);
                binding.ivSheetArt.setImageResource(R.drawable.bg_artwork_placeholder);
            }
        } else {
            binding.tvSheetSource.setVisibility(View.GONE);
            binding.ivSheetArt.setImageResource(R.drawable.bg_artwork_placeholder);
        }

        binding.btnSheetPlayPause.setIconResource(
                controller.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play
        );
        syncTransportStates();
        updateProgressUi();
    }

    private void syncTransportStates() {
        if (binding == null || !isAdded() || controller == null) {
            return;
        }
        int activeColor = ContextCompat.getColor(requireContext(), R.color.accent_primary);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        boolean shuffleOn = controller.getShuffleModeEnabled();
        binding.btnSheetShuffle.setImageTintList(
                ColorStateList.valueOf(shuffleOn ? activeColor : inactiveColor)
        );

        boolean hasNext = controller.hasNextMediaItem();
        binding.btnSheetNext.setEnabled(hasNext);
        binding.btnSheetNext.setAlpha(hasNext ? 1.0f : 0.4f);

        int repeatMode = controller.getRepeatMode();
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            binding.btnSheetRepeat.setImageResource(R.drawable.ic_repeat_one);
            binding.btnSheetRepeat.setImageTintList(ColorStateList.valueOf(activeColor));
            binding.btnSheetRepeat.setContentDescription(getString(R.string.repeat_one));
        } else if (repeatMode == Player.REPEAT_MODE_ALL) {
            binding.btnSheetRepeat.setImageResource(R.drawable.ic_repeat);
            binding.btnSheetRepeat.setImageTintList(ColorStateList.valueOf(activeColor));
            binding.btnSheetRepeat.setContentDescription(getString(R.string.repeat_all));
        } else {
            binding.btnSheetRepeat.setImageResource(R.drawable.ic_repeat);
            binding.btnSheetRepeat.setImageTintList(ColorStateList.valueOf(inactiveColor));
            binding.btnSheetRepeat.setContentDescription(getString(R.string.repeat_off));
        }
    }

    private void updateProgressUi() {
        if (binding == null || !isAdded() || userScrubbing) {
            return;
        }
        MediaController live = resolveController();
        if (live == null) {
            return;
        }
        long duration = getEffectiveDurationMs(live);
        long position = Math.max(0L, live.getCurrentPosition());

        binding.tvElapsedTime.setText(FormatUtils.formatDuration(position));
        binding.tvTotalTime.setText(FormatUtils.formatDuration(duration));

        if (duration > 0) {
            float percent = Math.max(0f, Math.min(100f, (position * 100f) / duration));
            binding.sliderProgress.setEnabled(true);
            binding.sliderProgress.setValue(percent);
        } else {
            binding.sliderProgress.setEnabled(false);
            binding.sliderProgress.setValue(0f);
        }
    }

    private long getEffectiveDurationMs(MediaController live) {
        long duration = live.getDuration();
        if (duration != C.TIME_UNSET && duration > 0) {
            return duration;
        }
        if (cachedTrack != null && cachedTrack.durationMs > 0) {
            return cachedTrack.durationMs;
        }
        return 0L;
    }

    private String formatPlatform(@Nullable String platform) {
        if (LinkResolver.PLATFORM_YOUTUBE.equals(platform)) return "YouTube";
        if (LinkResolver.PLATFORM_SPOTIFY.equals(platform)) return "Spotify";
        if (LinkResolver.PLATFORM_SOUNDCLOUD.equals(platform)) return "SoundCloud";
        if (LinkResolver.PLATFORM_DIRECT.equals(platform)) return "Direct Audio";
        return "Audio";
    }
}
