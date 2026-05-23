package com.cocode.vcode.ide.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ViewJsonStatusBarBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Contextual footer panel component signaling data integrity metrics for JSON documents.
 * Cycles background shapes and vector drawable symbols to represent real-time parsing states,
 * error diagnostics, or successful schema alignment confirmations.
 */
public class JsonStatusBar extends LinearLayout {

    private ViewJsonStatusBarBinding binding;

    public JsonStatusBar(Context context) {
        super(context);
        init(context);
    }

    public JsonStatusBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public JsonStatusBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        binding = ViewJsonStatusBarBinding.inflate(LayoutInflater.from(context), this);
        binding.tvJsonStatusMsg.setTypeface(FontManager.getInstance().getUiMedium(context));
    }

    /**
     * Shifts styling vectors to signify completely error-free source formatting validation profiles.
     */
    public void showValid() {
        binding.ivJsonStatusIcon.setVisibility(VISIBLE);
        binding.progressJsonValidating.setVisibility(GONE);

        binding.ivJsonStatusIcon.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_circle_check));
        binding.ivJsonStatusIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.vcode_accent_success));

        binding.tvJsonStatusMsg.setText(getContext().getString(R.string.vcode_valid_json));
        binding.tvJsonStatusMsg.setTextColor(ContextCompat.getColor(getContext(), R.color.vcode_text_primary));

        binding.jsonStatusContainer.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.vcode_bg_json_status_valid));
    }

    /**
     * Displays parsing exceptions alerts complete with structural feedback diagnostics description details.
     */
    public void showInvalid(String errorMessage) {
        binding.ivJsonStatusIcon.setVisibility(VISIBLE);
        binding.progressJsonValidating.setVisibility(GONE);

        binding.ivJsonStatusIcon.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_triangle_exclamation));
        binding.ivJsonStatusIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.vcode_accent_error));

        binding.tvJsonStatusMsg.setText(errorMessage != null ? errorMessage : getContext().getString(R.string.vcode_invalid_json));
        binding.tvJsonStatusMsg.setTextColor(ContextCompat.getColor(getContext(), R.color.vcode_accent_error));

        binding.jsonStatusContainer.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.vcode_bg_json_status_invalid));
    }

    /**
     * Activates infinite loading progress sweeps indicating active calculation checking runs.
     */
    public void showValidating() {
        binding.ivJsonStatusIcon.setVisibility(GONE);
        binding.progressJsonValidating.setVisibility(VISIBLE);

        binding.tvJsonStatusMsg.setText(getContext().getString(R.string.vcode_validating_json));
        binding.tvJsonStatusMsg.setTextColor(ContextCompat.getColor(getContext(), R.color.vcode_text_secondary));

        binding.jsonStatusContainer.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.vcode_bg_surface));
    }
}