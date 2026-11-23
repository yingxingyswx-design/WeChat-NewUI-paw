package com.wechatglass;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wechatglass.liquid.Spring;

/**
 * The home category switch: 好友 / 群聊 / 微信 as one liquid-glass capsule.
 *
 * <p>Animation follows the reference LiquidGlass module: springs integrated per
 * frame by a {@link Choreographer} callback instead of duration interpolators.
 * A position spring (tab units) carries the green pill, a press spring grows the
 * capsule while held, and two scale springs give it the same soft overshoot the
 * reference bar has.
 */
final class HomeCategoryBar extends FrameLayout {

    static final int HEIGHT_DP = 40;
    /** WeChat green, the same accent the bottom tab bar uses. */
    private static final int WECHAT_GREEN = 0xFF07C160;
    private static final long PRESS_DELAY_MS = 360L;
    private static final float PRESSED_SCALE = 1.14f;

    interface OnSelect {
        void onSelect(int mode);
    }

    private final TextView[] tabs = new TextView[3];
    private final LinearLayout row;
    private final View indicator;
    private final com.wechatglass.m3.M3ColorScheme scheme;
    private OnSelect onSelect;
    private int mode;
    private boolean night;
    private boolean longPressed;
    private boolean dragging;
    private float downRawX;
    private Runnable pressUp;

    /** Position in tab units (0..2); the reference bar's value spring. */
    private final Spring posSpring = new Spring(0.9f, 2600f, 0.004f, 0f);
    private final Spring pressSpring = new Spring(1f, 2600f, 0.004f, 0f);
    private final Spring scaleXSpring = new Spring(0.8f, 900f, 0.004f, 1f);
    private final Spring scaleYSpring = new Spring(0.8f, 900f, 0.004f, 1f);
    private boolean frameScheduled;
    private long lastFrameNanos;

