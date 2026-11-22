package com.wechatglass;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import android.widget.TextView;

public class SettingsActivity extends Activity {

    private static final String PREFS = "settings";
    private static final String KEY_GLASS = "glass_enabled";
    private static final String KEY_WALLPAPER = "wallpaper_uri";
    private static final String KEY_NATIVE = "native_list";
    private static final String ACTION = "com.wechatglass.CONFIG";
    private static final String ACTION_QUERY = "com.wechatglass.QUERY";
    private static final String ACTION_STATE = "com.wechatglass.STATE";

    private TextView wallpaperHint;
    private M3Switch glassSwitch;
    private M3Switch nativeSwitchField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int bg = night ? 0xFF141218 : 0xFFF7F2FA;
        int card = night ? 0xFF211F26 : 0xFFFFFFFF;
        int onBg = night ? 0xFFE6E0E9 : 0xFF1D1B20;
        int onVar = night ? 0xFFCAC4D0 : 0xFF49454F;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("液态玻璃");
        title.setTextSize(30f);
        title.setTextColor(onBg);
        title.setGravity(Gravity.START);
        root.addView(title, lp(-1, -2, 0, dp(4)));

        TextView sub = new TextView(this);
        sub.setText("外观与效果");
        sub.setTextSize(14f);
        sub.setTextColor(onVar);
        root.addView(sub, lp(-1, -2, 0, dp(24)));

