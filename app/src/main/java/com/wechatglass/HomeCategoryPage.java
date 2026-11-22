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

    private final ListView list;
    private final EntryAdapter adapter = new EntryAdapter();
    private final int topInset;
    private final int bottomInset;
    private final boolean night;
    private final OpenChat openChat;

    HomeCategoryPage(Context context, boolean night, int topInset, int bottomInset,
                     OpenChat openChat) {
        super(context);
        this.night = night;
        this.topInset = topInset;
        this.bottomInset = bottomInset;
        this.openChat = openChat;
        setBackgroundColor(night ? 0xFF111111 : 0xFFEDEDED);

        list = new ListView(context);
        list.setBackgroundColor(night ? 0xFF191919 : 0xFFFFFFFF);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(0);
        list.setPadding(0, topInset, 0, bottomInset);
        list.setClipToPadding(false);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                    int position, long id) {
                ConversationIndex.Entry entry = adapter.getItem(position);
                if (entry != null && openChat != null) {
                    openChat.open(entry.username);
                }
            }
        });
        addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        current = this;
    }

    void show(int mode) {
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
        adapter.setData(data);
        list.setSelection(0);
    }

    void setPaddings(int top, int bottom) {
        list.setPadding(0, top, 0, bottom);
    }

    private final class EntryAdapter extends BaseAdapter {
        private List<ConversationIndex.Entry> data = new ArrayList<>();

        void setData(List<ConversationIndex.Entry> next) {
            data = next == null ? new ArrayList<ConversationIndex.Entry>() : next;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public ConversationIndex.Entry getItem(int position) {
            return position >= 0 && position < data.size() ? data.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ConversationIndex.Entry entry = getItem(position);
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = buildRow();
            }
            bind(row, entry);
            return row;
        }
    }

    private LinearLayout buildRow() {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(context, 14f);
        row.setPadding(pad, Ui.dp(context, 12f), pad, Ui.dp(context, 12f));
        row.setBackgroundColor(night ? 0xFF191919 : 0xFFFFFFFF);

        TextView avatar = new TextView(context);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setTextSize(18f);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(
                Ui.dp(context, 44f), Ui.dp(context, 44f));
        avatarLp.rightMargin = Ui.dp(context, 12f);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF8A8A8A);
        avatar.setBackground(circle);
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
        holder.avatar.setText(initial(entry.display));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(colorFor(entry.username));
        holder.avatar.setBackground(circle);
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
        final TextView avatar;
        final TextView name;
        final TextView preview;
        final TextView time;

        Holder(TextView avatar, TextView name, TextView preview, TextView time) {
            this.avatar = avatar;
            this.name = name;
            this.preview = preview;
            this.time = time;
        }
    }
}
