package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

public class DiagnosticBar extends LinearLayout {

    private ProgressBar progressLoading;
    private ImageView ivClean;
    private LinearLayout llErrors;
    private TextView tvErrorCount;
    private LinearLayout llWarnings;
    private TextView tvWarningCount;
    private LinearLayout llInfos;
    private TextView tvInfoCount;

    public DiagnosticBar(Context context) {
        super(context);
        init(context);
    }

    public DiagnosticBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DiagnosticBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.view_diagnostic_bar, this, true);

        progressLoading = findViewById(R.id.progress_loading);
        ivClean = findViewById(R.id.iv_clean);
        llErrors = findViewById(R.id.ll_errors);
        tvErrorCount = findViewById(R.id.tv_error_count);
        llWarnings = findViewById(R.id.ll_warnings);
        tvWarningCount = findViewById(R.id.tv_warning_count);
        llInfos = findViewById(R.id.ll_infos);
        tvInfoCount = findViewById(R.id.tv_info_count);

        setTypefaces();

        // Default state
        setLoading();
    }

    private void setTypefaces() {
        FontManager fm = FontManager.getInstance();
        Typeface font = fm.getUiMedium(getContext());
        tvErrorCount.setTypeface(font);
        tvWarningCount.setTypeface(font);
        tvInfoCount.setTypeface(font);
    }

    public void setLoading() {
        progressLoading.setVisibility(View.VISIBLE);
        ivClean.setVisibility(View.GONE);
        llErrors.setVisibility(View.GONE);
        llWarnings.setVisibility(View.GONE);
        llInfos.setVisibility(View.GONE);
        animateAlpha(1f);
    }

    public void update(int errors, int warnings, int infos) {
        progressLoading.setVisibility(View.GONE);

        if (errors == 0 && warnings == 0 && infos == 0) {
            ivClean.setVisibility(View.VISIBLE);
            llErrors.setVisibility(View.GONE);
            llWarnings.setVisibility(View.GONE);
            llInfos.setVisibility(View.GONE);
        } else {
            ivClean.setVisibility(View.GONE);

            if (errors > 0) {
                llErrors.setVisibility(View.VISIBLE);
                tvErrorCount.setText(String.valueOf(errors));
            } else {
                llErrors.setVisibility(View.GONE);
            }

            if (warnings > 0) {
                llWarnings.setVisibility(View.VISIBLE);
                tvWarningCount.setText(String.valueOf(warnings));
                // Add margin if errors is also visible
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) llWarnings.getLayoutParams();
                params.setMarginStart(errors > 0 ? UiUtils.dpToPx(getContext(), 8) : 0);
                llWarnings.setLayoutParams(params);
            } else {
                llWarnings.setVisibility(View.GONE);
            }

            if (infos > 0) {
                llInfos.setVisibility(View.VISIBLE);
                tvInfoCount.setText(String.valueOf(infos));
                // Add margin if errors or warnings are visible
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) llInfos.getLayoutParams();
                params.setMarginStart((errors > 0 || warnings > 0) ? UiUtils.dpToPx(getContext(), 8) : 0);
                llInfos.setLayoutParams(params);
            } else {
                llInfos.setVisibility(View.GONE);
            }
        }
        animateAlpha(1f);
    }

    private void animateAlpha(float targetAlpha) {
        if (getAlpha() != targetAlpha) {
            animate().alpha(targetAlpha).setDuration(150).start();
        }
    }
}