    HomeCategoryBar(Context context, ViewGroup backdrop, boolean night,
                    String[] labels, int mode) {
        super(context);
        this.night = night;
        this.mode = mode;
        this.scheme = com.wechatglass.m3.M3Theme.colors(context);
        setClipToPadding(false);
        setClipChildren(false);

        View glass = backdrop == null ? null
                : com.wechatglass.liquid.LiquidPanelFactory.create(
                context, backdrop, context.getResources().getDisplayMetrics().density, night);
        if (glass != null) {
            glass.setClickable(false);
            glass.setFocusable(false);
            addView(glass, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            GradientDrawable fallback = new GradientDrawable();
            fallback.setColor(night ? 0xCC2C2C2C : 0xCCFFFFFF);
            fallback.setCornerRadius(Ui.dp(context, HEIGHT_DP / 2f));
            fallback.setStroke(Ui.dp(context, 1f), night ? 0x993C3C3C : 0x99D6D6D6);
            setBackground(fallback);
        }

        indicator = new View(context);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(scheme.primary);
        pill.setCornerRadius(Ui.dp(context, (HEIGHT_DP - 6) / 2f));
        indicator.setBackground(pill);
        addView(indicator, new LayoutParams(0, 0));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(context, 6f);
        row.setPadding(pad, Ui.dp(context, 3f), pad, Ui.dp(context, 3f));
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView tab = new TextView(context);
            tab.setText(labels[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tab.setPadding(Ui.dp(context, 12f), 0, Ui.dp(context, 12f), 0);
            tab.setMinWidth(Ui.dp(context, 48f));
            tab.setIncludeFontPadding(false);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyMode(index);
                }
            });
            installPressFeedback(tab);
            tabs[i] = tab;
            row.addView(tab, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        addView(row, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        installPressFeedback(this);
        posSpring.snapTo(mode);
        applyStyle();
    }

    void setOnSelect(OnSelect listener) {
        onSelect = listener;
    }

    int mode() {
        return mode;
    }

    void setNight(boolean isNight) {
        if (night == isNight) {
            return;
        }
        night = isNight;
        applyStyle();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!frameScheduled) {
            apply();
        }
    }

    private void applyMode(int index) {
        if (mode == index) {
            return;
        }
        mode = index;
        applyStyle();
        // The reference bar presses first, then lets the position spring carry
        // the pill across; the press is released once the spring settles.
        pressSpring.animateTo(1f);
        scaleXSpring.animateTo(PRESSED_SCALE);
        scaleYSpring.animateTo(PRESSED_SCALE);
        posSpring.animateTo(index);
        ensureFrame();
        if (onSelect != null) {
            onSelect.onSelect(index);
        }
    }

    private void applyStyle() {
        for (int i = 0; i < tabs.length; i++) {
            TextView tab = tabs[i];
            boolean selected = i == mode;
            tab.setTextColor(selected
                    ? scheme.onPrimary
                    : (night ? 0xFFE8E8E8 : 0xFF333333));
            tab.setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            float dt = lastFrameNanos == 0L
                    ? 0.016f : (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = frameTimeNanos;
            boolean running = false;
            running |= posSpring.update(dt);
            running |= pressSpring.update(dt);
            running |= scaleXSpring.update(dt);
            running |= scaleYSpring.update(dt);
            apply();
            if (!posSpring.isRunning() && pressSpring.target() > 0.5f
                    && !longPressed) {
                pressSpring.animateTo(0f);
                scaleXSpring.animateTo(1f);
                scaleYSpring.animateTo(1f);
                running = true;
            }
            if (running) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                frameScheduled = false;
                lastFrameNanos = 0L;
            }
        }
    };

    private void ensureFrame() {
        if (frameScheduled) {
            return;
        }
        frameScheduled = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void apply() {
        int count = tabs.length;
        if (count == 0 || row.getWidth() <= 0) {
            return;
        }
        float position = Math.max(0f, Math.min(posSpring.value(), count - 1f));
        int first = Math.min((int) Math.floor(position), count - 1);
        int second = Math.min(first + 1, count - 1);
        float fraction = Math.max(0f, Math.min(position - first, 1f));
        TextView a = tabs[first];
        TextView b = tabs[second];
        int left = Math.round(a.getLeft() + (b.getLeft() - a.getLeft()) * fraction)
                + row.getLeft();
        int width = Math.round(a.getWidth() + (b.getWidth() - a.getWidth()) * fraction);
        int top = row.getTop() + a.getTop();
        int height = a.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        indicator.layout(left, top, left + width, top + height);
        indicator.invalidate();
        setScaleX(scaleXSpring.value());
        setScaleY(scaleYSpring.value());
        invalidate();
    }

    /**
     * LiquidGlass-style interaction: hold to lift the capsule, then drag the
     * pill across the tabs; release snaps to the nearest tab. Plain taps are
     * left to the TextViews' own click listeners.
     */
    private void installPressFeedback(View target) {
        target.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        schedulePress();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging) {
                            return false;
                        }
                        float value = valueForX(event.getRawX());
                        posSpring.snapTo(clamp(value, 0f, tabs.length - 1f));
                        ensureFrame();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelPressTimer();
                        if (dragging) {
                            finishDrag();
                            return true;
                        }
                        return false;
                    default:
                        return false;
                }
            }
        });
    }

    private float valueForX(float rawX) {
        int count = tabs.length;
        float[] centers = new float[count];
        int[] location = new int[2];
        for (int i = 0; i < count; i++) {
            tabs[i].getLocationOnScreen(location);
            centers[i] = location[0] + tabs[i].getWidth() / 2f;
        }
        if (rawX <= centers[0]) {
            return 0f;
        }
        if (rawX >= centers[count - 1]) {
            return count - 1f;
        }
        for (int i = 0; i < count - 1; i++) {
            if (rawX <= centers[i + 1]) {
                float span = centers[i + 1] - centers[i];
                return span <= 0f ? i : i + (rawX - centers[i]) / span;
            }
        }
        return count - 1f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private void schedulePress() {
        if (pressUp != null) {
            removeCallbacks(pressUp);
        }
        pressUp = new Runnable() {
            @Override
            public void run() {
                if (longPressed) {
                    return;
                }
                longPressed = true;
                dragging = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                pressSpring.animateTo(1f);
                scaleXSpring.animateTo(PRESSED_SCALE);
                scaleYSpring.animateTo(PRESSED_SCALE);
                ensureFrame();
                android.util.Log.i("WeChatGlass", "HL bar drag begin");
            }
        };
        postDelayed(pressUp, PRESS_DELAY_MS);
    }

    private void finishDrag() {
        int index = Math.round(posSpring.value());
        index = Math.max(0, Math.min(index, tabs.length - 1));
        android.util.Log.i("WeChatGlass", "HL bar drag end -> " + index);
        if (index != mode) {
            mode = index;
            applyStyle();
            if (onSelect != null) {
                onSelect.onSelect(index);
            }
        }
        posSpring.animateTo(index);
        pressSpring.animateTo(0f);
        scaleXSpring.animateTo(1f);
        scaleYSpring.animateTo(1f);
        dragging = false;
        longPressed = false;
        ensureFrame();
    }

    private void cancelPressTimer() {
        if (pressUp != null) {
            removeCallbacks(pressUp);
            pressUp = null;
        }
    }
}
