package com.wechatglass;

import android.widget.ListView;

import java.util.Locale;

/**
 * Classifies the rows of WeChat's home conversation list into 好友 / 群聊 / 微信
 * and decides which of them the list is allowed to show.
 *
 * <p>Nothing here reads storage, a database or message content: a row's
 * conversation object is already in memory (the adapter built it), and the only
 * field used is its username. The filtering itself is done by swapping the
 * ListView's adapter for {@link FilteredConversationAdapter}, so WeChat's own
 * rows, click handlers and unread counters are untouched.
 */
final class HomeConversationFilter {

    static final int FRIEND = 0;
    static final int GROUP = 1;
    static final int SYSTEM = 2;

    private static volatile int mode = FRIEND;
    private static volatile FilteredConversationAdapter current;
    private static volatile boolean initialSelectionDone;

    private HomeConversationFilter() {
    }

    static int mode() {
        return mode;
    }

    static void setMode(int next) {
        if (next < FRIEND || next > SYSTEM || next == mode) {
            return;
        }
        android.util.Log.i("WeChatGlass", "HL mode " + mode + " -> " + next);
        mode = next;
        HomeCategoryPage page = HomeCategoryPage.current;
        if (page != null) {
            page.show(next);
        }
        refresh();
    }

    /** Rebuilds the visible set and lets the ListView relayout. */
    static void refresh() {
        FilteredConversationAdapter adapter = current;
        if (adapter == null) {
            return;
        }
        try {
            adapter.rebuild();
            adapter.notifyDataSetChanged();
        } catch (Throwable ignored) {
        }
    }

    /** Whether the filtered adapter is installed on the home list. */
    static boolean active() {
        return current != null;
    }

    /**
     * Wraps the home ListView's conversation adapter once. Returns whether the
     * list is (now) filtered; if WeChat uses a different adapter shape this
     * leaves the list exactly as it was.
     */
    static boolean attach(ListView listView) {
        if (listView == null) {
            return current != null;
        }
        try {
            android.widget.ListAdapter adapter =
                    (android.widget.ListAdapter) listView.getAdapter();
            if (adapter == null) {
                return false;
            }
            // The home list carries header rows (logged-in devices, TaskBar).
            // ListView wraps its adapter in HeaderViewListAdapter for them; we
            // wrap only the conversation adapter and let ListView put its own
            // header wrapper back around ours.
            android.widget.ListAdapter conversations = adapter;
            if (adapter instanceof android.widget.HeaderViewListAdapter) {
                conversations = ((android.widget.HeaderViewListAdapter) adapter)
                        .getWrappedAdapter();
            }
            if (conversations instanceof FilteredConversationAdapter) {
                return true;
            }
            if (!(conversations instanceof android.widget.BaseAdapter)) {
                return false;
            }
            if (!"iq5.a1".equals(conversations.getClass().getName())) {
                return false;
            }
            // Replacing the adapter resets ListView's scroll state, which on
            // the home page jumps the list up into the pull-down mini-program
            // panel. Remember where it was and put it back.
            int firstVisible = listView.getFirstVisiblePosition();
            int topOffset = 0;
            android.view.View firstChild = listView.getChildAt(0);
            if (firstChild != null) {
                topOffset = firstChild.getTop() - listView.getPaddingTop();
            }
            FilteredConversationAdapter wrapper =
                    new FilteredConversationAdapter(
                            (android.widget.BaseAdapter) conversations);
            listView.setAdapter(wrapper);
            current = wrapper;
            final int headerCount = listView.getHeaderViewsCount();
            if (firstVisible > 0 || topOffset != 0) {
                final int restorePosition = firstVisible;
                final int restoreTop = topOffset;
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        int count = listView.getCount();
                        int position = Math.max(0, Math.min(restorePosition, count - 1));
                        listView.setSelectionFromTop(position, restoreTop);
                    }
                });
            } else if (headerCount > 0 && !initialSelectionDone) {
                // The home list's first header is the TaskBar mini-program
                // panel. A reset adapter starts at position 0, which renders
                // that panel for a frame and then snaps back — the visible
                // "flash into the mini-program page". Park on the first
                // conversation synchronously, before the next layout.
                if (listView.getCount() > headerCount) {
                    listView.setSelectionFromTop(headerCount, 0);
                    initialSelectionDone = true;
                }
            }
            android.util.Log.i("WeChatGlass",
                    "HL filter adapter attached: " + conversations.getClass().getName()
                            + " headers=" + listView.getHeaderViewsCount()
                            + " visible=" + wrapper.getCount()
                            + " first=" + firstVisible);
            return true;
        } catch (Throwable throwable) {
            android.util.Log.e("WeChatGlass", "HL filter adapter failed", throwable);
            return false;
        }
    }

    static void detach() {
        FilteredConversationAdapter adapter = current;
        current = null;
        if (adapter != null) {
            adapter.detach();
        }
    }

    static Object itemOf(Object adapter, int position) {
        try {
            return de.robv.android.xposed.XposedHelpers
                    .callMethod(adapter, "getItem", position);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String talkerOf(Object adapter, int position) {
        Object conversation = itemOf(adapter, position);
        if (conversation == null) {
            return null;
        }
        try {
            Object username = de.robv.android.xposed.XposedHelpers
                    .callMethod(conversation, "f1");
            return username instanceof String ? (String) username : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean matches(String talker) {
        if (talker == null || talker.isEmpty()) {
            return true;
        }
        boolean group = isGroup(talker);
        boolean system = isSystem(talker);
        switch (mode) {
            case GROUP:
                return group;
            case SYSTEM:
                return system && !group;
            case FRIEND:
            default:
                return !group && !system;
        }
    }

    /** 群聊：普通群、折叠群聊入口。 */
    static boolean isGroup(String talker) {
        String t = talker.toLowerCase(Locale.ROOT);
        return t.endsWith("@chatroom") || t.equals("@placeholder_foldgroup");
    }

    /**
     * 微信自带会话：文件传输助手、微信团队、微信支付、公众号/服务号、系统通知等。
     * Anything not matched here is treated as a friend chat — defaulting to
     * visible is the safe direction.
     */
    static boolean isSystem(String talker) {
        String t = talker.toLowerCase(Locale.ROOT);
        if (t.startsWith("gh_")) {
            return true; // 公众号 / 服务号
        }
        if (t.indexOf('@') > 0) {
            // @openim, @app, @finder, @placeholder_* … — every @-suffixed
            // session except a real group chatroom is a WeChat-owned surface.
            return !isGroup(t);
        }
        switch (t) {
            case "filehelper":          // 文件传输助手
            case "weixin":              // 微信团队
            case "weixinpay":
            case "wxpay":               // 微信支付
            case "fmessage":            // 朋友推荐
            case "medianote":           // 语音记事本
            case "floatbottle":         // 漂流瓶
            case "bottle":
            case "qqmail":              // QQ邮箱提醒
            case "newsapp":             // 腾讯新闻
            case "notifymessage":       // 服务通知
            case "tmessage":            // 模板消息
            case "mphelper":            // 公众平台助手
            case "brandsessionholder":  // 订阅号会话
            case "brandservicesessionholder": // 服务号会话
            case "service_officialaccounts":  // 服务号聚合入口
            case "officialaccounts":
            case "qqfriend":
            case "qqsync":
            case "qmessage":
            case "voipapp":
            case "voicevoipapp":
            case "exmail_tool":
            case "weibo":
            case "shake_message":
                return true;
            default:
                return false;
        }
    }
}
