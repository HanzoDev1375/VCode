package com.cocode.vcode.ide.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cocode.vcode.ide.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Platform interface layout helper.
 * Manages soft keyboard flags, visual text notifications banners, display density unit metric conversions,
 * edge-to-edge window inset tracking alignments, and rounds components programmatically.
 */
public class UiUtils {

    private UiUtils() {
    }

    /**
     * Translates horizontal scale raw dp points values into exact machine execution pixel units.
     */
    public static int dpToPx(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()));
    }

    /**
     * Safely retracts keyboard layouts off active workspace inputs.
     */
    public static void hideKeyboard(Activity activity) {
        if (activity == null) return;
        View view = activity.getCurrentFocus();
        if (view == null) view = new View(activity);
        InputMethodManager imm = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Focuses input elements and requests window manager parameters to launch presentation keyboards.
     */
    public static void showKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Displays a text action message confirmation message box along base editor sheets layers.
     */
    public static void showSnackbar(View anchor, String message, int duration) {
        if (anchor == null || message == null) return;
        Snackbar.make(anchor, message, duration).show();
    }

    /**
     * Launches stylized notification banners colored explicitly to represent process failure logs feedback.
     */
    public static void showErrorSnackbar(View anchor, String message) {
        if (anchor == null || message == null) return;
        Snackbar snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(
                ContextCompat.getColor(anchor.getContext(), R.color.vcode_accent_error));
        snackbar.show();
    }

    /**
     * Configures component properties padding layers to account for status bar space limitations.
     */
    public static void applySystemBarInsets(View view) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /**
     * Calculates composite layout space properties for complex edge-to-edge screens,
     * distributing safe boundaries across drawers components and active container panels.
     */
    public static void applySystemBarInsets(View drawerLayout, View mainContent, View drawerContainer) {
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            Insets systemAndImeBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );

            mainContent.setPadding(
                    systemAndImeBars.left,
                    systemAndImeBars.top,
                    systemAndImeBars.right,
                    systemAndImeBars.bottom
            );
            drawerContainer.setPadding(
                    systemAndImeBars.left,
                    systemAndImeBars.top,
                    systemAndImeBars.right,
                    systemAndImeBars.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Applies precise outline corner clipping geometries along with solid background coloring rules.
     * @param view The target presentation layer item to morph.
     * @param radius The exact boundary corner rounding width specified in scale pixels.
     * @param color The absolute color resource hex definition value.
     */
    public static void setViewRounded(View view, float radius, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radius);
        shape.setColor(color);
        view.setBackground(shape);
        view.setClipToOutline(true);
    }
}