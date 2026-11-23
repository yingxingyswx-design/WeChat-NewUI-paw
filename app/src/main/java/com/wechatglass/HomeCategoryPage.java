package com.wechatglass;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan B: our own conversation list, drawn over WeChat's list.
 *
 * <p>WeChat only creates a conversation row once a message exists, so friends
 * and groups without any chat history never appear. This page renders all of
 * them from {@link ConversationIndex}: rows with history show the last message
 * and time, rows without history show 无聊天记录. WeChat's own list and adapter
 * are left untouched — this view is simply placed on top of it, and tapping a
 * row opens that chat through WeChat's own activity (which also creates the
 * session only when the user opens it).
 */
final class HomeCategoryPage extends FrameLayout {

    static HomeCategoryPage current;

    interface OpenChat {
        void open(String username);
    }

    interface OpenAction {
        void open(String action);
    }

    private final ListView list;
    private final EntryAdapter adapter = new EntryAdapter();
    private final int topInset;
    private final int bottomInset;
    private final boolean night;
    private final OpenChat openChat;
    private final OpenAction openAction;

    HomeCategoryPage(Context context, boolean night, int topInset, int bottomInset,
                     OpenChat openChat, final Runnable onPullDown, OpenAction openAction) {
        super(context);
        this.night = night;
        this.topInset = topInset;
        this.bottomInset = bottomInset;
        this.openChat = openChat;
        this.openAction = openAction;
        setBackgroundColor(night ? 0xFF111111 : 0xFFEDEDED);

        list = new ListView(context);
        list.setBackgroundColor(night ? 0xFF191919 : 0xFFFFFFFF);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(0);
        // A real blank area at the top. ListView resets its own padding while
        // installing an adapter, so the space is a header item instead.
        View topSpace = new View(context);
        topSpace.setBackgroundColor(night ? 0xFF111111 : 0xFFEDEDED);
        topSpace.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, topInset));
        list.addHeaderView(topSpace, null, false);
        if (bottomInset > 0) {
            View bottomSpace = new View(context);
            bottomSpace.setBackgroundColor(night ? 0xFF111111 : 0xFFEDEDED);
            bottomSpace.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, bottomInset));
            list.addFooterView(bottomSpace, null, false);
        }
        list.setAdapter(adapter);
        final float[] pullStart = new float[1];
        final boolean[] pullArmed = new boolean[1];
        list.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        pullStart[0] = event.getRawY();
                        pullArmed[0] = list.getFirstVisiblePosition() == 0;
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (pullArmed[0] && event.getRawY() - pullStart[0]
                                > Ui.dp(getContext(), 90f)) {
                            pullArmed[0] = false;
                            if (onPullDown != null) {
                                onPullDown.run();
                            }
                        }
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        pullArmed[0] = false;
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                    int position, long id) {
                // ListView counts the blank header (and the footer) in
                // position; the adapter only knows its own rows.
                int adapterPosition = position - list.getHeaderViewsCount();
                Row row = adapter.rowAt(adapterPosition);
                if (row == null) {
                    return;
                }
                if (row.action != null && openAction != null) {
                    openAction.open(row.action);
                    return;
                }
                if (row.entry != null && openChat != null) {
                    openChat.open(row.entry.username);
                }
            }
        });
        // A hard top limit: the list is pushed down by a fixed margin instead of
        // padding, because ListView resets its own padding when its adapter is
        // set. The blocker keeps that strip blank (the floating top bar lives
        // there) and stops rows from drawing underneath it.
        // Padding version: rows are clipped below the top inset instead of
        // being pushed by margins.
        addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        current = this;
        android.util.Log.i("WeChatGlass", "HL page ctor topInset=" + topInset
                + " bottomInset=" + bottomInset);
    }

    private static final class Row {
        final ConversationIndex.Entry entry;
        final String label;
        final String action;

        Row(ConversationIndex.Entry entry) {
            this.entry = entry;
            this.label = null;
            this.action = null;
        }

        Row(String label) {
            this.entry = null;
            this.label = label;
            this.action = null;
        }

        Row(String label, String action) {
            this.entry = null;
            this.label = label;
            this.action = action;
        }
    }

    private String deviceText;
    private String noteText;
    private int currentMode = HomeConversationFilter.FRIEND;

    void setHeaderTexts(String device, String note) {
        deviceText = device;
        noteText = note;
        show(currentMode);
    }

    void show(int mode) {
        currentMode = mode;
        List<ConversationIndex.Entry> data;
        switch (mode) {
            case HomeConversationFilter.GROUP:
                data = ConversationIndex.groups();
                break;
            case HomeConversationFilter.SYSTEM:
                data = ConversationIndex.system();
                break;
            case HomeConversationFilter.FRIEND:
            default:
                data = ConversationIndex.friends();
                break;
        }
        List<ConversationIndex.Entry> pinned = new ArrayList<>();
        List<ConversationIndex.Entry> unpinned = new ArrayList<>();
        for (ConversationIndex.Entry entry : data) {
            if (entry.pinned) {
                pinned.add(entry);
            } else {
                unpinned.add(entry);
            }
        }
        // Newest first, like WeChat's own list. (The contact query comes back
        // in pinyin order, which is why the rows looked name-sorted.)
        java.util.Comparator<ConversationIndex.Entry> byTime =
                new java.util.Comparator<ConversationIndex.Entry>() {
                    @Override
                    public int compare(ConversationIndex.Entry a, ConversationIndex.Entry b) {
                        return Long.compare(b.time, a.time);
                    }
                };
        java.util.Collections.sort(pinned, byTime);
        java.util.Collections.sort(unpinned, byTime);

        List<Row> rows = new ArrayList<>();
        rows.add(new Row("小程序", "weapp"));
        rows.add(new Row("已登入设备", "device"));
        rows.add(new Row(noteText != null && !noteText.isEmpty()
                ? noteText : "置顶笔记", "note"));
        if (!pinned.isEmpty()) {
            rows.add(new Row("置顶"));
            for (ConversationIndex.Entry entry : pinned) {
                rows.add(new Row(entry));
            }
        }
        if (!unpinned.isEmpty()) {
            rows.add(new Row("未置顶"));
            for (ConversationIndex.Entry entry : unpinned) {
                rows.add(new Row(entry));
            }
        }
        adapter.setData(rows);
        list.setSelection(0);
    }

    void setPaddings(int top, int bottom) {
        list.setPadding(0, top, 0, bottom);
    }

    private final class EntryAdapter extends BaseAdapter {
        private List<Row> data = new ArrayList<>();

        void setData(List<Row> next) {
            data = next == null ? new ArrayList<Row>() : next;
            notifyDataSetChanged();
        }

        Row rowAt(int position) {
            return position >= 0 && position < data.size() ? data.get(position) : null;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            Row row = rowAt(position);
            return row == null ? null : row.entry;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rowAt(position);
            if (row == null) {
                return new View(getContext());
            }
            if (row.action != null) {
                return buildActionRow(row.label, row.action);
            }
            if (row.entry == null) {
                return buildHeader(convertView, row.label);
            }
            LinearLayout view;
            if (convertView instanceof LinearLayout
                    && convertView.getTag() instanceof Holder) {
                view = (LinearLayout) convertView;
            } else {
                view = buildRow();
            }
            bind(view, row.entry);
            return view;
        }
    }

    /** Entry row (小程序 / 已登入设备 / 置顶笔记); the label is one line, ellipsized. */
    private View buildActionRow(String label, String action) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(context, 14f);
        row.setPadding(pad, Ui.dp(context, 10f), pad, Ui.dp(context, 10f));
        row.setBackgroundColor(night ? 0xFF191919 : 0xFFFFFFFF);

        TextView text = new TextView(context);
        text.setText(label);
        text.setTextSize(16f);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setTextColor(night ? 0xFFF2F2F2 : 0xFF1A1A1A);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextSize(18f);
        chevron.setTextColor(night ? 0xFF8A8A8A : 0xFF9A9A9A);
        row.addView(chevron);
        return row;
    }

    private View buildHeader(View convertView, String label) {        TextView header;
        if (convertView instanceof TextView) {
            header = (TextView) convertView;
        } else {
            header = new TextView(getContext());
            header.setTextSize(12f);
            header.setPadding(Ui.dp(getContext(), 14f), Ui.dp(getContext(), 12f),
                    Ui.dp(getContext(), 14f), Ui.dp(getContext(), 6f));
            header.setTextColor(night ? 0xFF9A9A9A : 0xFF8A8A8A);
            header.setBackgroundColor(night ? 0xFF111111 : 0xFFEDEDED);
        }
        header.setText(label);
        return header;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!logged) {
            logged = true;
            int[] location = new int[2];
            getLocationOnScreen(location);
            View first = list.getChildAt(0);
            android.util.Log.i("WeChatGlass", "HL page geom top=" + location[1]
                    + " listTop=" + list.getTop()
                    + " padTop=" + list.getPaddingTop()
                    + " childTop=" + (first == null ? -1 : first.getTop())
                    + " h=" + getHeight());
        }
    }

    private boolean logged;

    private LinearLayout buildRow() {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(context, 14f);
        row.setPadding(pad, Ui.dp(context, 12f), pad, Ui.dp(context, 12f));
        row.setBackgroundColor(night ? 0xFF191919 : 0xFFFFFFFF);

        android.widget.ImageView avatar = new android.widget.ImageView(context);
        avatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(
                Ui.dp(context, 44f), Ui.dp(context, 44f));
        avatarLp.rightMargin = Ui.dp(context, 12f);
        avatar.setBackgroundColor(0xFFBDBDBD);
        final int avatarRadius = Ui.dp(context, 8f);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), avatarRadius);
            }
        });
        avatar.setClipToOutline(true);
        row.addView(avatar, avatarLp);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(texts, textsLp);

        TextView name = new TextView(context);
        name.setTextSize(16f);
        name.setSingleLine(true);
        name.setTextColor(night ? 0xFFF2F2F2 : 0xFF1A1A1A);
        texts.addView(name);

        TextView preview = new TextView(context);
        preview.setTextSize(13f);
        preview.setSingleLine(true);
        preview.setTextColor(night ? 0xFF9A9A9A : 0xFF8A8A8A);
        texts.addView(preview);

        TextView time = new TextView(context);
        time.setTextSize(12f);
        time.setSingleLine(true);
        time.setTextColor(night ? 0xFF8A8A8A : 0xFF9A9A9A);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.leftMargin = Ui.dp(context, 8f);
        row.addView(time, timeLp);

        row.setTag(new Holder(avatar, name, preview, time));
        return row;
    }

    private void bind(LinearLayout row, ConversationIndex.Entry entry) {
        Holder holder = (Holder) row.getTag();
        if (entry == null) {
            return;
        }
        holder.name.setText(entry.display);
        holder.avatar.setBackgroundColor(colorFor(entry.username));
        AvatarLoader.load(holder.avatar, entry.username);
        if (entry.hasConversation) {
            holder.preview.setText(entry.preview == null || entry.preview.isEmpty()
                    ? "[无消息]" : entry.preview);
            holder.time.setText(formatTime(entry.time));
        } else {
            holder.preview.setText("无聊天记录");
            holder.time.setText("");
        }
    }

    private static String initial(String display) {
        if (display == null || display.isEmpty()) {
            return "?";
        }
        return display.substring(0, 1).toUpperCase();
    }

    private static int colorFor(String username) {
        int hash = username == null ? 0 : username.hashCode();
        int[] colors = {0xFF7E9CD8, 0xFFD88A7E, 0xFF7ED89A, 0xFFD8B77E,
                0xFF9A7ED8, 0xFF7ED8D0, 0xFFD87EAE};
        return colors[Math.abs(hash) % colors.length];
    }

    private static String formatTime(long time) {
        if (time <= 0) {
            return "";
        }
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(time);
        if (now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR)) {
            return String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                    c.get(java.util.Calendar.MINUTE));
        }
        return String.format("%02d/%02d", c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private static final class Holder {
        final android.widget.ImageView avatar;
        final TextView name;
        final TextView preview;
        final TextView time;

        Holder(android.widget.ImageView avatar, TextView name, TextView preview, TextView time) {
            this.avatar = avatar;
            this.name = name;
            this.preview = preview;
            this.time = time;
        }
    }
}
