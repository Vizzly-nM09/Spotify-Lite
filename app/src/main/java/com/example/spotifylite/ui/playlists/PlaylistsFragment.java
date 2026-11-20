package com.example.spotifylite.ui.playlists;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spotifylite.R;
import com.example.spotifylite.data.TrackRepository;
import com.example.spotifylite.databinding.FragmentPlaylistsBinding;
import com.example.spotifylite.model.Playlist;
import com.example.spotifylite.model.Track;
import com.example.spotifylite.ui.PlaybackHost;
import com.example.spotifylite.ui.library.TrackAdapter;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaylistsFragment extends Fragment {
    private FragmentPlaylistsBinding binding;
    private TrackRepository repository;
    private PlaylistAdapter playlistAdapter;
    private TrackAdapter detailTrackAdapter;

    private List<Playlist> allPlaylists = new ArrayList<>();
    private List<Track> allTracks = new ArrayList<>();
    @Nullable
    private String selectedPlaylistId;
    private boolean isDraggingPlaylists = false;
    private boolean isDraggingTracks = false;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentPlaylistsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = TrackRepository.getInstance(requireContext());

        playlistAdapter = new PlaylistAdapter(new PlaylistAdapter.OnPlaylistInteractionListener() {
            @Override
            public void onPlaylistClicked(Playlist playlist) {
                selectedPlaylistId = playlist.id;
                renderCurrentView();
            }

            @Override
            public void onPlaylistMoreClicked(Playlist playlist, View anchor) {
                showPlaylistMenu(playlist, anchor);
            }
        });
        binding.rvPlaylists.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlaylists.setAdapter(playlistAdapter);
        attachPlaylistDragHelper();

        detailTrackAdapter = new TrackAdapter(new TrackAdapter.OnTrackInteractionListener() {
            @Override
            public void onTrackClicked(Track track) {
                Playlist active = findSelectedPlaylist();
                if (active != null && getActivity() instanceof PlaybackHost) {
                    List<Track> queue = repository.resolvePlaylistTracks(active);
                    ((PlaybackHost) getActivity()).playTrackFromLibrary(track, queue);
                }
            }

            @Override
            public void onTrackMoreClicked(Track track, View anchor) {
                showPlaylistTrackMenu(track, anchor);
            }
        });
        binding.rvPlaylistTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlaylistTracks.setAdapter(detailTrackAdapter);
        attachTrackDragHelper();

        binding.btnNewPlaylist.setOnClickListener(v ->
                PlaylistDialogs.showCreatePlaylistDialog(requireContext(), repository, null));
        binding.btnEmptyCreatePlaylist.setOnClickListener(v ->
                PlaylistDialogs.showCreatePlaylistDialog(requireContext(), repository, null));

        binding.btnBackPlaylists.setOnClickListener(v -> {
            selectedPlaylistId = null;
            renderCurrentView();
        });

        binding.btnDetailMore.setOnClickListener(v -> {
            Playlist active = findSelectedPlaylist();
            if (active != null) {
                showPlaylistMenu(active, v);
            }
        });

        binding.btnPlayPlaylist.setOnClickListener(v -> {
            Playlist active = findSelectedPlaylist();
            if (active == null) return;
            List<Track> tracks = repository.resolvePlaylistTracks(active);
            if (!tracks.isEmpty() && getActivity() instanceof PlaybackHost) {
                ((PlaybackHost) getActivity()).playTrackFromLibrary(tracks.get(0), tracks);
            }
        });

        binding.btnAddTracksToPlaylist.setOnClickListener(v -> {
            Playlist active = findSelectedPlaylist();
            if (active != null) {
                showAddTracksPicker(active);
            }
        });

        repository.getPlaylistsLiveData().observe(getViewLifecycleOwner(), playlists -> {
            allPlaylists = playlists != null ? playlists : new ArrayList<>();
            renderCurrentView();
        });

        repository.getTracksLiveData().observe(getViewLifecycleOwner(), tracks -> {
            allTracks = tracks != null ? tracks : new ArrayList<>();
            Map<String, String> thumbnails = new HashMap<>();
            for (Track t : allTracks) {
                if (t.id != null && t.thumbnailUrl != null) {
                    thumbnails.put(t.id, t.thumbnailUrl);
                }
            }
            playlistAdapter.setTrackThumbnails(thumbnails);
            renderCurrentView();
        });

        if (getActivity() instanceof PlaybackHost) {
            detailTrackAdapter.setPlayingTrackId(((PlaybackHost) getActivity()).getCurrentPlayingTrackId());
        }
    }

    public boolean handleBackPress() {
        if (selectedPlaylistId != null) {
            selectedPlaylistId = null;
            renderCurrentView();
            return true;
        }
        return false;
    }

    public void updatePlayingTrackId(@Nullable String trackId) {
        if (detailTrackAdapter != null) {
            detailTrackAdapter.setPlayingTrackId(trackId);
        }
    }

    private void renderCurrentView() {
        if (binding == null) {
            return;
        }
        Playlist active = findSelectedPlaylist();
        if (selectedPlaylistId != null && active == null) {
            selectedPlaylistId = null;
        }

        if (active != null) {
            binding.layoutPlaylistsList.setVisibility(View.GONE);
            binding.layoutPlaylistDetail.setVisibility(View.VISIBLE);
            binding.tvDetailTitle.setText(active.name);

            List<Track> resolved = repository.resolvePlaylistTracks(active);
            binding.tvDetailCount.setText(formatTrackCount(resolved.size()));
            binding.btnPlayPlaylist.setEnabled(!resolved.isEmpty());
            binding.btnPlayPlaylist.setAlpha(resolved.isEmpty() ? 0.5f : 1.0f);

            if (resolved.isEmpty()) {
                binding.rvPlaylistTracks.setVisibility(View.GONE);
                binding.tvPlaylistDetailEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.tvPlaylistDetailEmpty.setVisibility(View.GONE);
                binding.rvPlaylistTracks.setVisibility(View.VISIBLE);
                if (!isDraggingTracks) {
                    detailTrackAdapter.submitList(resolved);
                }
            }
            return;
        }

        binding.layoutPlaylistDetail.setVisibility(View.GONE);
        binding.layoutPlaylistsList.setVisibility(View.VISIBLE);
        if (allPlaylists.isEmpty()) {
            binding.rvPlaylists.setVisibility(View.GONE);
            binding.layoutPlaylistsEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.layoutPlaylistsEmpty.setVisibility(View.GONE);
            binding.rvPlaylists.setVisibility(View.VISIBLE);
            if (!isDraggingPlaylists) {
                playlistAdapter.submitList(new ArrayList<>(allPlaylists));
            }
        }
    }

    private void attachPlaylistDragHelper() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0
        ) {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target
            ) {
                playlistAdapter.moveItem(
                        viewHolder.getBindingAdapterPosition(),
                        target.getBindingAdapterPosition()
                );
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    isDraggingPlaylists = true;
                    setDragLiftState(viewHolder.itemView, true);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                setDragLiftState(viewHolder.itemView, false);
                if (isDraggingPlaylists) {
                    isDraggingPlaylists = false;
                    repository.reorderPlaylists(playlistAdapter.getCurrentIds());
                }
            }
        });
        helper.attachToRecyclerView(binding.rvPlaylists);
    }

    private void attachTrackDragHelper() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0
        ) {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target
            ) {
                detailTrackAdapter.moveItem(
                        viewHolder.getBindingAdapterPosition(),
                        target.getBindingAdapterPosition()
                );
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    isDraggingTracks = true;
                    setDragLiftState(viewHolder.itemView, true);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                setDragLiftState(viewHolder.itemView, false);
                if (isDraggingTracks) {
                    isDraggingTracks = false;
                    if (selectedPlaylistId != null) {
                        repository.reorderPlaylistTracks(selectedPlaylistId, detailTrackAdapter.getCurrentIds());
                    }
                }
            }
        });
        helper.attachToRecyclerView(binding.rvPlaylistTracks);
    }

    private void setDragLiftState(View itemView, boolean lifted) {
        itemView.animate()
                .scaleX(lifted ? 1.02f : 1.0f)
                .scaleY(lifted ? 1.02f : 1.0f)
                .setDuration(120L)
                .start();
        if (itemView instanceof MaterialCardView) {
            float elevationPx = lifted ? 6f * getResources().getDisplayMetrics().density : 0f;
            ((MaterialCardView) itemView).setCardElevation(elevationPx);
        }
    }

    @Nullable
    private Playlist findSelectedPlaylist() {
        if (selectedPlaylistId == null) {
            return null;
        }
        for (Playlist playlist : allPlaylists) {
            if (selectedPlaylistId.equals(playlist.id)) {
                return playlist;
            }
        }
        return null;
    }

    private String formatTrackCount(int count) {
        if (count == 0) return getString(R.string.playlist_track_count_zero);
        if (count == 1) return getString(R.string.playlist_track_count_single);
        return getString(R.string.playlist_track_count_plural, count);
    }

    private void showPlaylistMenu(Playlist playlist, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_rename_playlist);
        menu.getMenu().add(0, 2, 1, R.string.menu_delete_playlist);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                PlaylistDialogs.showRenamePlaylistDialog(requireContext(), repository, playlist);
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDeletePlaylist(playlist);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showPlaylistTrackMenu(Track track, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_remove_from_playlist);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1 && selectedPlaylistId != null) {
                repository.removeTrackFromPlaylist(selectedPlaylistId, track.id);
                Toast.makeText(
                        requireContext(),
                        R.string.toast_removed_from_playlist,
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmDeletePlaylist(Playlist playlist) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_playlist_title)
                .setMessage(getString(R.string.dialog_delete_playlist_message, playlist.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deletePlaylist(playlist.id);
                    if (playlist.id.equals(selectedPlaylistId)) {
                        selectedPlaylistId = null;
                    }
                    Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_playlist_deleted, playlist.name),
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void showAddTracksPicker(Playlist playlist) {
        if (allTracks.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    R.string.toast_library_empty_for_playlist,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        String[] titles = new String[allTracks.size()];
        boolean[] checked = new boolean[allTracks.size()];
        for (int i = 0; i < allTracks.size(); i++) {
            Track t = allTracks.get(i);
            titles[i] = t.title != null ? t.title : "Audio Track";
            checked[i] = playlist.trackIds != null && playlist.trackIds.contains(t.id);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_add_tracks_title)
                .setMultiChoiceItems(titles, checked, (dialog, which, isChecked) ->
                        checked[which] = isChecked)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    for (int i = 0; i < allTracks.size(); i++) {
                        Track t = allTracks.get(i);
                        if (t.id == null) continue;
                        if (checked[i]) {
                            repository.addTrackToPlaylist(playlist.id, t.id);
                        } else {
                            repository.removeTrackFromPlaylist(playlist.id, t.id);
                        }
                    }
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

