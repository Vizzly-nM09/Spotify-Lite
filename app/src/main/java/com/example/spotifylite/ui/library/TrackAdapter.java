package com.example.spotifylite.ui.library;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifylite.R;
import com.example.spotifylite.databinding.ItemTrackBinding;
import com.example.spotifylite.model.Track;
import com.example.spotifylite.utils.FormatUtils;
import com.example.spotifylite.utils.LinkResolver;

import java.util.Objects;

public class TrackAdapter extends ListAdapter<Track, TrackAdapter.TrackViewHolder> {

    public interface OnTrackInteractionListener {
        void onTrackClicked(Track track);

        void onDeleteClicked(Track track);
    }

    private final OnTrackInteractionListener listener;
    @Nullable
    private String playingTrackId;

    public TrackAdapter(OnTrackInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setPlayingTrackId(@Nullable String trackId) {
        if (Objects.equals(this.playingTrackId, trackId)) {
            return;
        }
        this.playingTrackId = trackId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTrackBinding binding = ItemTrackBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new TrackViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class TrackViewHolder extends RecyclerView.ViewHolder {
        private final ItemTrackBinding binding;

        TrackViewHolder(ItemTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Track track) {
            Context ctx = binding.getRoot().getContext();
            boolean isPlaying = track.id != null && track.id.equals(playingTrackId);

            binding.tvTrackTitle.setText(track.title);
            binding.tvTrackTitle.setTextColor(ContextCompat.getColor(
                    ctx,
                    isPlaying ? R.color.accent_primary : R.color.text_primary
            ));
            binding.tvPlayingBadge.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
            binding.cardTrack.setStrokeColor(ContextCompat.getColor(
                    ctx,
                    isPlaying ? R.color.accent_primary : R.color.stroke_subtle
            ));

            StringBuilder subtitle = new StringBuilder();
            String cleanedArtist = FormatUtils.cleanArtistName(track.artist);
            subtitle.append(!cleanedArtist.isEmpty()
                    ? cleanedArtist
                    : ctx.getString(R.string.unknown_artist));
            subtitle.append(" · ").append(platformDisplay(track.sourcePlatform));
            if (track.durationMs > 0) {
                subtitle.append(" · ").append(FormatUtils.formatDuration(track.durationMs));
            }
            String sizeText = FormatUtils.formatFileSize(track.fileSizeBytes);
            if (!sizeText.isEmpty()) {
                subtitle.append(" · ").append(sizeText);
            }
            binding.tvTrackSubtitle.setText(subtitle.toString());

            if (track.thumbnailUrl != null && !track.thumbnailUrl.isEmpty()) {
                Glide.with(binding.ivTrackArt)
                        .load(track.thumbnailUrl)
                        .placeholder(R.drawable.bg_artwork_placeholder)
                        .error(R.drawable.bg_artwork_placeholder)
                        .centerCrop()
                        .into(binding.ivTrackArt);
            } else {
                Glide.with(binding.ivTrackArt).clear(binding.ivTrackArt);
                binding.ivTrackArt.setImageResource(R.drawable.bg_artwork_placeholder);
            }

            binding.cardTrack.setOnClickListener(v -> listener.onTrackClicked(track));
            binding.btnDeleteTrack.setOnClickListener(v -> listener.onDeleteClicked(track));
        }

        private String platformDisplay(@Nullable String platform) {
            if (LinkResolver.PLATFORM_YOUTUBE.equals(platform)) return "YouTube";
            if (LinkResolver.PLATFORM_SPOTIFY.equals(platform)) return "Spotify";
            if (LinkResolver.PLATFORM_SOUNDCLOUD.equals(platform)) return "SoundCloud";
            if (LinkResolver.PLATFORM_DIRECT.equals(platform)) return "Direct";
            return "Audio";
        }
    }

    private static final DiffUtil.ItemCallback<Track> DIFF_CALLBACK = new DiffUtil.ItemCallback<Track>() {
        @Override
        public boolean areItemsTheSame(@NonNull Track oldItem, @NonNull Track newItem) {
            return Objects.equals(oldItem.id, newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Track oldItem, @NonNull Track newItem) {
            return oldItem.equals(newItem);
        }
    };
}

