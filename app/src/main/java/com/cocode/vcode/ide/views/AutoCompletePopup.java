package com.cocode.vcode.ide.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.autocomplete.CompletionItem;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom completion suggestion overlay for the editor view.
 * Uses a lightweight, non-focusable PopupWindow wrapping a RecyclerView to display
 * context-aware code completions without stealing key events from the soft keyboard.
 */
public class AutoCompletePopup {

    private static final int WIDTH_DP = 280;
    private final Context context;
    private final PopupWindow popupWindow;
    private final AutoCompleteAdapter adapter;

    /**
     * Initializes the autocomplete popup component with default styling, structures, and sizing layouts.
     */
    public AutoCompletePopup(Context context) {
        this.context = context;
        this.adapter = new AutoCompleteAdapter();

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        recyclerView.setBackground(ContextCompat.getDrawable(context, R.drawable.vcode_bg_autocomplete_popup));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // Configure the popup panel window frame characteristics
        popupWindow = new PopupWindow(recyclerView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false); // Keeps soft input focus firmly inside the main text editing window
        popupWindow.setElevation(8f);
    }

    /**
     * Measures layout alignments and displays code proposals adjacent to the text cursor coordinates.
     * Integrates boundary collision processing to prevent rendering clipping zones behind keyboard areas.
     */
    public void show(List<CompletionItem> items, View editorView, int cursorOffset) {
        if (items == null || items.isEmpty()) {
            dismiss();
            return;
        }

        adapter.setItems(items);

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(UiUtils.dpToPx(context, WIDTH_DP), screenWidth - UiUtils.dpToPx(context, 32));
        popupWindow.setWidth(popupWidth);

        // Calculate estimated height for collision detection (roughly 48dp per item)
        int estimatedHeight = items.size() > 4 ? UiUtils.dpToPx(context, 216) : items.size() * UiUtils.dpToPx(context, 48);

        if (items.size() > 4) {
            popupWindow.setHeight(UiUtils.dpToPx(context, 216));
        } else {
            popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int[] editorLocation = new int[2];
        editorView.getLocationInWindow(editorLocation);

        float cursorX = 0;
        float cursorYBottom = 0;
        float cursorYTop = 0;
        int paddingTop = 0;
        int scrollY = 0;

        if (editorView instanceof android.widget.EditText) {
            android.widget.EditText et = (android.widget.EditText) editorView;
            paddingTop = et.getTotalPaddingTop();
            scrollY = et.getScrollY();

            if (et.getLayout() != null && cursorOffset >= 0 && cursorOffset <= et.getText().length()) {
                cursorX = et.getLayout().getPrimaryHorizontal(cursorOffset);

                int line = et.getLayout().getLineForOffset(cursorOffset);
                cursorYBottom = et.getLayout().getLineBottom(line);
                cursorYTop = et.getLayout().getLineTop(line);
            }
        }

        int x = editorLocation[0] + (int) cursorX - editorView.getScrollX();

        // Android screen bounds (dynamically shrinks when keyboard opens)
        android.graphics.Rect visibleFrame = new android.graphics.Rect();
        editorView.getWindowVisibleDisplayFrame(visibleFrame);

        // Theoretical placement coords
        int yBelow = editorLocation[1] + paddingTop + (int) cursorYBottom - scrollY + UiUtils.dpToPx(context, 4);
        int yAbove = editorLocation[1] + paddingTop + (int) cursorYTop - scrollY - estimatedHeight - UiUtils.dpToPx(context, 4);

        int y;

        // If placing it below pushes into the keyboard area, flip it to the top
        if (yBelow + estimatedHeight > visibleFrame.bottom) {
            y = Math.max(visibleFrame.top, yAbove); // Prevent clipping off-screen bounds
        } else {
            y = yBelow;
        }

        if (x + popupWidth > screenWidth) {
            x = screenWidth - popupWidth - UiUtils.dpToPx(context, 8);
        }
        x = Math.max(0, x);

        if (popupWindow.isShowing()) {
            popupWindow.update(x, y, popupWidth, -1);
        } else {
            popupWindow.showAtLocation(editorView, Gravity.NO_GRAVITY, x, y);
        }
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        adapter.setListener(listener);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(CompletionItem item);
    }

    /**
     * Internal data management adapter linking suggestible completions lists into presentation line items.
     */
    private class AutoCompleteAdapter extends RecyclerView.Adapter<AutoCompleteAdapter.ViewHolder> {

        private List<CompletionItem> items = new ArrayList<>();
        private OnItemSelectedListener listener;

        @SuppressLint("NotifyDataSetChanged")
        void setItems(List<CompletionItem> items) {
            this.items = items != null ? items : new ArrayList<>();
            notifyDataSetChanged();
        }

        void setListener(OnItemSelectedListener l) {
            this.listener = l;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Programmatically establish item row layout styling constructs
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(UiUtils.dpToPx(context, 12), UiUtils.dpToPx(context, 8), UiUtils.dpToPx(context, 12), UiUtils.dpToPx(context, 8));
            row.setMinimumHeight(UiUtils.dpToPx(context, 48));
            row.setBackground(buildRippleBackground());

            // Build indicator symbol circle containers
            TextView badge = new TextView(context);
            int badgeSize = UiUtils.dpToPx(context, 22);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(badgeSize, badgeSize);
            badgeParams.setMarginEnd(UiUtils.dpToPx(context, 10));
            badge.setLayoutParams(badgeParams);
            badge.setGravity(android.view.Gravity.CENTER);
            badge.setTextSize(8);
            badge.setTextColor(Color.WHITE);
            badge.setTypeface(FontManager.getInstance().getUiFont(context), Typeface.BOLD);
            row.addView(badge);

            // Build completion target text layout
            TextView label = new TextView(context);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(labelParams);
            label.setTextSize(14);
            label.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
            label.setMaxLines(1);
            row.addView(label);

            // Build secondary descriptive detail markers
            TextView detail = new TextView(context);
            detail.setTextSize(11);
            detail.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_secondary));
            detail.setTypeface(FontManager.getInstance().getUiFont(context));
            detail.setMaxLines(1);
            row.addView(detail);

            return new ViewHolder(row, badge, label, detail);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CompletionItem item = items.get(position);

            holder.badge.setBackground(buildCircleDrawable(getBadgeColor(item.getType())));
            holder.badge.setText(getBadgeLetter(item.getType()));

            String labelText = item.getLabel();
            if (labelText != null && labelText.length() > 15) {
                holder.label.setText(labelText.substring(0, 15) + "...");
            } else {
                holder.label.setText(labelText != null ? labelText : "");
            }

            // Emphasize the top matches group by applying standard bold weightings to the first item row entry
            holder.label.setTypeface(FontManager.getInstance().getCodeFont(context),
                    position == 0 ? Typeface.BOLD : Typeface.NORMAL);

            holder.detail.setText(item.getDetail() != null ? item.getDetail() : "");

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemSelected(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * Resolves unique color accents assigned to distinguish varying identifier tokens categories.
         */
        private int getBadgeColor(CompletionItem.Type type) {
            if (type == null) return ContextCompat.getColor(context, R.color.vcode_text_secondary);
            switch (type) {
                case TAG:
                case BUILTIN:
                    return ContextCompat.getColor(context, R.color.vcode_accent_primary);
                case ATTRIBUTE:
                case CSS_PROPERTY:
                    return ContextCompat.getColor(context, R.color.vcode_accent_secondary);
                case VALUE:
                case CSS_VALUE:
                case FUNCTION:
                    return ContextCompat.getColor(context, R.color.vcode_accent_success);
                case KEYWORD:
                    return ContextCompat.getColor(context, R.color.vcode_accent_warning);
                case SNIPPET:
                case JSON_KEY:
                    return ContextCompat.getColor(context, R.color.vcode_accent_json);
                default:
                    return ContextCompat.getColor(context, R.color.vcode_text_secondary);
            }
        }

        /**
         * Resolves short textual shorthand letters representing specific code tokens classifications.
         */
        private String getBadgeLetter(CompletionItem.Type type) {
            if (type == null) return "?";
            switch (type) {
                case TAG:
                    return "T";
                case ATTRIBUTE:
                    return "A";
                case VALUE:
                case CSS_VALUE:
                    return "V";
                case CSS_PROPERTY:
                    return "P";
                case KEYWORD:
                    return "K";
                case FUNCTION:
                    return "F";
                case BUILTIN:
                    return "B";
                case SNIPPET:
                    return "S";
                case JSON_KEY:
                    return "J";
                default:
                    return "?";
            }
        }

        private android.graphics.drawable.GradientDrawable buildCircleDrawable(int color) {
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            d.setColor(color);
            return d;
        }

        private android.graphics.drawable.Drawable buildRippleBackground() {
            android.graphics.drawable.ColorDrawable bg = new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
            android.content.res.ColorStateList rippleColor = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.vcode_selection_color));
            return new android.graphics.drawable.RippleDrawable(rippleColor, bg, null);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView badge, label, detail;

            ViewHolder(View root, TextView badge, TextView label, TextView detail) {
                super(root);
                this.badge = badge;
                this.label = label;
                this.detail = detail;
            }
        }
    }
}