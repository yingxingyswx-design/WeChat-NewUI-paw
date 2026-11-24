package com.wechatglass;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.wechatglass.m3.M3Theme;
import com.wechatglass.m3.M3Type;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * M3E 2-column card grid for the Discover and Me tabs.
 *
 * <p>Everything is read from the live Preference objects of the native page:
 * the title lives in the private field {@code h} (WeChat overrides getTitle()
 * to return null), the icon in {@code i} and the layout in {@code F}. Rows are
 * grouped by the section-gap objects, never by hard-coded indices. Clicks go
 * through {@code performItemClick} on the native list, i.e. WeChat's own path.
 */
class M3GridPage extends M3PageBase {

    static final int DISCOVER = 0;
    static final int ME = 1;

    private final int mode;
    private final GridAdapter adapter = new GridAdapter();
    private final List<Row> rows = new ArrayList<>();
    private View nativeList;
    private ListAdapter nativeAdapter;

    M3GridPage(Context context, String title, int mode) {
        super(context, title);
        this.mode = mode;
        list.setAdapter(adapter);
    }

    /** Reads the native page's adapter objects; safe to call repeatedly. */
    boolean bind(View pageRoot) {
        View found = findList(pageRoot);
        if (found == null) {
            return false;
        }
        ListAdapter adapter = null;
        try {
            Object value = de.robv.android.xposed.XposedHelpers.callMethod(found, "getAdapter");
            if (value instanceof ListAdapter) {
                adapter = (ListAdapter) value;
            }
        } catch (Throwable ignored) {
        }
        if (adapter == null || adapter.getCount() == 0) {
            return false;
        }
        if (found == nativeList && adapter == nativeAdapter && !rows.isEmpty()) {
            return true;
        }
        nativeList = found;
        nativeAdapter = adapter;
        rebuild();
        return true;
    }

    private void rebuild() {
        List<Cell> cells = new ArrayList<>();
        Object profile = null;
        int profilePos = -1;
        for (int pos = 0; pos < nativeAdapter.getCount(); pos++) {
            Object item = nativeAdapter.getItem(pos);
            if (item == null) {
                continue;
            }
            String type = item.getClass().getSimpleName();
            if (type.contains("SmallCategory")) {
                continue;
            }
            if (mode == ME && type.contains("AccountInfoPreference")) {
                profile = item;
                profilePos = pos;
                continue;
            }
            Cell cell = new Cell();
            cell.pref = item;
            cell.position = pos;
            cell.title = asString(readField(item, "h"));
            if (cell.title.trim().isEmpty()) {
                // Trailing placeholders / category rows carry no title; they are
                // what showed up as ghost cards before.
                continue;
            }
            Object iconRes = readField(item, "i");
            if (iconRes instanceof Integer) {
                cell.iconRes = (Integer) iconRes;
            }
            cells.add(cell);
        }
        rows.clear();
        if (mode == ME && profile != null) {
            Row header = new Row();
            header.profile = profile;
            header.position = profilePos;
            rows.add(header);
        }
        for (int i = 0; i < cells.size(); i += 2) {
            Row row = new Row();
            row.left = cells.get(i);
            if (i + 1 < cells.size()) {
                row.right = cells.get(i + 1);
            }
            rows.add(row);
        }
        adapter.notifyDataSetChanged();
    }

    private static final class Cell {
        Object pref;
        int position;
        String title;
        int iconRes;
    }

    private static final class Row {
        Object profile;
        int position;
        Cell left;
        Cell right;
    }

