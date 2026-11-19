package com.example.spotifylite.model;

public class DownloadJobRecord {
    public String workId;
    public String url;
    public String customTitle;
    public String platform;
    public boolean wifiOnly;
    public long createdAtMs;

    public DownloadJobRecord() {
    }

    public DownloadJobRecord(
            String workId,
            String url,
            String customTitle,
            String platform,
            boolean wifiOnly,
            long createdAtMs
    ) {
        this.workId = workId;
        this.url = url;
        this.customTitle = customTitle;
        this.platform = platform;
        this.wifiOnly = wifiOnly;
        this.createdAtMs = createdAtMs;
    }
}

