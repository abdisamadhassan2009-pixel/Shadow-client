package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Ghost Client: Custom Crosshair. Independent implementation that follows Flarial's
 * default crosshair style (thanks Flarial, discord.gg/flarial). Contains no Flarial code.
 *
 * Draws a crosshair in the exact centre of the screen using Flarial's default style
 * (#FAFAFA at 55% opacity, outlined so it stays visible on bright terrain). Touches pass
 * straight through to the game. Size and opacity come from the mod's settings.
 * Drawn once and only redrawn when settings change, so it costs nothing per frame.
 */
public class ShadowCrosshairOverlay implements ShadowHudElement {
    private static final int FLARIAL_DEFAULT_COLOR = 0xFAFAFA;
    private static final float FLARIAL_DEFAULT_ALPHA = 0.55f;

    private final Activity activity;
    private final String modId;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CrosshairView view;
    private WindowManager.LayoutParams wmParams;
    private boolean isShowing;
    private Runnable pendingShow;

    public ShadowCrosshairOverlay(Activity activity, String modId) {
        this.activity = activity;
        this.modId = modId;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public void show(int startX, int startY) {
        if (isShowing) return;
        if (pendingShow != null) handler.removeCallbacks(pendingShow);
        pendingShow = this::showInternal;
        handler.postDelayed(pendingShow, 500);
    }

    private void showInternal() {
        pendingShow = null;
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;
        view = new CrosshairView(activity);
        applyConfigurationChangesToView();
        int size = view.sizePx();
        try {
            wmParams = new WindowManager.LayoutParams(size, size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            wmParams.gravity = Gravity.CENTER;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();
            view.setVisibility(View.GONE);
            windowManager.addView(view, wmParams);
            isShowing = true;
        } catch (Exception e) {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
            view.setClickable(false);
            view.setFocusable(false);
            view.setVisibility(View.GONE);
            root.addView(view, params);
            wmParams = null;
            isShowing = true;
        }
        InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
        if (manager != null) manager.refreshRuntimeVisibility();
    }

    @Override
    public void hide() {
        if (pendingShow != null) {
            handler.removeCallbacks(pendingShow);
            pendingShow = null;
        }
        if (!isShowing) return;
        isShowing = false;
        try {
            if (wmParams != null) {
                windowManager.removeView(view);
            } else {
                ViewGroup root = activity.findViewById(android.R.id.content);
                if (root != null) root.removeView(view);
            }
        } catch (Exception ignored) {
        }
        view = null;
    }

    @Override
    public void setVisibility(int visibility) {
        if (view != null && view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    @Override
    public void setHudEditorMode(boolean active) {
        // The crosshair is always centred, so the HUD editor has nothing to move.
    }

    @Override
    public void applyConfigurationChanges() {
        if (!isShowing || view == null) return;
        applyConfigurationChangesToView();
        int size = view.sizePx();
        if (wmParams != null) {
            wmParams.width = size;
            wmParams.height = size;
            try {
                windowManager.updateViewLayout(view, wmParams);
            } catch (Exception ignored) {
            }
        } else if (view.getLayoutParams() != null) {
            view.getLayoutParams().width = size;
            view.getLayoutParams().height = size;
            view.requestLayout();
        }
    }

    private void applyConfigurationChangesToView() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        int sizeSetting = manager.getOverlayButtonSize(modId);   // 20..100, default 56
        int opacity = manager.getOverlayOpacity(modId);          // 0..100, default 100
        view.configure(sizeSetting / 56f, FLARIAL_DEFAULT_ALPHA * (opacity / 100f));
    }

    @Override
    public void updatePosition(int x, int y) {
        // Always centred.
    }

    private static final class CrosshairView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private float scale = 1f;

        CrosshairView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            fill.setStyle(Paint.Style.FILL);
            outline.setStyle(Paint.Style.FILL);
        }

        void configure(float scale, float alpha) {
            this.scale = Math.max(0.4f, scale);
            int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
            fill.setColor((a << 24) | FLARIAL_DEFAULT_COLOR);
            outline.setColor(Math.round(a * 0.6f) << 24);
            invalidate();
        }

        int sizePx() {
            return Math.round(24 * density * scale) + 4;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float arm = 8 * density * scale;       // arm length
            float gap = 3 * density * scale;       // gap from centre
            float t = Math.max(1.5f, 1.5f * density * scale) / 2f; // half thickness
            float o = Math.max(1f, density * 0.75f);               // outline width
            drawCross(canvas, cx, cy, arm, gap, t + o, outline);
            canvas.drawRect(cx - t - o, cy - t - o, cx + t + o, cy + t + o, outline);
            drawCross(canvas, cx, cy, arm, gap, t, fill);
            canvas.drawRect(cx - t, cy - t, cx + t, cy + t, fill);
        }

        private static void drawCross(Canvas c, float cx, float cy, float arm, float gap, float t, Paint p) {
            c.drawRect(cx - gap - arm, cy - t, cx - gap, cy + t, p);
            c.drawRect(cx + gap, cy - t, cx + gap + arm, cy + t, p);
            c.drawRect(cx - t, cy - gap - arm, cx + t, cy - gap, p);
            c.drawRect(cx - t, cy + gap, cx + t, cy + gap + arm, p);
        }
    }
}
