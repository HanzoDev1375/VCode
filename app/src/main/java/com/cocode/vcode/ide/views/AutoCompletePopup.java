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
import com.cocode.vcode.ide.databinding.ItemAutocompleteSuggestionBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import android.graphics.drawable.GradientDrawable;

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
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);
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

        // Calculate estimated height for collision detection (roughly 38dp per item)
        int estimatedHeight = items.size() > 5 ? UiUtils.dpToPx(context, 190) : items.size() * UiUtils.dpToPx(context, 38);

        if (items.size() > 5) {
            popupWindow.setHeight(UiUtils.dpToPx(context, 190));
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
        private final int colorPrimary, colorSecondary, colorSuccess, colorWarning, colorJson, colorTextSecondary, colorTextPrimary;
        private final Typeface uiFont, uiFontBold, codeFont;

        AutoCompleteAdapter() {
            colorPrimary = ContextCompat.getColor(context, R.color.vcode_accent_primary);
            colorSecondary = ContextCompat.getColor(context, R.color.vcode_accent_secondary);
            colorSuccess = ContextCompat.getColor(context, R.color.vcode_accent_success);
            colorWarning = ContextCompat.getColor(context, R.color.vcode_accent_warning);
            colorJson = ContextCompat.getColor(context, R.color.vcode_accent_json);
            colorTextSecondary = ContextCompat.getColor(context, R.color.vcode_text_secondary);
            colorTextPrimary = ContextCompat.getColor(context, R.color.vcode_text_primary);
            uiFont = FontManager.getInstance().getUiFont(context);
            uiFontBold = Typeface.create(uiFont, Typeface.BOLD);
            codeFont = FontManager.getInstance().getCodeFont(context);
        }

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
            ItemAutocompleteSuggestionBinding binding = ItemAutocompleteSuggestionBinding.inflate(
                    android.view.LayoutInflater.from(context), parent, false);
            return new ViewHolder(binding);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CompletionItem item = items.get(position);

            GradientDrawable badgeBg = (GradientDrawable) holder.binding.tvTypeBadge.getBackground();
            if (badgeBg != null) {
                badgeBg.setColor(getBadgeColor(item.getType()));
            }
            holder.binding.tvTypeBadge.setText(getBadgeLetter(item.getType()));
            holder.binding.tvTypeBadge.setTypeface(uiFontBold);

            String labelText = item.getLabel();
            if (labelText != null && labelText.length() > 20) {
                holder.binding.tvLabel.setText(labelText.substring(0, 20) + "...");
            } else {
                holder.binding.tvLabel.setText(labelText != null ? labelText : "");
            }

            // Bold styling for top suggestion
            holder.binding.tvLabel.setTypeface(codeFont, position == 0 ? Typeface.BOLD : Typeface.NORMAL);

            holder.binding.tvDetail.setText(item.getDetail() != null ? item.getDetail() : "");
            holder.binding.tvDetail.setTypeface(uiFont);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemSelected(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private int getBadgeColor(CompletionItem.Type type) {
            if (type == null) return colorTextSecondary;
            switch (type) {
                case TAG:
                case BUILTIN: return colorPrimary;
                case ATTRIBUTE:
                case CSS_PROPERTY: return colorSecondary;
                case VALUE:
                case CSS_VALUE:
                case FUNCTION: return colorSuccess;
                case KEYWORD: return colorWarning;
                case SNIPPET:
                case JSON_KEY: return colorJson;
                default: return colorTextSecondary;
            }
        }

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

        class ViewHolder extends RecyclerView.ViewHolder {
            final ItemAutocompleteSuggestionBinding binding;

            ViewHolder(ItemAutocompleteSuggestionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}