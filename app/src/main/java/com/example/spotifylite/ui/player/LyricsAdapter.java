package com.example.spotifylite.ui.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spotifylite.R;
import com.example.spotifylite.model.LrcLine;

import java.util.ArrayList;
import java.util.List;

public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.LyricViewHolder> {

    private static final String PAYLOAD_ACTIVE_CHANGE = "active_change";
    private static final long ANIM_DURATION_MS = 220L;
    private static final float ACTIVE_SCALE = 1.06f;
    private static final float INACTIVE_ALPHA = 0.65f;

    public interface OnLyricLineClickListener {
        void onLineClicked(long timeMs);
    }

    private final List<LrcLine> lines = new ArrayList<>();
    private final OnLyricLineClickListener listener;
    private int activeIndex = -1;

    public LyricsAdapter(OnLyricLineClickListener listener) {
        this.listener = listener;
    }

    public void submitLines(List<LrcLine> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        activeIndex = -1;
        notifyDataSetChanged();
    }

    public void setActiveIndex(int newIndex) {
        if (this.activeIndex == newIndex) {
            return;
        }
        int previous = this.activeIndex;
        this.activeIndex = newIndex;
        if (previous >= 0 && previous < lines.size()) {
            notifyItemChanged(previous, PAYLOAD_ACTIVE_CHANGE);
        }
        if (newIndex >= 0 && newIndex < lines.size()) {
            notifyItemChanged(newIndex, PAYLOAD_ACTIVE_CHANGE);
        }
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    @NonNull
    @Override
    public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lyric_line, parent, false);
        return new LyricViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
        holder.bind(lines.get(position), position == activeIndex, false);
    }

    @Override
    public void onBindViewHolder(
            @NonNull LyricViewHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (payloads.contains(PAYLOAD_ACTIVE_CHANGE)) {
            holder.bind(lines.get(position), position == activeIndex, true);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    class LyricViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvLine;
        @Nullable
        private ValueAnimator colorAnimator;

        LyricViewHolder(TextView itemView) {
            super(itemView);
            this.tvLine = itemView;
        }

        void bind(LrcLine line, boolean isActive, boolean animate) {
            Context ctx = tvLine.getContext();
            tvLine.setText(line.text);
            tvLine.setTextSize(15.5f);
            tvLine.setPivotX(0f);

            Typeface typeface = ResourcesCompat.getFont(
                    ctx,
                    isActive ? R.font.outfit_semibold : R.font.work_sans_medium
            );
            if (typeface != null) {
                tvLine.setTypeface(typeface);
            }

            int targetColor = ContextCompat.getColor(
                    ctx,
                    isActive ? R.color.accent_primary : R.color.text_secondary
            );
            float targetScale = isActive ? ACTIVE_SCALE : 1.0f;
            float targetAlpha = isActive ? 1.0f : INACTIVE_ALPHA;

            if (colorAnimator != null) {
                colorAnimator.cancel();
                colorAnimator = null;
            }
            tvLine.animate().cancel();

            if (animate) {
                int startColor = tvLine.getCurrentTextColor();
                colorAnimator = ValueAnimator.ofArgb(startColor, targetColor);
                colorAnimator.setDuration(ANIM_DURATION_MS);
                colorAnimator.setInterpolator(new DecelerateInterpolator());
                colorAnimator.addUpdateListener(anim ->
                        tvLine.setTextColor((int) anim.getAnimatedValue()));
                colorAnimator.start();

                tvLine.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .alpha(targetAlpha)
                        .setDuration(ANIM_DURATION_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                tvLine.setTextColor(targetColor);
                tvLine.setScaleX(targetScale);
                tvLine.setScaleY(targetScale);
                tvLine.setAlpha(targetAlpha);
            }

            tvLine.setOnClickListener(v -> listener.onLineClicked(line.timeMs));
        }
    }
}
