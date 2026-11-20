package com.example.spotifylite.ui.playlists;

import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.spotifylite.R;
import com.example.spotifylite.data.TrackRepository;
import com.example.spotifylite.model.Playlist;
import com.example.spotifylite.model.Track;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public final class PlaylistDialogs {

    public interface OnPlaylistCreatedListener {
        void onCreated(Playlist playlist);
    }

    private PlaylistDialogs() {
    }

    public static void showCreatePlaylistDialog(
            Context context,
            TrackRepository repository,
            @Nullable OnPlaylistCreatedListener listener
    ) {
        showNameInputDialog(
                context,
                context.getString(R.string.dialog_new_playlist_title),
                "",
                context.getString(R.string.action_create),
                name -> {
                    Playlist created = repository.createPlaylist(name);
                    Toast.makeText(
                            context,
                            context.getString(R.string.toast_playlist_created, created.name),
                            Toast.LENGTH_SHORT
                    ).show();
                    if (listener != null) {
                        listener.onCreated(created);
                    }
                }
        );
    }

    public static void showRenamePlaylistDialog(
            Context context,
            TrackRepository repository,
            Playlist playlist
    ) {
        showNameInputDialog(
                context,
                context.getString(R.string.dialog_rename_playlist_title),
                playlist.name != null ? playlist.name : "",
                context.getString(R.string.action_save),
                name -> repository.renamePlaylist(playlist.id, name)
        );
    }

    public static void showAddToPlaylistDialog(
            Context context,
            TrackRepository repository,
            Track track
    ) {
        List<Playlist> playlists = repository.getPlaylistsSnapshot();
        String[] items = new String[playlists.size() + 1];
        for (int i = 0; i < playlists.size(); i++) {
            items[i] = playlists.get(i).name;
        }
        items[playlists.size()] = context.getString(R.string.dialog_create_new_playlist_option);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_choose_playlist_title)
                .setItems(items, (dialog, which) -> {
                    if (which == playlists.size()) {
                        showCreatePlaylistDialog(context, repository, created ->
                                addTrackAndNotify(context, repository, created, track));
                    } else {
                        addTrackAndNotify(context, repository, playlists.get(which), track);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static void addTrackAndNotify(
            Context context,
            TrackRepository repository,
            Playlist playlist,
            Track track
    ) {
        boolean added = repository.addTrackToPlaylist(playlist.id, track.id);
        Toast.makeText(
                context,
                context.getString(
                        added ? R.string.toast_added_to_playlist : R.string.toast_already_in_playlist,
                        playlist.name
                ),
                Toast.LENGTH_SHORT
        ).show();
    }

    private interface OnNameConfirmed {
        void onConfirmed(String name);
    }

    private static void showNameInputDialog(
            Context context,
            String title,
            String initialText,
            String positiveLabel,
            OnNameConfirmed onConfirmed
    ) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.hint_playlist_name);
        input.setText(initialText);
        input.setSelection(initialText.length());

        FrameLayout container = new FrameLayout(context);
        int marginPx = Math.round(20 * context.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(marginPx, marginPx / 2, marginPx, 0);
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(positiveLabel, (dialog, which) -> {
                    String entered = input.getText().toString().trim();
                    if (!entered.isEmpty()) {
                        onConfirmed.onConfirmed(entered);
                    }
                })
                .show();
    }
}

