package org.levimc.launcher.core.mods.inbuilt.shadow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.levimc.launcher.R;

/**
 * Ghost Client Armor HUD: one row per worn armor piece (plus held items) with an icon tinted by
 * material and the durability left, coloured green to red. Data comes from {@link GhostPvpNative}.
 */
final class ArmorHud {
    private static final int GREEN = 0xFF3DDC97, YELLOW = 0xFFF2C94C, ORANGE = 0xFFFF9F43, RED = 0xFFFF5A5A;
    private static final int DIM = 0xFF8C93A1, WHITE = 0xFFFFFFFF;

    private static final int[] ICONS = {
            R.drawable.ic_ghost_armor_helmet, R.drawable.ic_ghost_armor_chest, R.drawable.ic_ghost_armor_legs,
            R.drawable.ic_ghost_armor_boots, R.drawable.ic_ghost_hand, R.drawable.ic_ghost_offhand};
    // A different placeholder per row so a change of icon also changes the text (forces a redraw).
    private static final char[] MARKS = {'H', 'C', 'L', 'B', 'M', 'O'};

    private ArmorHud() {
    }

    static CharSequence text(Context context) {
        GhostPvpNative.Item[] items = GhostPvpNative.equipment();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (items == null) {
            icon(context, sb, 1, DIM);
            append(sb, GhostPvpNative.isReady() ? " Armor HUD" : " Armor HUD (needs MC 1.26.60)", DIM);
            return sb;
        }
        boolean any = false;
        for (int i = 0; i < items.length; i++) {
            GhostPvpNative.Item item = items[i];
            if (!item.present) continue;
            if (i >= GhostPvpNative.MAIN_HAND && item.max <= 0 && item.count <= 1 && !isTotem(item.id)) continue;
            if (any) sb.append('\n');
            any = true;
            icon(context, sb, i, materialColor(item.id));
            sb.append(' ');
            if (item.max > 0) {
                append(sb, String.valueOf(item.remaining()), durabilityColor(item.fraction()));
            } else {
                append(sb, "x" + item.count, isTotem(item.id) ? YELLOW : WHITE);
            }
        }
        if (!any) {
            icon(context, sb, 1, DIM);
            append(sb, " No armor", DIM);
        }
        return sb;
    }

    private static boolean isTotem(String id) {
        return id != null && id.contains("totem");
    }

    static int durabilityColor(float f) {
        if (f > 0.60f) return GREEN;
        if (f > 0.30f) return YELLOW;
        if (f > 0.15f) return ORANGE;
        return RED;
    }

    static int materialColor(String id) {
        if (id == null) return WHITE;
        if (id.contains("netherite")) return 0xFFA493A0;
        if (id.contains("diamond")) return 0xFF4FE3E0;
        if (id.contains("golden") || id.contains("gold") || id.contains("totem")) return 0xFFF2C94C;
        if (id.contains("chainmail")) return 0xFF9AA0A8;
        if (id.contains("iron")) return 0xFFE2E2E2;
        if (id.contains("leather")) return 0xFFB07A4A;
        if (id.contains("turtle")) return 0xFF4CAF50;
        if (id.contains("elytra")) return 0xFFB3A6E6;
        if (id.contains("copper")) return 0xFFE08A5C;
        if (id.contains("wooden") || id.contains("bow") || id.contains("shield")) return 0xFFC79A63;
        if (id.contains("stone")) return 0xFFB0B0B0;
        return WHITE;
    }

    private static void append(SpannableStringBuilder sb, String s, int color) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void icon(Context context, SpannableStringBuilder sb, int index, int tint) {
        int start = sb.length();
        sb.append(MARKS[index]);
        Drawable d = drawable(context, index);
        if (d != null) sb.setSpan(new IconSpan(d.mutate(), tint), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Colour span carries the tint so the overlay notices material changes.
        sb.setSpan(new ForegroundColorSpan(tint), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static final Drawable.ConstantState[] CACHE = new Drawable.ConstantState[ICONS.length];

    private static Drawable drawable(Context context, int index) {
        Drawable.ConstantState state = CACHE[index];
        if (state == null) {
            Drawable d = ContextCompat.getDrawable(context, ICONS[index]);
            if (d == null) return null;
            state = d.getConstantState();
            if (state == null) return d;
            CACHE[index] = state;
        }
        return state.newDrawable(context.getResources());
    }

    /** Draws an icon sized to the current text size, so it scales with the widget's size setting. */
    private static final class IconSpan extends ReplacementSpan {
        private final Drawable drawable;

        IconSpan(Drawable drawable, int tint) {
            this.drawable = drawable;
            drawable.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            int size = Math.round(paint.getTextSize() * 1.15f);
            if (fm != null) {
                Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
                fm.ascent = pfm.ascent;
                fm.descent = pfm.descent;
                fm.top = pfm.top;
                fm.bottom = pfm.bottom;
            }
            return size;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, @NonNull Paint paint) {
            int size = Math.round(paint.getTextSize() * 1.15f);
            int cy = (top + bottom) / 2;
            drawable.setBounds((int) x, cy - size / 2, (int) x + size, cy - size / 2 + size);
            drawable.draw(canvas);
        }
    }
}
