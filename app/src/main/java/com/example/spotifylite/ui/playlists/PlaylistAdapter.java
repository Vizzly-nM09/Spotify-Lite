package com.example.spotifylite.ui.playlists;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifylite.R;
import com.example.spotifylite.databinding.ItemPlaylistBinding;
import com.example.spotifylite.model.Playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistInteractionListener {
        void onPlaylistClicked(Playlist playlist);

        void onPlaylistMoreClicked(Playlist playlist, View anchor);
    }

    private final List<Playlist> items = new ArrayList<>();
    private final OnPlaylistInteractionListener listener;
    private Map<String, String> trackThumbnails = new HashMap<>();

    public PlaylistAdapter(OnPlaylistInteractionListener listener) {
        this.listener = listener;
    }

    public void submitList(@Nullable List<Playlist> newItems) {
        List<Playlist> next = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return Objects.equals(items.get(oldPos).id, next.get(newPos).id);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return items.get(oldPos).equals(next.get(newPos));
            }
        });
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0
                || fromPosition >= items.size() || toPosition >= items.size()) {
            return;
        }
        Collections.swap(items, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<String> getCurrentIds() {
        List<String> ids = new ArrayList<>(items.size());
        for (Playlist p : items) {
            if (p.id != null) {
                ids.add(p.id);
            }
        }
        return ids;
    }

    public void setTrackThumbnails(@Nullable Map<String, String> thumbnailsByTrackId) {
        this.trackThumbnails = thumbnailsByTrackId != null ? thumbnailsByTrackId : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlaylistBinding binding = ItemPlaylistBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ItemPlaylistBinding binding;

        PlaylistViewHolder(ItemPlaylistBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Playlist playlist) {
            Context ctx = binding.getRoot().getContext();
            binding.tvPlaylistName.setText(playlist.name);

            int count = playlist.trackIds != null ? playlist.trackIds.size() : 0;
            if (count == 0) {
                binding.tvPlaylistCount.setText(R.string.playlist_track_count_zero);
            } else if (count == 1) {
                binding.tvPlaylistCount.setText(R.string.playlist_track_count_single);
            } else {
                binding.tvPlaylistCount.setText(ctx.getString(R.string.playlist_track_count_plural, count));
            }

            String coverUrl = findFirstThumbnail(playlist);
            if (coverUrl != null && !coverUrl.isEmpty()) {
                Glide.with(binding.ivPlaylistArt)
                        .load(coverUrl)
                        .placeholder(R.drawable.bg_artwork_placeholder)
                        .error(R.drawable.bg_artwork_placeholder)
                        .centerCrop()
                        .into(binding.ivPlaylistArt);
            } else {
                Glide.with(binding.ivPlaylistArt).clear(binding.ivPlaylistArt);
                binding.ivPlaylistArt.setImageResource(R.drawable.bg_artwork_placeholder);
            }

            binding.cardPlaylist.setOnClickListener(v -> listener.onPlaylistClicked(playlist));
            binding.btnPlaylistMore.setOnClickListener(v -> listener.onPlaylistMoreClicked(playlist, v));
        }

        @Nullable
        private String findFirstThumbnail(Playlist playlist) {
            if (playlist.trackIds == null) {
                return null;
            }
            for (String trackId : playlist.trackIds) {
                String url = trackThumbnails.get(trackId);
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
            return null;
        }
    }
}
