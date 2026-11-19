package com.example.spotifylite.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AudioFileUtils {
    private static final String SUBFOLDER = "SpotifyLite";

    private AudioFileUtils() {
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public static final class SavedAudioResult {
        public final String contentUri;
        public final String fileName;
        public final long fileSizeBytes;
        public final long durationMs;

        public SavedAudioResult(String contentUri, String fileName, long fileSizeBytes, long durationMs) {
            this.contentUri = contentUri;
            this.fileName = fileName;
            this.fileSizeBytes = fileSizeBytes;
            this.durationMs = durationMs;
        }
    }

    public static SavedAudioResult downloadAndSaveAudio(
            Context context,
            String streamUrl,
            String displayTitle,
            String artist,
            String extension,
            String mimeType,
            long knownDurationMs,
            @Nullable ProgressListener progressListener
    ) throws IOException {
        String fileName = LinkResolver.sanitizeFileName(displayTitle, extension);
        OkHttpClient client = OkHttpNewPipeDownloader.getInstance().getHttpClient();
        Request request = new Request.Builder()
                .url(streamUrl)
                .header("User-Agent", OkHttpNewPipeDownloader.USER_AGENT)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Audio download failed (HTTP " + response.code() + ").");
            }

            ResponseBody body = response.body();
            long totalBytes = body.contentLength();
            InputStream inputStream = body.byteStream();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return writeViaMediaStore(
                        context,
                        inputStream,
                        totalBytes,
                        fileName,
                        displayTitle,
                        artist,
                        mimeType,
                        knownDurationMs,
                        progressListener
                );
            }
            return writeToAppMusicDir(
                    context,
                    inputStream,
                    totalBytes,
                    fileName,
                    knownDurationMs,
                    progressListener
            );
        }
    }

    private static SavedAudioResult writeViaMediaStore(
            Context context,
            InputStream inputStream,
            long totalBytes,
            String fileName,
            String displayTitle,
            String artist,
            String mimeType,
            long knownDurationMs,
            @Nullable ProgressListener progressListener
    ) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Audio.Media.TITLE, displayTitle);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + SUBFOLDER);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Could not create audio file in MediaStore.");
        }

        long writtenBytes;
        try (OutputStream outputStream = resolver.openOutputStream(uri)) {
            if (outputStream == null) {
                throw new IOException("Could not open MediaStore output stream.");
            }
            writtenBytes = copyWithProgress(inputStream, outputStream, totalBytes, progressListener);
        } catch (IOException e) {
            resolver.delete(uri, null, null);
            throw e;
        }

        ContentValues publishValues = new ContentValues();
        publishValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
        resolver.update(uri, publishValues, null, null);

        long finalDuration = knownDurationMs > 0
                ? knownDurationMs
                : extractDurationMs(context, uri);
        return new SavedAudioResult(uri.toString(), fileName, writtenBytes, finalDuration);
    }

    private static SavedAudioResult writeToAppMusicDir(
            Context context,
            InputStream inputStream,
            long totalBytes,
            String fileName,
            long knownDurationMs,
            @Nullable ProgressListener progressListener
    ) throws IOException {
        File musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (musicDir == null) {
            musicDir = new File(context.getFilesDir(), "Music");
        }
        File targetDir = new File(musicDir, SUBFOLDER);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Could not create music storage folder.");
        }

        File outFile = resolveUniqueFile(targetDir, fileName);
        long writtenBytes;
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            writtenBytes = copyWithProgress(inputStream, fos, totalBytes, progressListener);
        } catch (IOException e) {
            // Remove partial file so broken tracks never appear in storage.
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            throw e;
        }

        Uri fileUri = Uri.fromFile(outFile);
        long finalDuration = knownDurationMs > 0
                ? knownDurationMs
                : extractDurationMs(context, fileUri);
        return new SavedAudioResult(fileUri.toString(), outFile.getName(), writtenBytes, finalDuration);
    }

    private static File resolveUniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    private static long copyWithProgress(
            InputStream input,
            OutputStream output,
            long totalBytes,
            @Nullable ProgressListener progressListener
    ) throws IOException {
        byte[] buffer = new byte[8192];
        long totalRead = 0L;
        int lastPercent = -1;
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            totalRead += read;
            if (progressListener != null && totalBytes > 0) {
                int percent = (int) Math.min(100L, (totalRead * 100L) / totalBytes);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    progressListener.onProgress(percent);
                }
            }
        }
        output.flush();
        if (progressListener != null && lastPercent < 100) {
            progressListener.onProgress(100);
        }
        return totalRead;
    }

    private static long extractDurationMs(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                return Long.parseLong(durationStr);
            }
        } catch (Exception ignored) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
                // Ignore release failure.
            }
        }
        return 0L;
    }

    public static boolean audioExists(Context context, @Nullable String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(uriString);
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            return path != null && new File(path).exists();
        }
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{MediaStore.Audio.Media._ID},
                null,
                null,
                null
        )) {
            return cursor != null && cursor.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    public static void deleteAudio(Context context, @Nullable String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            return;
        }
        Uri uri = Uri.parse(uriString);
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            }
            return;
        }
        try {
            context.getContentResolver().delete(uri, null, null);
        } catch (Exception ignored) {
            // File may already have been deleted externally.
        }
    }
}

