package com.cocode.vcode.ide.utils;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Structural spacing offset layout provider specialized for grid list structures views.
 * Programmatically appends explicit margin padding configurations along item boundaries
 * without generating dummy view items inside lists layouts.
 */
public class MarginItemDecorator extends RecyclerView.ItemDecoration {
    private final int topMargin;
    private final int bottomMargin;
    private final int betweenMargin;

    public MarginItemDecorator(int topMargin, int bottomMargin, int betweenMargin) {
        this.topMargin = topMargin;
        this.bottomMargin = bottomMargin;
        this.betweenMargin = betweenMargin;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return; // Exit if the targeted item is currently undergoing animation shifts or extraction removal loops
        }

        int itemCount = state.getItemCount();

        if (position == 0) {
            // Apply leading spacing properties across the absolute first entry of the current list layout
            outRect.set(0, topMargin, 0, betweenMargin);
        } else if (position == itemCount - 1) {
            // Apply terminal balancing spacing dimensions against trailing list footer nodes
            outRect.set(0, 0, 0, bottomMargin);
        } else {
            // Standard interior separation dimensions applied between sequential sibling cell items
            outRect.set(0, 0, 0, betweenMargin);
        }
    }
}