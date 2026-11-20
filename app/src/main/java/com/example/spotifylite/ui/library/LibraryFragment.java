package com.example.spotifylite.ui.library;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifylite.R;
import com.example.spotifylite.data.TrackRepository;
import com.example.spotifylite.databinding.FragmentLibraryBinding;
import com.example.spotifylite.model.Track;
import com.example.spotifylite.ui.PlaybackHost;
import com.example.spotifylite.ui.playlists.PlaylistDialogs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private TrackRepository repository;
    private TrackAdapter adapter;

    private List<Track> allTracks = new ArrayList<>();
    private String currentQuery = "";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = TrackRepository.getInstance(requireContext());

        adapter = new TrackAdapter(new TrackAdapter.OnTrackInteractionListener() {
            @Override
            public void onTrackClicked(Track track) {
                if (getActivity() instanceof PlaybackHost) {
                    PlaybackHost host = (PlaybackHost) getActivity();
                    host.playTrackFromLibrary(track, filterTracks(allTracks, currentQuery));
                }
            }

            @Override
            public void onTrackMoreClicked(Track track, View anchor) {
                showTrackMenu(track, anchor);
            }
        });

        binding.rvTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTracks.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s != null ? s.toString().trim() : "";
                applyFilterAndRender();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnGoToDownload.setOnClickListener(v -> {
            if (getActivity() instanceof PlaybackHost) {
                ((PlaybackHost) getActivity()).switchToDownloadTab();
            }
        });

        binding.btnClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));

        repository.getTracksLiveData().observe(getViewLifecycleOwner(), tracks -> {
            allTracks = tracks != null ? tracks : new ArrayList<>();
            applyFilterAndRender();
        });

        if (getActivity() instanceof PlaybackHost) {
            adapter.setPlayingTrackId(((PlaybackHost) getActivity()).getCurrentPlayingTrackId());
        }
    }

    public void updatePlayingTrackId(@Nullable String trackId) {
        if (adapter != null) {
            adapter.setPlayingTrackId(trackId);
        }
    }

    private void showTrackMenu(Track track, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_add_to_playlist);
        menu.getMenu().add(0, 2, 1, R.string.menu_delete_track);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                PlaylistDialogs.showAddToPlaylistDialog(requireContext(), repository, track);
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDeleteTrack(track);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmDeleteTrack(Track track) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, track.title))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deleteTrack(track);
                    if (getActivity() instanceof PlaybackHost) {
                        ((PlaybackHost) getActivity()).onTrackDeleted(track);
                    }
                    Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_track_deleted, track.title),
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void applyFilterAndRender() {
        if (binding == null) {
            return;
        }
        int total = allTracks.size();
        if (total == 0) {
            binding.tvTrackCount.setVisibility(View.GONE);
            binding.tilSearch.setVisibility(View.GONE);
            binding.rvTracks.setVisibility(View.GONE);
            binding.layoutSearchEmpty.setVisibility(View.GONE);
            binding.layoutLibraryEmpty.setVisibility(View.VISIBLE);
            return;
        }

        binding.tilSearch.setVisibility(View.VISIBLE);
        binding.tvTrackCount.setVisibility(View.VISIBLE);
        binding.tvTrackCount.setText(total == 1
                ? getString(R.string.library_count_single)
                : getString(R.string.library_count_plural, total));

        List<Track> filtered = filterTracks(allTracks, currentQuery);
        if (filtered.isEmpty()) {
            binding.rvTracks.setVisibility(View.GONE);
            binding.layoutLibraryEmpty.setVisibility(View.GONE);
            binding.layoutSearchEmpty.setVisibility(View.VISIBLE);
            binding.tvSearchEmptyBody.setText(getString(R.string.search_empty_body, currentQuery));
        } else {
            binding.layoutLibraryEmpty.setVisibility(View.GONE);
            binding.layoutSearchEmpty.setVisibility(View.GONE);
            binding.rvTracks.setVisibility(View.VISIBLE);
            adapter.submitList(filtered);
        }
    }

    private List<Track> filterTracks(List<Track> source, String query) {
        if (query == null || query.isEmpty()) {
            return new ArrayList<>(source);
        }
        String lower = query.toLowerCase(Locale.US);
        List<Track> matches = new ArrayList<>();
        for (Track track : source) {
            boolean matchesTitle = track.title != null && track.title.toLowerCase(Locale.US).contains(lower);
            boolean matchesArtist = track.artist != null && track.artist.toLowerCase(Locale.US).contains(lower);
            boolean matchesPlatform = track.sourcePlatform != null
                    && track.sourcePlatform.toLowerCase(Locale.US).contains(lower);
            if (matchesTitle || matchesArtist || matchesPlatform) {
                matches.add(track);
            }
        }
        return matches;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository != null) {
            repository.pruneMissingFiles();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
