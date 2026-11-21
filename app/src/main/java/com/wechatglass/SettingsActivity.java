package com.wechatglass;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Simple settings UI: one switch, no resources needed. */
public class SettingsActivity extends Activity {

    private CheckBox glassBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("WeChat NewUI");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("只作用于微信顶栏与悬浮输入栏。");
        hint.setTextSize(13);
        hint.setTextColor(0xFF666666);
        hint.setPadding(0, pad / 2, 0, pad);
        root.addView(hint);

        glassBox = new CheckBox(this);
        glassBox.setText("开启毛玻璃（增加封号风险）");
        glassBox.setTextSize(16);
        glassBox.setPadding(0, pad / 2, 0, pad / 2);
        glassBox.setChecked(getSharedPreferences("settings", 0)
                .getBoolean("glass", false));
        glassBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences("settings", 0)
                    .edit()
                    .putBoolean("glass", isChecked)
                    .apply();
            sendConfig(isChecked);
        });
        root.addView(glassBox);

        TextView note = new TextView(this);
        note.setText("关闭时为纯色（微信白/黑 + 状态栏渐变）。\n"
                + "开启后使用 GPU 模糊，需要 Android 12+；低版本自动回退纯色。\n"
                + "切换后重新进入微信页面生效。");
        note.setTextSize(12);
        note.setTextColor(0xFF888888);
        note.setPadding(0, pad / 2, 0, 0);
        root.addView(note);

        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        sendConfig(glassBox.isChecked());
    }

    private void sendConfig(boolean glass) {
        try {
            android.content.Intent intent = new android.content.Intent(HookConfig.ACTION);
            intent.setPackage("com.tencent.mm");
            intent.putExtra(HookConfig.KEY_GLASS, glass);
            sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }
}