        root.addView(section("液态玻璃", card, onBg, onVar));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText("开启液态玻璃（增加封号风险）");
        label.setTextSize(16f);
        label.setTextColor(onBg);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, labelLp);
        M3Switch glass = new M3Switch(this);
        glassSwitch = glass;
        glass.setCheckedNoAnim(getSharedPreferences(PREFS, 0).getBoolean(KEY_GLASS, true));
        glass.setOnClickListener(v -> {
            userToggled = true;
            boolean on = !glass.isChecked();
            glass.setChecked(on);
            getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_GLASS, on).apply();
            pushConfig();
        });
        row.addView(glass);
        root.addView(row, lp(-1, -2, 0, dp(8)));

        root.addView(section("界面", card, onBg, onVar));

        LinearLayout nativeRow = new LinearLayout(this);
        nativeRow.setOrientation(LinearLayout.HORIZONTAL);
        nativeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView nativeLabel = new TextView(this);
        nativeLabel.setText("启用 Material Design 设计风格（实验）");
        nativeLabel.setTextSize(16f);
        nativeLabel.setTextColor(onBg);
        nativeRow.addView(nativeLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        M3Switch nativeSwitch = new M3Switch(this);
        nativeSwitchField = nativeSwitch;
        // Checked = Material Design (custom list); unchecked = WeChat native.
        nativeSwitch.setCheckedNoAnim(!getSharedPreferences(PREFS, 0)
                .getBoolean(KEY_NATIVE, true));
        nativeSwitch.setOnClickListener(v -> {
            userToggled = true;
            boolean on = !nativeSwitch.isChecked();
            nativeSwitch.setChecked(on);
            getSharedPreferences(PREFS, 0).edit()
                    .putBoolean(KEY_NATIVE, !on).apply();
            pushConfig();
        });
        nativeRow.addView(nativeSwitch);
        root.addView(nativeRow, lp(-1, -2, 0, dp(8)));

        TextView nativeHint = new TextView(this);
        nativeHint.setText("开启后使用自建会话列表（实验，可能不稳定）；"
                + "关闭时使用微信原生列表（含小程序、已登入设备、置顶笔记）。");
        nativeHint.setTextSize(13f);
        nativeHint.setTextColor(onVar);
        root.addView(nativeHint, lp(-1, -2, 0, dp(16)));

        root.addView(section("操作", card, onBg, onVar));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button stopButton = new Button(this);
        stopButton.setText("强制停止微信");
        stopButton.setAllCaps(false);
        stopButton.setTextSize(15f);
        stopButton.setOnClickListener(v -> forceStopWeChat());
        Button openButton = new Button(this);
        openButton.setText("打开微信");
        openButton.setAllCaps(false);
        openButton.setTextSize(15f);
        openButton.setOnClickListener(v -> openWeChat());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        stopLp.rightMargin = dp(8);
        actions.addView(stopButton, stopLp);
        actions.addView(openButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(actions, lp(-1, -2, 0, dp(8)));

        TextView wallTitle = new TextView(this);
        wallTitle.setText("背景壁纸");
        wallTitle.setTextSize(16f);
        wallTitle.setTextColor(onBg);
        root.addView(wallTitle, lp(-1, -2, 0, dp(24)));

        wallpaperHint = new TextView(this);
        wallpaperHint.setTextSize(13f);
        wallpaperHint.setTextColor(onVar);
        String saved = getSharedPreferences(PREFS, 0).getString(KEY_WALLPAPER, null);
        wallpaperHint.setText(saved == null ? "未选择（玻璃只显示内容）" : saved);
        root.addView(wallpaperHint, lp(-1, -2, 0, dp(8)));

        Button pick = new Button(this);
        pick.setText("选择壁纸");
        pick.setAllCaps(false);
        pick.setTextSize(16f);
        pick.setOnClickListener(v -> pickWallpaper());
        root.addView(pick, lp(-1, dp(56), 0, dp(8)));

        registerStateReceiver();
        sendQuery();
    }

    private boolean userToggled;
    private android.content.BroadcastReceiver stateReceiver;

    private void sendQuery() {
        try {
            Intent intent = new Intent(ACTION_QUERY);
            intent.setPackage("com.tencent.mm");
            sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    /** WeChat replies with its real state, so the switches never lie. */
    private void registerStateReceiver() {
        stateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (intent == null || userToggled) {
                    return;
                }
                boolean glass = intent.getBooleanExtra("glass", true);
                boolean nativeList = intent.getBooleanExtra("native_list", true);
                if (glassSwitch != null) {
                    glassSwitch.setCheckedNoAnim(glass);
                }
                if (nativeSwitchField != null) {
                    nativeSwitchField.setCheckedNoAnim(!nativeList);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_STATE);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter,
                    android.content.Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        if (stateReceiver != null) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (Throwable ignored) {
            }
            stateReceiver = null;
        }
        super.onDestroy();
    }

    /** Sends both switches to WeChat's process. */
    private void pushConfig() {
        try {
            Intent intent = new Intent(ACTION);
            intent.setPackage("com.tencent.mm");
            intent.putExtra("glass", glassSwitch == null || glassSwitch.isChecked());
            intent.putExtra("native_list",
                    !(nativeSwitchField != null && nativeSwitchField.isChecked()));
            sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    private void forceStopWeChat() {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "am force-stop com.tencent.mm"});
            process.waitFor();
        } catch (Throwable throwable) {
            try {
                android.app.ActivityManager manager = (android.app.ActivityManager)
                        getSystemService(ACTIVITY_SERVICE);
                if (manager != null) {
                    manager.killBackgroundProcesses("com.tencent.mm");
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void openWeChat() {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void pickWallpaper() {
        Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, 1001);
        } catch (Throwable t) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            startActivityForResult(fallback, 1001);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
            getSharedPreferences(PREFS, 0).edit().putString(KEY_WALLPAPER, uri.toString()).apply();
            try {
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                if (in != null) in.close();
                if (bmp != null) {
                    java.io.File dir = getExternalFilesDir(null);
                    if (dir != null) {
                        if (!dir.exists()) dir.mkdirs();
                        java.io.FileOutputStream out =
                                new java.io.FileOutputStream(new java.io.File(dir, "wallpaper.png"));
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                        out.close();
                        wallpaperHint.setText("已保存: " + uri);
                    }
                }
            } catch (Throwable t) {
                wallpaperHint.setText("保存失败: " + t);
            }
            wallpaperHint.setText(uri.toString());
        }
    }

    private View section(String text, int color, int onBg, int onVar) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(20f);
        t.setTextColor(onBg);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(28));
        t.setBackground(g);
        t.setPadding(dp(20), dp(18), dp(20), dp(18));
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
