package com.example.spotifylite.ui.download;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;

import com.example.spotifylite.R;
import com.example.spotifylite.databinding.ItemDownloadTaskBinding;
import com.example.spotifylite.model.DownloadJobRecord;
import com.example.spotifylite.utils.LinkResolver;
import com.example.spotifylite.workers.MusicDownloadWorker;

import java.util.ArrayList;
import java.util.List;

public class DownloadQueueAdapter extends RecyclerView.Adapter<DownloadQueueAdapter.JobViewHolder> {

    public static final class QueueItem {
        public final WorkInfo workInfo;
        @Nullable
        public final DownloadJobRecord record;
        public final boolean unmeteredWifiConnected;

        public QueueItem(WorkInfo workInfo, @Nullable DownloadJobRecord record, boolean unmeteredWifiConnected) {
            this.workInfo = workInfo;
            this.record = record;
            this.unmeteredWifiConnected = unmeteredWifiConnected;
        }
    }

    public interface OnJobActionListener {
        void onCancelJob(String workId);

        void onRetryJob(DownloadJobRecord record);

        void onPlayCompletedTrack(String trackId);
    }

    private final List<QueueItem> items = new ArrayList<>();
    private final OnJobActionListener listener;

    public DownloadQueueAdapter(OnJobActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<QueueItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDownloadTaskBinding binding = ItemDownloadTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new JobViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class JobViewHolder extends RecyclerView.ViewHolder {
        private final ItemDownloadTaskBinding binding;

        JobViewHolder(ItemDownloadTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(QueueItem item) {
            Context ctx = binding.getRoot().getContext();
            WorkInfo info = item.workInfo;
            WorkInfo.State state = info.getState();

            String progressTitle = info.getProgress().getString(MusicDownloadWorker.KEY_TITLE);
            String outputTitle = info.getOutputData().getString(MusicDownloadWorker.KEY_TITLE);
            String recordTitle = item.record != null && item.record.customTitle != null && !item.record.customTitle.isEmpty()
                    ? item.record.customTitle
                    : (item.record != null ? item.record.url : info.getId().toString());

            String title = firstNonEmpty(outputTitle, progressTitle, recordTitle);
            binding.tvJobTitle.setText(title);

            String platformLabel = formatPlatform(ctx, item.record != null ? item.record.platform : LinkResolver.PLATFORM_OTHER);
            boolean wifiOnly = item.record != null && item.record.wifiOnly;

            binding.tvJobDetail.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            binding.progressJob.setVisibility(View.GONE);
            binding.btnJobAction.setVisibility(View.VISIBLE);

            if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED) {
                boolean waitingForWifi = wifiOnly && !item.unmeteredWifiConnected;
                applyBadge(
                        ctx,
                        waitingForWifi ? R.color.status_warning_bg : R.color.accent_container,
                        waitingForWifi ? R.color.status_warning_fg : R.color.accent_on_container,
                        waitingForWifi ? R.drawable.ic_wifi : R.drawable.ic_download,
                        waitingForWifi ? R.string.status_enqueued_wifi : R.string.status_enqueued
                );
                String detail = waitingForWifi
                        ? platformLabel + " · Waiting for Wi-Fi connection"
                        : platformLabel + " · Waiting to start";
                binding.tvJobDetail.setText(detail);
                binding.progressJob.setVisibility(View.VISIBLE);
                binding.progressJob.setIndeterminate(true);
                binding.btnJobAction.setImageResource(R.drawable.ic_close);
                binding.btnJobAction.setContentDescription(ctx.getString(R.string.cd_cancel_download));
                binding.btnJobAction.setOnClickListener(v -> listener.onCancelJob(info.getId().toString()));
            } else if (state == WorkInfo.State.RUNNING) {
                int progress = info.getProgress().getInt(MusicDownloadWorker.KEY_PROGRESS, 0);
                String stage = info.getProgress().getString(MusicDownloadWorker.KEY_STAGE);
                applyBadge(
                        ctx,
                        R.color.accent_container,
                        R.color.accent_on_container,
                        R.drawable.ic_download,
                        R.string.status_running
                );
                String stageText = (stage != null && !stage.isEmpty()) ? stage : "Downloading...";
                binding.tvJobDetail.setText(platformLabel + " · " + stageText);
                binding.progressJob.setVisibility(View.VISIBLE);
                binding.progressJob.setIndeterminate(progress <= 0);
                if (progress > 0) {
                    binding.progressJob.setProgress(progress);
                }
                binding.btnJobAction.setImageResource(R.drawable.ic_close);
                binding.btnJobAction.setContentDescription(ctx.getString(R.string.cd_cancel_download));
                binding.btnJobAction.setOnClickListener(v -> listener.onCancelJob(info.getId().toString()));
            } else if (state == WorkInfo.State.SUCCEEDED) {
                applyBadge(
                        ctx,
                        R.color.status_success_bg,
                        R.color.status_success_fg,
                        R.drawable.ic_check,
                        R.string.status_succeeded
                );
                binding.tvJobDetail.setText(platformLabel + " · Ready to play offline");
                String trackId = info.getOutputData().getString(MusicDownloadWorker.KEY_TRACK_ID);
                if (trackId != null && !trackId.isEmpty()) {
                    binding.btnJobAction.setImageResource(R.drawable.ic_play);
                    binding.btnJobAction.setContentDescription(ctx.getString(R.string.cd_play_downloaded));
                    binding.btnJobAction.setOnClickListener(v -> listener.onPlayCompletedTrack(trackId));
                } else {
                    binding.btnJobAction.setVisibility(View.GONE);
                }
            } else if (state == WorkInfo.State.FAILED) {
                applyBadge(
                        ctx,
                        R.color.status_error_bg,
                        R.color.status_error_fg,
                        R.drawable.ic_error,
                        R.string.status_failed
                );
                String errorMsg = info.getOutputData().getString(MusicDownloadWorker.KEY_ERROR_MESSAGE);
                binding.tvJobDetail.setText(errorMsg != null && !errorMsg.isEmpty()
                        ? errorMsg
                        : "Unable to download audio.");
                binding.tvJobDetail.setTextColor(ContextCompat.getColor(ctx, R.color.status_error_fg));
                if (item.record != null) {
                    binding.btnJobAction.setImageResource(R.drawable.ic_retry);
                    binding.btnJobAction.setContentDescription(ctx.getString(R.string.cd_retry_download));
                    binding.btnJobAction.setOnClickListener(v -> listener.onRetryJob(item.record));
                } else {
                    binding.btnJobAction.setVisibility(View.GONE);
                }
            } else {
                applyBadge(
                        ctx,
                        R.color.bg_surface_elevated,
                        R.color.text_secondary,
                        R.drawable.ic_close,
                        R.string.status_cancelled
                );
                binding.tvJobDetail.setText(platformLabel + " · Cancelled");
                if (item.record != null) {
                    binding.btnJobAction.setImageResource(R.drawable.ic_retry);
                    binding.btnJobAction.setContentDescription(ctx.getString(R.string.cd_retry_download));
                    binding.btnJobAction.setOnClickListener(v -> listener.onRetryJob(item.record));
                } else {
                    binding.btnJobAction.setVisibility(View.GONE);
                }
            }
        }

        private void applyBadge(Context ctx, int bgRes, int fgRes, int iconRes, int labelRes) {
            int bgColor = ContextCompat.getColor(ctx, bgRes);
            int fgColor = ContextCompat.getColor(ctx, fgRes);
            binding.badgeStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
            binding.ivStatusIcon.setImageResource(iconRes);
            binding.ivStatusIcon.setImageTintList(ColorStateList.valueOf(fgColor));
            binding.tvStatusLabel.setText(labelRes);
            binding.tvStatusLabel.setTextColor(fgColor);
        }

        private String formatPlatform(Context ctx, String platform) {
            if (LinkResolver.PLATFORM_YOUTUBE.equals(platform)) return ctx.getString(R.string.source_youtube);
            if (LinkResolver.PLATFORM_SPOTIFY.equals(platform)) return "Spotify";
            if (LinkResolver.PLATFORM_SOUNDCLOUD.equals(platform)) return ctx.getString(R.string.source_soundcloud);
            if (LinkResolver.PLATFORM_DIRECT.equals(platform)) return ctx.getString(R.string.source_direct);
            return ctx.getString(R.string.source_other);
        }

        private String firstNonEmpty(String... values) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
            return "Music Track";
        }
    }
}

