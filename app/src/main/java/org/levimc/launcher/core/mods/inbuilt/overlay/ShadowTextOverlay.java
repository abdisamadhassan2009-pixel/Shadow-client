package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Shadow Client: a draggable HUD text widget (keystrokes, clock, battery, session timer).
 * Based on the CPS display overlay, generalised over the mod id and text source.
 * Performance: refreshes on its own interval and only calls setText when the text changed.
 */
public class ShadowTextOverlay {
    public interface TextProvider {
        CharSequence get();
    }

    private static final float DRAG_THRESHOLD = 10f;

    private final Activity activity;
    private final String modId;
    private final int updateIntervalMs;
    private final TextProvider provider;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View overlayView;
    private TextView statsText;
    private Drawable normalBackground;
    private WindowManager.LayoutParams wmParams;
    private boolean isShowing = false;
    private Runnable pendingShowRunnable;
    private CharSequence lastText;

    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    private boolean isHudEditorMode = false;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShowing) return;
            updateDisplay();
            handler.postDelayed(this, updateIntervalMs);
        }
    };

    public ShadowTextOverlay(Activity activity, String modId, int updateIntervalMs, TextProvider provider) {
        this.activity = activity;
        this.modId = modId;
        this.updateIntervalMs = updateIntervalMs;
        this.provider = provider;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
    }

    public String getModId() {
        return modId;
    }

    public void show(int startX, int startY) {
        if (isShowing) return;
        if (pendingShowRunnable != null) handler.removeCallbacks(pendingShowRunnable);
        pendingShowRunnable = () -> showInternal(startX, startY);
        handler.postDelayed(pendingShowRunnable, 500);
    }

    private void inflate() {
        overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_stats_display, null);
        statsText = overlayView.findViewById(R.id.stats_text);
        normalBackground = overlayView.getBackground();
        lastText = null;
        updateDisplay();
    }

    private void showInternal(int startX, int startY) {
        pendingShowRunnable = null;
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            inflate();
            wmParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            wmParams.gravity = Gravity.TOP | Gravity.START;
            wmParams.x = startX;
            wmParams.y = startY;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();

            overlayView.setOnTouchListener(this::handleTouch);
            overlayView.setVisibility(View.GONE);
            windowManager.addView(overlayView, wmParams);
            isShowing = true;
            afterShow();
            updatePosition(wmParams.x, wmParams.y);
        } catch (Exception e) {
            showFallback(startX, startY);
        }
    }

    private void showFallback(int startX, int startY) {
        if (isShowing) return;
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) return;
        inflate();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.START;
        OverlayBounds.Position position = OverlayBounds.clampPosition(activity, overlayView, startX, startY);
        params.leftMargin = position.x;
        params.topMargin = position.y;
        overlayView.setOnTouchListener(this::handleTouch);
        overlayView.setVisibility(View.GONE);
        rootView.addView(overlayView, params);
        isShowing = true;
        wmParams = null;
        afterShow();
    }

    private void afterShow() {
        handler.post(updateRunnable);
        applyOpacity();
        applySize();
        setHudEditorMode(isHudEditorMode);
        InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
        if (manager != null) manager.refreshRuntimeVisibility();
    }

    private void updateDisplay() {
        if (statsText == null) return;
        CharSequence text = provider.get();
        if (lastText != null && TextUtils.equals(lastText, text) && !(text instanceof android.text.Spanned)) return;
        if (lastText != null && text instanceof android.text.Spanned && lastText.toString().equals(text.toString())
                && sameSpans((android.text.Spanned) lastText, (android.text.Spanned) text)) return;
        lastText = text;
        statsText.setText(text);
    }

    private static boolean sameSpans(android.text.Spanned a, android.text.Spanned b) {
        Object[] sa = a.getSpans(0, a.length(), android.text.style.ForegroundColorSpan.class);
        Object[] sb = b.getSpans(0, b.length(), android.text.style.ForegroundColorSpan.class);
        if (sa.length != sb.length) return false;
        for (int i = 0; i < sa.length; i++) {
            if (((android.text.style.ForegroundColorSpan) sa[i]).getForegroundColor()
                    != ((android.text.style.ForegroundColorSpan) sb[i]).getForegroundColor()) return false;
            if (a.getSpanStart(sa[i]) != b.getSpanStart(sb[i])) return false;
        }
        return true;
    }

    private void applyOpacity() {
        if (overlayView == null || normalBackground == null) return;
        int opacity = InbuiltModManager.getInstance(activity).getOverlayOpacity(modId);
        normalBackground.mutate().setAlpha((int) (opacity * 2.55f));
    }

    private void applySize() {
        if (statsText == null) return;
        int sizeDp = InbuiltModManager.getInstance(activity).getOverlayButtonSize(modId);
        float scale = sizeDp / 56f;
        statsText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12 * scale);
        float density = activity.getResources().getDisplayMetrics().density;
        int hPad = (int) (8 * scale * density);
        int vPad = (int) (4 * scale * density);
        statsText.setPadding(hPad, vPad, hPad, vPad);
        clampCurrentPosition(true);
    }

    public void setHudEditorMode(boolean active) {
        isHudEditorMode = active;
        if (overlayView == null) return;
        if (active) {
            overlayView.setBackgroundColor(0x446E8BFF);
        } else {
            overlayView.setBackground(normalBackground);
            applyOpacity();
        }
    }

    public void updatePosition(int x, int y) {
        if (overlayView == null || !isShowing) return;
        OverlayBounds.Position position = OverlayBounds.clampPosition(activity, overlayView, x, y);
        if (wmParams != null && windowManager != null) {
            wmParams.x = position.x;
            wmParams.y = position.y;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            if (params == null) return;
            params.leftMargin = position.x;
            params.topMargin = position.y;
            overlayView.setLayoutParams(params);
        }
    }

    private int currentX() {
        if (wmParams != null) return wmParams.x;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
        return p == null ? 0 : p.leftMargin;
    }

    private int currentY() {
        if (wmParams != null) return wmParams.y;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
        return p == null ? 0 : p.topMargin;
    }

    private boolean handleTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isHudEditorMode) {
                    InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
                    if (manager != null) manager.selectHudEditorDisplay(modId);
                }
                initialX = currentX();
                initialY = currentY();
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                isDragging = false;
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(isHudEditorMode);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - initialTouchX;
                float dy = event.getRawY() - initialTouchY;
                if (isHudEditorMode && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragging = true;
                }
                if (isDragging && isHudEditorMode) {
                    updatePosition((int) (initialX + dx), (int) (initialY + dy));
                }
                return isHudEditorMode || !isDragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging && isHudEditorMode) {
                    InbuiltModManager.getInstance(activity).setOverlayPosition(modId, currentX(), currentY());
                }
                isDragging = false;
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return false;
    }

    public void hide() {
        if (pendingShowRunnable != null) {
            handler.removeCallbacks(pendingShowRunnable);
            pendingShowRunnable = null;
        }
        if (!isShowing) return;
        isShowing = false;
        handler.removeCallbacks(updateRunnable);
        try {
            if (wmParams != null && windowManager != null) {
                windowManager.removeView(overlayView);
            } else {
                ViewGroup rootView = activity.findViewById(android.R.id.content);
                if (rootView != null) rootView.removeView(overlayView);
            }
        } catch (Exception ignored) {
        }
        overlayView = null;
        statsText = null;
        lastText = null;
    }

    public void applyConfigurationChanges() {
        if (!isShowing || overlayView == null) return;
        applyOpacity();
        applySize();
    }

    public void setVisibility(int visibility) {
        if (overlayView != null && overlayView.getVisibility() != visibility) {
            overlayView.setVisibility(visibility);
        }
    }

    private void clampCurrentPosition(boolean save) {
        if (overlayView == null || !isShowing) return;
        int x = currentX(), y = currentY();
        OverlayBounds.Position position = OverlayBounds.clampPosition(activity, overlayView, x, y);
        if (position.x != x || position.y != y) {
            updatePosition(position.x, position.y);
            if (save) InbuiltModManager.getInstance(activity).setOverlayPosition(modId, position.x, position.y);
        }
    }
}
