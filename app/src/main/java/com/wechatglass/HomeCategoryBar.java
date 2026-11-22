package com.wechatglass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The home category switch: 好友 / 群聊 / 微信 as one liquid-glass capsule,
 * placed at the left end of the home top bar.
 *
 * <p>The selected segment is carried by a WeChat-green pill that slides between
 * tabs (spring-ish overshoot), and the whole capsule grows while the user
 * presses on it — the same press feedback the reference LiquidGlass module uses
 * for its floating bar.
 */
final class HomeCategoryBar extends FrameLayout {

    static final int HEIGHT_DP = 40;
    /** WeChat green, the same accent the bottom tab bar uses. */
    private static final int WECHAT_GREEN = 0xFF07C160;
    private static final long PRESS_DELAY_MS = 360L;

    interface OnSelect {
        void onSelect(int mode);
    }

    private final TextView[] tabs = new TextView[3];
    private final LinearLayout row;
    private final View indicator;
    private OnSelect onSelect;
    private int mode;
    private boolean night;
    private boolean pressed;
    private ValueAnimator slide;
    private Runnable pressUp;

    HomeCategoryBar(Context context, ViewGroup backdrop, boolean night,
                    String[] labels, int mode) {
        super(context);
        this.night = night;
        this.mode = mode;
        setClipToPadding(false);
        setClipChildren(false);

        View glass = com.wechatglass.liquid.LiquidPanelFactory.create(
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
            fallback.setStroke(Ui.dp(context, 1f), night ? 0x33FFFFFF : 0x22000000);
            setBackground(fallback);
        }

        // The green pill lives behind the labels and slides between them.
        indicator = new View(context);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(WECHAT_GREEN);
        pill.setCornerRadius(Ui.dp(context, (HEIGHT_DP - 6) / 2f));
        indicator.setBackground(pill);
        addView(indicator, new LayoutParams(0, 0));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(context, 3f);
        row.setPadding(pad, pad, pad, pad);
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
        positionIndicator(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        positionIndicator(false);
    }

    private void applyMode(int index) {
        if (mode == index) {
            return;
        }
        mode = index;
        applyStyle();
        positionIndicator(true);
        if (onSelect != null) {
            onSelect.onSelect(index);
        }
    }

    private void applyStyle() {
        for (int i = 0; i < tabs.length; i++) {
            TextView tab = tabs[i];
            boolean selected = i == mode;
            tab.setTextColor(selected
                    ? 0xFFFFFFFF
                    : (night ? 0xFFE8E8E8 : 0xFF333333));
            tab.setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void positionIndicator(boolean animate) {
        int index = Math.max(0, Math.min(mode, tabs.length - 1));
        TextView tab = tabs[index];
        if (tab == null || tab.getWidth() <= 0 || row.getWidth() <= 0) {
            return;
        }
        int targetLeft = row.getLeft() + tab.getLeft();
        int targetTop = row.getTop() + tab.getTop();
        int targetWidth = tab.getWidth();
        int targetHeight = tab.getHeight();

        if (slide != null) {
            slide.cancel();
            slide = null;
        }
        if (!animate) {
            applyIndicator(targetLeft, targetTop, targetWidth, targetHeight);
            return;
        }
        final int startLeft = indicator.getLeft();
        final int startWidth = indicator.getWidth();
        if (startWidth <= 0) {
            applyIndicator(targetLeft, targetTop, targetWidth, targetHeight);
            return;
        }
        final int endLeft = targetLeft;
        final int endWidth = targetWidth;
        final int top = targetTop;
        final int height = targetHeight;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(260L);
        animator.setInterpolator(new OvershootInterpolator(1.15f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float f = (Float) animation.getAnimatedValue();
                int left = Math.round(startLeft + (endLeft - startLeft) * f);
                int width = Math.round(startWidth + (endWidth - startWidth) * f);
                applyIndicator(left, top, width, height);
            }
        });
        slide = animator;
        animator.start();
    }

    /**
     * Moves the pill by laying it out directly. Going through
     * {@code setLayoutParams} here would call {@code requestLayout()} from
     * inside the bar's own layout pass, which produced an endless
     * "second layout pass" loop and the visible flicker.
     */
    private void applyIndicator(int left, int top, int width, int height) {
        indicator.layout(left, top, left + Math.max(1, width), top + Math.max(1, height));
        indicator.invalidate();
        invalidate();
    }

    /** Press feedback: after a short hold the capsule grows, and it settles back on release. */
    private void installPressFeedback(View target) {
        target.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        schedulePress();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelPress();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    private void schedulePress() {
        if (pressUp != null) {
            removeCallbacks(pressUp);
        }
        pressUp = new Runnable() {
            @Override
            public void run() {
                if (pressed) {
                    return;
                }
                pressed = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                animate().scaleX(1.08f).scaleY(1.08f)
                        .setDuration(180L)
                        .setInterpolator(new OvershootInterpolator(2.2f))
                        .start();
            }
        };
        postDelayed(pressUp, PRESS_DELAY_MS);
    }

    private void cancelPress() {
        if (pressUp != null) {
            removeCallbacks(pressUp);
            pressUp = null;
        }
        if (pressed) {
            pressed = false;
            animate().scaleX(1f).scaleY(1f)
                    .setDuration(160L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }
}
