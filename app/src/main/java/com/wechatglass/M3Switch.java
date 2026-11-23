package com.wechatglass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Material 3 Expressive style switch, fully custom drawn. */
public class M3Switch extends View {

    private int onTrack;
    private int offTrack;
    private int offStroke;
    private int onThumb;
    private int offThumb;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean checked;
    private float progress;
    private float stretch;
    private ValueAnimator anim;

    public M3Switch(Context c) {
        super(c);
        com.wechatglass.m3.M3ColorScheme scheme =
                com.wechatglass.m3.M3Theme.colors(c);
        onTrack = scheme.primary;
        offTrack = scheme.surfaceContainerHighest;
        offStroke = scheme.outline;
        onThumb = scheme.onPrimary;
        offThumb = scheme.outline;
        setClickable(true);
        setFocusable(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
    }

    public boolean isChecked() { return checked; }

    public void setCheckedNoAnim(boolean v) {
        checked = v;
        progress = v ? 1f : 0f;
        invalidate();
    }

    public void setChecked(boolean v) {
        if (checked == v) return;
        checked = v;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(progress, v ? 1f : 0f);
        anim.setDuration(320);
        anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            stretch = (float) Math.sin(progress * Math.PI) * 0.35f;
            invalidate();
        });
        anim.start();
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(dp(56), dp(36));
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float trackH = h * 0.86f;
        rect.set(0, (h - trackH) / 2f, w, (h + trackH) / 2f);
        float r = trackH / 2f;

        track.setColor(blend(offTrack, onTrack, progress));
        track.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(rect, r, r, track);

        if (progress < 0.98f) {
            stroke.setColor(offStroke);
            canvas.drawRoundRect(rect, r, r, stroke);
        }

        float thumbR = trackH * 0.42f;
        float cx = r + (w - 2 * r) * progress;
        float cy = h / 2f;
        thumb.setColor(blend(offThumb, onThumb, progress));
        canvas.drawCircle(cx, cy, thumbR, thumb);

        // Expressive squish: a second, wider ellipse behind the thumb while moving.
        if (stretch > 0.01f) {
            thumb.setColor(blend(offThumb, onThumb, progress));
            thumb.setAlpha(90);
            canvas.drawOval(cx - thumbR * (1 + stretch), cy - thumbR,
                    cx + thumbR * (1 + stretch), cy + thumbR, thumb);
            thumb.setAlpha(255);
        }
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