    private final class GridAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position < 0 || position >= rows.size()) {
                return new View(getContext());
            }
            Row row = rows.get(position);
            if (row.profile != null) {
                return profileCard(row);
            }
            Context context = getContext();
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(pad, M3Theme.dp(context, 3f), pad, M3Theme.dp(context, 3f));
            line.addView(card(row.left), weightParams());
            LinearLayout.LayoutParams rightLp = weightParams();
            if (row.right == null) {
                line.addView(new View(context), rightLp);
            } else {
                rightLp.leftMargin = gap;
                line.addView(card(row.right), rightLp);
            }
            return line;
        }
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private View card(Cell cell) {
        if (cell == null) {
            return new View(getContext());
        }
        View view = mode == DISCOVER ? discoverCard(cell) : meCard(cell);
        final Cell target = cell;
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickNative(target.position);
            }
        });
        return view;
    }

    private View discoverCard(Cell cell) {
        Context context = getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground());
        int padding = M3Theme.dp(context, 16f);
        card.setPadding(padding, padding, padding, padding);
        Drawable icon = iconFor(cell);
        if (icon != null) {
            card.addView(iconBox(icon, 48f, 28f));
        }
        TextView title = new TextView(context);
        title.setText(cell.title);
        M3Type.apply(title, M3Type.TITLE_MEDIUM);
        title.setTextColor(scheme.onSurface);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = icon == null ? 0 : M3Theme.dp(context, 12f);
        card.addView(title, lp);
        card.setMinimumHeight(M3Theme.dp(context, 132f));
        return card;
    }

    private View meCard(Cell cell) {
        Context context = getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBackground());
        card.setPadding(M3Theme.dp(context, 14f), 0, M3Theme.dp(context, 14f), 0);
        Drawable icon = iconFor(cell);
        if (icon != null) {
            card.addView(iconBox(icon, 40f, 22f));
        }
        TextView title = new TextView(context);
        title.setText(cell.title);
        M3Type.apply(title, M3Type.TITLE_MEDIUM);
        title.setTextColor(scheme.onSurface);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = icon == null ? 0 : M3Theme.dp(context, 12f);
        card.addView(title, lp);
        card.setMinimumHeight(M3Theme.dp(context, 64f));
        return card;
    }

    private View profileCard(Row row) {
        Context context = getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBackground());
        int padding = M3Theme.dp(context, 16f);
        card.setPadding(padding, padding, padding, padding);

        ImageView avatar = new ImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable drawable = readAvatar(row.profile);
        String username = asString(readField(row.profile, "K"));
        if (drawable != null) {
            avatar.setImageDrawable(drawable);
        } else if (username.length() > 0) {
            AvatarLoader.load(avatar, username);
        }
        avatar.setBackgroundColor(scheme.surfaceContainerHighest);
        applyCircle(avatar);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(
                M3Theme.dp(context, 64f), M3Theme.dp(context, 64f));
        card.addView(avatar, avatarLp);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.leftMargin = M3Theme.dp(context, 16f);
        card.addView(texts, textsLp);

        TextView name = new TextView(context);        name.setText(asString(readField(row.profile, "J")));
        M3Type.apply(name, M3Type.HEADLINE_SMALL, true);
        name.setTextColor(scheme.onSurface);
        name.setSingleLine(true);
        texts.addView(name);

        TextView alias = new TextView(context);
        String id = asString(readField(row.profile, "L"));
        alias.setText("Weixin ID: " + id);
        M3Type.apply(alias, M3Type.BODY_MEDIUM);
        alias.setTextColor(scheme.onSurfaceVariant);
        alias.setSingleLine(true);
        LinearLayout.LayoutParams aliasLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aliasLp.topMargin = M3Theme.dp(context, 4f);
        texts.addView(alias, aliasLp);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickNative(0);
            }
        });
        return card;
    }

    private Drawable readAvatar(Object preference) {
        Object[] fields = {readField(preference, "M"), readField(preference, "N")};
        for (Object value : fields) {
            if (value instanceof ImageView) {
                Drawable drawable = ((ImageView) value).getDrawable();
                if (drawable != null) {
                    return drawable;
                }
            }
        }
        return null;
    }

    private static final android.view.ViewOutlineProvider OVAL =
            new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            };

    private void applyCircle(ImageView view) {
        view.setOutlineProvider(OVAL);
        view.setClipToOutline(true);
    }

    private Drawable iconFor(Cell cell) {
        // WeChat ships the right glyph for every entry (and every language), so
        // reuse its drawable by the id carried on the Preference object.
        if (cell.iconRes != 0) {
            try {
                Drawable nativeIcon = getContext().getDrawable(cell.iconRes);
                if (nativeIcon != null) {
                    return nativeIcon;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String iconForTitle(String title) {
        String text = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
        if (contains(text, "pay", "支付", "サービス", "服务")) {
            return "m3_ic_pay";
        }
        if (contains(text, "お気に入り", "收藏", "favorite", "saved")) {
            return "m3_ic_bookmark";
        }
        if (contains(text, "モーメンツ", "朋友圈", "moments")) {
            return "m3_ic_moments";
        }
        if (contains(text, "動画", "视频号", "channels", "video")) {
            return "m3_ic_channels";
        }
        if (contains(text, "カード", "卡包", "wallet", "card")) {
            return "m3_ic_card";
        }
        if (contains(text, "ステッカー", "表情", "sticker", "emoji", "mood")) {
            return "m3_ic_emoji";
        }
        if (contains(text, "設定", "设置", "settings")) {
            return "m3_ic_settings";
        }
        return "m3_ic_bookmark";
    }

    private static boolean contains(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void clickNative(int position) {
        if (!(nativeList instanceof AbsListView) || nativeAdapter == null) {
            return;
        }
        AbsListView view = (AbsListView) nativeList;
        int first = view.getFirstVisiblePosition();
        View row = view.getChildAt(position - first);
        try {
            view.performItemClick(row != null ? row : view, position,
                    nativeAdapter.getItemId(position));
        } catch (Throwable throwable) {
            android.util.Log.w("WeChatGlass", "performItemClick failed", throwable);
        }
    }

    private static View findList(View root) {
        if (root instanceof AbsListView) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findList(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
