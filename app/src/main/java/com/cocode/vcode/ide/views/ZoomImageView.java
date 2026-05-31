package com.cocode.vcode.ide.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Custom ImageView implementation supporting high-performance pinch-to-zoom,
 * panning, and double-tap interactions for graphic resources.
 * Manages viewport boundary alignment offsets via matrix transformations to keep
 * images bound securely inside display frames during manipulation sequences.
 */
public class ZoomImageView extends AppCompatImageView {

    // --- Interaction State Vector Identifiers ---
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    // Tracking points for gesture tracking offsets calculations
    private final PointF last = new PointF();
    private final PointF start = new PointF();
    // Scale constraints definition parameters
    private final float maxScale = 5f;
    private Matrix matrix;
    private int mode = NONE;
    private float[] m;

    // Viewport layout sizing parameters
    private int viewWidth, viewHeight;
    private float saveScale = 1f;
    private float origWidth, origHeight;

    // Specialized platform inputs gesture intercept coordinators
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    public ZoomImageView(Context context) {
        super(context);
        sharedConstructing(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    /**
     * Initializes shared interaction detectors, default matrix allocations,
     * and maps touch event handling pathways.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void sharedConstructing(Context context) {
        super.setClickable(true);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new GestureListener());
        matrix = new Matrix();
        m = new float[9];
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);

        setOnTouchListener((v, event) -> {
            // Forward raw interaction events to appropriate gesture mapping systems
            mScaleDetector.onTouchEvent(event);
            mGestureDetector.onTouchEvent(event);

            PointF curr = new PointF(event.getX(), event.getY());

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    last.set(curr);
                    start.set(last);
                    mode = DRAG;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG) {
                        float deltaX = curr.x - last.x;
                        float deltaY = curr.y - last.y;

                        // Calculate valid drag movements to prevent pulling images away from viewport limits
                        float fixTransX = getFixDragTrans(deltaX, viewWidth, origWidth * saveScale);
                        float fixTransY = getFixDragTrans(deltaY, viewHeight, origHeight * saveScale);

                        matrix.postTranslate(fixTransX, fixTransY);
                        fixTrans(); // Apply edge padding boundary locks
                        last.set(curr.x, curr.y);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE;
                    break;
            }

            setImageMatrix(matrix);
            invalidate(); // Request redraw cycle to render updated viewport dimensions
            return true;
        });
    }

    // --- Reset logic to clear zoom state when a new image is loaded ---

    @Override
    public void setImageURI(Uri uri) {
        super.setImageURI(uri);
        resetZoomState();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        resetZoomState();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        resetZoomState();
    }

    /**
     * Reverts scale properties back to standard 1f dimensions and clears tracking
     * matrix modifications to properly accommodate incoming source media assets.
     */
    private void resetZoomState() {
        saveScale = 1f;
        if (matrix != null) {
            matrix.reset();
            setImageMatrix(matrix);
        }
        // Force onMeasure pass to recompute centering math values for the new asset footprint
        requestLayout();
    }

    // ----------------------------------------------------------------------

    /**
     * Resolves layout coordinates from tracking matrix values to keep graphic components
     * bound firmly inside valid coordinate zones, correcting translation alignment drift.
     */
    private void fixTrans() {
        matrix.getValues(m);
        float transX = m[Matrix.MTRANS_X];
        float transY = m[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, viewWidth, origWidth * saveScale);
        float fixTransY = getFixTrans(transY, viewHeight, origHeight * saveScale);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    /**
     * Calculates structural compensation offsets required to lock image borders securely
     * inside the canvas borders when viewport scrolling limits are exceeded.
     */
    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        // Define translation ranges depending on whether the graphic fills the view width or height completely
        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans) return -trans + minTrans;
        if (trans > maxTrans) return -trans + maxTrans;
        return 0;
    }

    /**
     * Prevents drag mechanics execution if an asset sits entirely within viewport margins.
     */
    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) return 0;
        return delta;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);

        // Core Centering Logic: Formats source dimensions uniformly matching target canvas constraints on launch
        if (saveScale == 1f && viewWidth > 0 && viewHeight > 0) {
            Drawable drawable = getDrawable();
            if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0)
                return;

            int bmWidth = drawable.getIntrinsicWidth();
            int bmHeight = drawable.getIntrinsicHeight();

            float scaleX = (float) viewWidth / (float) bmWidth;
            float scaleY = (float) viewHeight / (float) bmHeight;
            float scale = Math.min(scaleX, scaleY); // Select proportional dimension to preserve graphic aspect ratios

            matrix.setScale(scale, scale);

            // Deduce remaining blank spatial margins to center components inside layout fields
            float redundantYSpace = (float) viewHeight - (scale * (float) bmHeight);
            float redundantXSpace = (float) viewWidth - (scale * (float) bmWidth);

            redundantYSpace /= 2f;
            redundantXSpace /= 2f;

            matrix.postTranslate(redundantXSpace, redundantYSpace);

            // Establish primary dimension metrics representing active bounding sizes definitions
            origWidth = viewWidth - 2 * redundantXSpace;
            origHeight = viewHeight - 2 * redundantYSpace;
            setImageMatrix(matrix);
        }
        fixTrans();
    }

    /**
     * Double tap listener class handling quick toggle shortcuts.
     * Transitions between base scale parameters and upper limits values.
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float targetScale = (saveScale == 1f) ? maxScale : 1f;
            float scaleFactor = targetScale / saveScale;

            matrix.postScale(scaleFactor, scaleFactor, e.getX(), e.getY());
            saveScale = targetScale;
            fixTrans();
            setImageMatrix(matrix);
            invalidate();
            return true;
        }
    }

    /**
     * Pinch gesture tracking state manager.
     * Processes changes in multi-touch tracking vectors to update scale variables.
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= mScaleFactor;
            float minScale = 1f;

            // Restrict calculated scale configurations inside min vs max threshold intervals
            if (saveScale > maxScale) {
                saveScale = maxScale;
                mScaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }

            // Adjust translation anchoring points based on focal selection indicators coordinates
            if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
                matrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f);
            } else {
                matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
            }

            fixTrans(); // Maintain viewport boundaries clamping updates
            return true;
        }
    }
}