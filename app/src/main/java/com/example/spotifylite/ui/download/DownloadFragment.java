package com.example.spotifylite.ui.download;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.spotifylite.R;
import com.example.spotifylite.data.TrackRepository;
import com.example.spotifylite.databinding.FragmentDownloadBinding;
import com.example.spotifylite.model.DownloadJobRecord;
import com.example.spotifylite.ui.PlaybackHost;
import com.example.spotifylite.utils.LinkResolver;
import com.example.spotifylite.workers.MusicDownloadWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DownloadFragment extends Fragment {
    private FragmentDownloadBinding binding;
    private WorkManager workManager;
    private TrackRepository repository;
    private DownloadQueueAdapter queueAdapter;

    private List<WorkInfo> latestWorkInfos = new ArrayList<>();
    private Map<String, DownloadJobRecord> latestJobRecords = new HashMap<>();
    @Nullable
    private UUID latestEnqueuedId;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentDownloadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context appContext = requireContext().getApplicationContext();
        workManager = WorkManager.getInstance(appContext);
        repository = TrackRepository.getInstance(appContext);

        setupForm();
        setupQueueList();
        observeWorkAndRecords();
    }

    private void setupForm() {
        updateUrlEndIcon("");
        highlightSourceChip(null);

        binding.tilMusicUrl.setEndIconOnClickListener(v -> {
            String current = binding.etMusicUrl.getText() != null
                    ? binding.etMusicUrl.getText().toString().trim()
                    : "";
            if (current.isEmpty()) {
                pasteFromClipboard();
            } else {
                binding.etMusicUrl.setText("");
            }
        });

        binding.etMusicUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s != null ? s.toString() : "";
                binding.tilMusicUrl.setError(null);
                updateUrlEndIcon(text);
                updateDetectedSourceHighlight(text);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) ->
                binding.tvWifiConstraintHelper.setText(
                        isChecked ? R.string.helper_wifi_only_on : R.string.helper_wifi_only_off
                )
        );

        binding.btnStartDownload.setOnClickListener(v -> {
            String url = binding.etMusicUrl.getText() != null
                    ? binding.etMusicUrl.getText().toString().trim()
                    : "";
            String customTitle = binding.etCustomTitle.getText() != null
                    ? binding.etCustomTitle.getText().toString().trim()
                    : "";
            boolean wifiOnly = binding.switchWifiOnly.isChecked();
            enqueueDownload(url, customTitle, wifiOnly, true);
        });

        binding.btnCheckStatus.setOnClickListener(v -> checkLatestDownloadStatus());
        binding.btnClearFinished.setOnClickListener(v -> clearFinishedJobs());
    }

    private void updateUrlEndIcon(String text) {
        boolean hasText = text != null && !text.trim().isEmpty();
        if (hasText) {
            binding.tilMusicUrl.setEndIconDrawable(R.drawable.ic_close);
            binding.tilMusicUrl.setEndIconContentDescription(getString(R.string.cd_clear_url));
            binding.tilMusicUrl.setEndIconTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            );
        } else {
            binding.tilMusicUrl.setEndIconDrawable(R.drawable.ic_paste);
            binding.tilMusicUrl.setEndIconContentDescription(getString(R.string.cd_paste_link));
            binding.tilMusicUrl.setEndIconTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent_primary))
            );
        }
    }

    private void updateDetectedSourceHighlight(String rawUrl) {
        if (!LinkResolver.isValidHttpUrl(rawUrl)) {
            highlightSourceChip(null);
            return;
        }
        highlightSourceChip(LinkResolver.detectPlatform(rawUrl));
    }

    private void highlightSourceChip(@Nullable String activePlatform) {
        styleSourceChip(binding.chipYoutube, LinkResolver.PLATFORM_YOUTUBE.equals(activePlatform));
        styleSourceChip(binding.chipSpotify, LinkResolver.PLATFORM_SPOTIFY.equals(activePlatform));
        styleSourceChip(binding.chipSoundcloud, LinkResolver.PLATFORM_SOUNDCLOUD.equals(activePlatform));
        styleSourceChip(binding.chipDirect, LinkResolver.PLATFORM_DIRECT.equals(activePlatform));
    }

    private void styleSourceChip(TextView chip, boolean active) {
        Context ctx = requireContext();
        int bgColor = ContextCompat.getColor(
                ctx,
                active ? R.color.accent_container : R.color.bg_surface_elevated
        );
        int fgColor = ContextCompat.getColor(
                ctx,
                active ? R.color.accent_on_container : R.color.text_secondary
        );
        chip.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        chip.setTextColor(fgColor);
    }

    private void setupQueueList() {
        queueAdapter = new DownloadQueueAdapter(new DownloadQueueAdapter.OnJobActionListener() {
            @Override
            public void onCancelJob(String workId) {
                workManager.cancelWorkById(UUID.fromString(workId));
            }

            @Override
            public void onRetryJob(DownloadJobRecord record) {
                enqueueDownload(record.url, record.customTitle, record.wifiOnly, false);
            }

            @Override
            public void onPlayCompletedTrack(String trackId) {
                if (getActivity() instanceof PlaybackHost) {
                    PlaybackHost host = (PlaybackHost) getActivity();
                    host.playTrackById(trackId);
                }
            }
        });
        binding.rvQueue.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvQueue.setAdapter(queueAdapter);
    }

    private void observeWorkAndRecords() {
        workManager.getWorkInfosByTagLiveData(MusicDownloadWorker.WORK_TAG)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    latestWorkInfos = workInfos != null ? workInfos : new ArrayList<>();
                    renderQueue();
                });

        repository.getJobsLiveData().observe(getViewLifecycleOwner(), map -> {
            latestJobRecords = map != null ? map : new HashMap<>();
            renderQueue();
        });
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(requireContext(), R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence text = clip.getItemAt(0).coerceToText(requireContext());
            if (text != null && text.length() > 0) {
                binding.etMusicUrl.setText(text.toString().trim());
                binding.etMusicUrl.setSelection(binding.etMusicUrl.length());
                return;
            }
        }
        Toast.makeText(requireContext(), R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show();
    }

    private void enqueueDownload(String url, String customTitle, boolean wifiOnly, boolean clearInputs) {
        if (url.isEmpty()) {
            binding.tilMusicUrl.setError(getString(R.string.error_empty_url));
            return;
        }
        if (!LinkResolver.isValidHttpUrl(url)) {
            binding.tilMusicUrl.setError(getString(R.string.error_invalid_url));
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .build();

        Data inputData = new Data.Builder()
                .putString(MusicDownloadWorker.KEY_URL, url)
                .putString(MusicDownloadWorker.KEY_CUSTOM_TITLE, customTitle)
                .putBoolean(MusicDownloadWorker.KEY_WIFI_ONLY, wifiOnly)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MusicDownloadWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(MusicDownloadWorker.WORK_TAG)
                .build();

        latestEnqueuedId = request.getId();
        String platform = LinkResolver.detectPlatform(url);
        repository.saveJobRecord(new DownloadJobRecord(
                request.getId().toString(),
                url,
                customTitle,
                platform,
                wifiOnly,
                System.currentTimeMillis()
        ));

        workManager.enqueue(request);

        boolean wifiReady = isUnmeteredWifiConnected();
        int toastRes = (wifiOnly && !wifiReady)
                ? R.string.toast_enqueued_wifi
                : R.string.toast_enqueued_now;
        Toast.makeText(requireContext(), toastRes, Toast.LENGTH_SHORT).show();

        if (clearInputs) {
            binding.etMusicUrl.setText("");
            binding.etCustomTitle.setText("");
            highlightSourceChip(null);
        }
    }

    private void checkLatestDownloadStatus() {
        WorkInfo target = findLatestWorkInfo();
        if (target == null) {
            Toast.makeText(requireContext(), R.string.toast_no_active_job, Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadJobRecord record = latestJobRecords.get(target.getId().toString());
        boolean wifiOnly = record != null && record.wifiOnly;
        String statusText;
        switch (target.getState()) {
            case ENQUEUED:
            case BLOCKED:
                statusText = (wifiOnly && !isUnmeteredWifiConnected())
                        ? getString(R.string.status_enqueued_wifi)
                        : getString(R.string.status_enqueued);
                break;
            case RUNNING:
                int progress = target.getProgress().getInt(MusicDownloadWorker.KEY_PROGRESS, 0);
                statusText = progress > 0
                        ? getString(R.string.status_running) + " (" + progress + "%)"
                        : getString(R.string.status_running);
                break;
            case SUCCEEDED:
                statusText = getString(R.string.status_succeeded);
                break;
            case FAILED:
                statusText = getString(R.string.status_failed);
                break;
            case CANCELLED:
                statusText = getString(R.string.status_cancelled);
                break;
            default:
                statusText = target.getState().name();
                break;
        }
        Toast.makeText(
                requireContext(),
                getString(R.string.status_toast_format, statusText),
                Toast.LENGTH_SHORT
        ).show();
    }

    @Nullable
    private WorkInfo findLatestWorkInfo() {
        if (latestWorkInfos.isEmpty()) {
            return null;
        }
        if (latestEnqueuedId != null) {
            for (WorkInfo info : latestWorkInfos) {
                if (latestEnqueuedId.equals(info.getId())) {
                    return info;
                }
            }
        }
        WorkInfo best = latestWorkInfos.get(0);
        long bestTime = getCreatedAt(best);
        for (WorkInfo info : latestWorkInfos) {
            long created = getCreatedAt(info);
            if (created > bestTime) {
                best = info;
                bestTime = created;
            }
        }
        return best;
    }

    private void clearFinishedJobs() {
        List<String> finishedIds = new ArrayList<>();
        for (WorkInfo info : latestWorkInfos) {
            if (info.getState().isFinished()) {
                finishedIds.add(info.getId().toString());
            }
        }
        workManager.pruneWork();
        repository.removeJobRecords(finishedIds);
    }

    private void renderQueue() {
        if (binding == null) {
            return;
        }
        boolean unmeteredWifi = isUnmeteredWifiConnected();
        List<DownloadQueueAdapter.QueueItem> queueItems = new ArrayList<>();
        boolean anyWaitingForWifi = false;
        boolean anyFinished = false;

        for (WorkInfo info : latestWorkInfos) {
            DownloadJobRecord record = latestJobRecords.get(info.getId().toString());
            if (info.getState().isFinished()) {
                anyFinished = true;
            }
            if ((info.getState() == WorkInfo.State.ENQUEUED || info.getState() == WorkInfo.State.BLOCKED)
                    && record != null && record.wifiOnly && !unmeteredWifi) {
                anyWaitingForWifi = true;
            }
            queueItems.add(new DownloadQueueAdapter.QueueItem(info, record, unmeteredWifi));
        }

        Collections.sort(queueItems, (a, b) -> {
            long timeA = a.record != null ? a.record.createdAtMs : 0L;
            long timeB = b.record != null ? b.record.createdAtMs : 0L;
            return Long.compare(timeB, timeA);
        });

        binding.bannerWaitingWifi.setVisibility(anyWaitingForWifi ? View.VISIBLE : View.GONE);
        binding.btnClearFinished.setVisibility(anyFinished ? View.VISIBLE : View.GONE);

        if (queueItems.isEmpty()) {
            binding.layoutQueueEmpty.setVisibility(View.VISIBLE);
            binding.rvQueue.setVisibility(View.GONE);
        } else {
            binding.layoutQueueEmpty.setVisibility(View.GONE);
            binding.rvQueue.setVisibility(View.VISIBLE);
            queueAdapter.submitList(queueItems);
        }
    }

    private long getCreatedAt(WorkInfo info) {
        DownloadJobRecord record = latestJobRecords.get(info.getId().toString());
        return record != null ? record.createdAtMs : 0L;
    }

    private boolean isUnmeteredWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network active = cm.getActiveNetwork();
        if (active == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    @Override
    public void onResume() {
        super.onResume();
        renderQueue();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
