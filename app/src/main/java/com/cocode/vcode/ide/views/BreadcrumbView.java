package com.cocode.vcode.ide.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Horizontal navigation path trail representing folder nesting paths for an open file.
 * Automatically tokenizes slash delimiters into clean chevron nodes and controls
 * alignment updates to ensure trailing leaf text parameters remain visible.
 */
public class BreadcrumbView extends HorizontalScrollView {

    private LinearLayout container;

    public BreadcrumbView(Context context) {
        super(context);
        init();
    }

    public BreadcrumbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BreadcrumbView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        // Fallback layout baseline setting: use application default surfaces colors if missing from styles
        if (getBackground() == null) {
            setBackgroundColor(ContextCompat.getColor(getContext(), R.color.vcode_bg_surface));
        }

        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        int padPx = UiUtils.dpToPx(getContext(), 16);
        container.setPaddingRelative(padPx, 0, padPx, 0);
        container.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(container);
    }

    /**
     * Parses absolute path records, splitting text into structured graphical segment nodes.
     *
     * @param projectName  The user-facing active project workspace label name.
     * @param relativePath The path coordinates mapped between the project root and the open document.
     */
    public void setPath(String projectName, String relativePath) {
        container.removeAllViews();

        // Project root segment remains anchored as the leading baseline element path identifier
        addSegment(projectName != null ? projectName : "Project", true);

        if (relativePath != null && !relativePath.isEmpty()) {
            String normalized = relativePath.replace('\\', '/');
            String[] parts = normalized.split("/");

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) continue;

                addDivider();
                boolean isLast = (i == parts.length - 1);
                addSegment(parts[i], isLast);
            }
        }

        // Auto-scroll layout tracking logic: automatically shift frames rightward to present leaf file changes
        post(() -> fullScroll(FOCUS_RIGHT));
    }

    private void addSegment(String text, boolean isActive) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setMaxLines(1);
        tv.setTextSize(12);
        tv.setTypeface(FontManager.getInstance().getUiFont(getContext()));
        tv.setTextColor(ContextCompat.getColor(getContext(),
                isActive ? R.color.vcode_text_primary : R.color.vcode_text_secondary));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(params);
        container.addView(tv);
    }

    private void addDivider() {
        ImageView iv = new ImageView(getContext());
        iv.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_chevron_right));
        iv.setColorFilter(ContextCompat.getColor(getContext(), R.color.vcode_text_hint));

        int size = UiUtils.dpToPx(getContext(), 14);
        int margin = UiUtils.dpToPx(getContext(), 4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMarginStart(margin);
        params.setMarginEnd(margin);
        params.gravity = Gravity.CENTER_VERTICAL;
        iv.setLayoutParams(params);

        container.addView(iv);
    }
}