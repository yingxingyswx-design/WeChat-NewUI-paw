package com.wechatglass;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only index of the home page's three categories.
 *
 * <p>Plan B: the list itself will be ours, so it can show friends and groups
 * that have no conversation row yet (WeChat does not create one until a message
 * exists). Nothing here writes to storage — it only reads the contact table,
 * the conversation table and WeChat's own friend/chatroom SQL predicates.
 */
final class ConversationIndex {

    static final class Entry {
        String username = "";
        String display = "";
        boolean group;
        boolean hasConversation;
        int unread;
        String preview = "";
        long time;
    }

    private static final List<Entry> FRIENDS = new ArrayList<>();
    private static final List<Entry> GROUPS = new ArrayList<>();
    private static final List<Entry> SYSTEM = new ArrayList<>();
    private static volatile boolean loaded;

    private ConversationIndex() {
    }

    static boolean loaded() {
        return loaded;
    }

    static synchronized void load(ClassLoader cl) {
        if (loaded) {
            return;
        }
        try {
            Class<?> serviceClass = de.robv.android.xposed.XposedHelpers
                    .findClass("l51.i9", cl);
            Object container = de.robv.android.xposed.XposedHelpers
                    .callStaticMethod(serviceClass, "b");
            Object contactStorage = de.robv.android.xposed.XposedHelpers
                    .callMethod(container, "r");
            Object conversationStorage = de.robv.android.xposed.XposedHelpers
                    .callMethod(container, "s");
            Object contactDb = de.robv.android.xposed.XposedHelpers
                    .getObjectField(contactStorage, "d");
            Object conversationDb = de.robv.android.xposed.XposedHelpers
                    .getObjectField(conversationStorage, "d");

            Map<String, Entry> conversations = queryConversations(conversationDb);

            // WeChat's own friend predicate (ContactStorage.t):
            // type & 1 != 0, type & 32 == 0, type & 8 == 0,
            // verifyFlag & 8 == 0, deleteFlag == 0, and not a chatroom.
            List<Entry> friends = queryContacts(contactDb,
                    "select username, nickname, conRemark from rcontact"
                            + " where (type & 1 != 0) and type & 32 = 0"
                            + " and type & 8 = 0 and verifyFlag & 8 = 0"
                            + " and deleteFlag = 0"
                            + " and username not like '%@chatroom'"
                            + " and username not like '%@im.chatroom'"
                            + " and username not like 'gh_%'"
                            + " and username not like '%@%'");
            // Chatrooms live in rcontact as '%@chatroom'; the separate chatroom
            // table only keeps the rooms WeChat still tracks. This catches the
            // ones we already left as well.
            List<Entry> groups = queryContacts(contactDb,
                    "select username, nickname, conRemark from rcontact"
                            + " where type & 8 = 0"
                            + " and (username like '%@chatroom'"
                            + " or username like '%@im.chatroom')");

            FRIENDS.clear();
            GROUPS.clear();
            SYSTEM.clear();
            for (Entry e : friends) {
                Entry conv = conversations.get(e.username);
                if (conv != null) {
                    e.hasConversation = true;
                    e.unread = conv.unread;
                    e.preview = conv.preview;
                    e.time = conv.time;
                    e.display = e.display.isEmpty() ? conv.display : e.display;
                }
                if (HomeConversationFilter.isSystem(e.username)) {
                    SYSTEM.add(e);
                } else {
                    FRIENDS.add(e);
                }
            }
            for (Entry e : groups) {
                Entry conv = conversations.get(e.username);
                if (conv != null) {
                    e.hasConversation = true;
                    e.unread = conv.unread;
                    e.preview = conv.preview;
                    e.time = conv.time;
                }
                GROUPS.add(e);
            }
            // 微信自带：直接取现有会话里的系统号（文件传输助手、公众号、
            // 服务号、通知、微信团队……），比从 rcontact 里猜更准。
            for (Entry conv : conversations.values()) {
                if (!HomeConversationFilter.isSystem(conv.username)) {
                    continue;
                }
                Entry e = new Entry();
                e.username = conv.username;
                e.display = conv.username;
                e.hasConversation = true;
                e.unread = conv.unread;
                e.preview = conv.preview;
                e.time = conv.time;
                SYSTEM.add(e);
            }
            loaded = true;
            android.util.Log.i("WeChatGlass", "HL index friends=" + FRIENDS.size()
                    + " (with conv=" + countWithConversation(FRIENDS) + ")"
                    + " groups=" + GROUPS.size()
                    + " (with conv=" + countWithConversation(GROUPS) + ")"
                    + " system=" + SYSTEM.size()
                    + " conversations=" + conversations.size());
            logCount(contactDb, "rcontact @chatroom",
                    "select count(*) from rcontact where username like '%@chatroom'");
            logCount(contactDb, "rcontact @im.chatroom",
                    "select count(*) from rcontact where username like '%@im.chatroom'");
            logCount(contactDb, "chatroom table", "select count(*) from chatroom");
            logCount(contactDb, "rcontact type&2",
                    "select count(*) from rcontact where type & 2 != 0");
            for (int i = 0; i < Math.min(5, FRIENDS.size()); i++) {
                Entry e = FRIENDS.get(i);
                android.util.Log.i("WeChatGlass", "HL friend " + e.username
                        + " name=" + e.display + " conv=" + e.hasConversation);
            }
            for (int i = 0; i < Math.min(5, GROUPS.size()); i++) {
                Entry e = GROUPS.get(i);
                android.util.Log.i("WeChatGlass", "HL group " + e.username
                        + " name=" + e.display + " conv=" + e.hasConversation);
            }
        } catch (Throwable throwable) {
            android.util.Log.e("WeChatGlass", "HL index failed", throwable);
        }
    }

    private static int countWithConversation(List<Entry> list) {
        int n = 0;
        for (Entry e : list) {
            if (e.hasConversation) {
                n++;
            }
        }
        return n;
    }

    private static void logCount(Object db, String label, String sql) {
        Cursor cursor = null;
        try {
            cursor = (Cursor) de.robv.android.xposed.XposedHelpers
                    .callMethod(db, "A", sql, null);
            if (cursor != null && cursor.moveToFirst()) {
                android.util.Log.i("WeChatGlass", "HL count " + label + "=" + cursor.getInt(0));
            }
        } catch (Throwable throwable) {
            android.util.Log.e("WeChatGlass", "HL count failed: " + label, throwable);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static List<Entry> queryContacts(Object db, String sql) {
        List<Entry> out = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = (Cursor) de.robv.android.xposed.XposedHelpers
                    .callMethod(db, "A", sql, null);
            if (cursor != null) {
                int userCol = cursor.getColumnIndex("username");
                int nickCol = cursor.getColumnIndex("nickname");
                int remarkCol = cursor.getColumnIndex("conRemark");
                while (cursor.moveToNext()) {
                    String username = cursor.getString(userCol);
                    if (username == null || username.isEmpty()) {
                        continue;
                    }
                    Entry e = new Entry();
                    e.username = username;
                    String remark = remarkCol >= 0 ? cursor.getString(remarkCol) : null;
                    String nick = nickCol >= 0 ? cursor.getString(nickCol) : null;
                    e.display = remark != null && !remark.isEmpty() ? remark : nick;
                    if (e.display == null || e.display.isEmpty()) {
                        e.display = username;
                    }
                    out.add(e);
                }
            }
        } catch (Throwable throwable) {
            android.util.Log.e("WeChatGlass", "HL query failed: " + sql, throwable);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    private static Map<String, Entry> queryConversations(Object db) {
        Map<String, Entry> out = new HashMap<>();
        Cursor cursor = null;
        try {
            cursor = (Cursor) de.robv.android.xposed.XposedHelpers.callMethod(db, "A",
                    "select username, content, conversationTime, unReadCount, flag"
                            + " from rconversation order by flag desc, conversationTime desc", null);
            if (cursor != null) {
                int userCol = cursor.getColumnIndex("username");
                int contentCol = cursor.getColumnIndex("content");
                int timeCol = cursor.getColumnIndex("conversationTime");
                int unreadCol = cursor.getColumnIndex("unReadCount");
                int flagCol = cursor.getColumnIndex("flag");
                int logged = 0;
                while (cursor.moveToNext()) {
                    String username = cursor.getString(userCol);
                    if (username == null) {
                        continue;
                    }
                    Entry e = new Entry();
                    e.username = username;
                    e.hasConversation = true;
                    e.preview = contentCol >= 0 ? cursor.getString(contentCol) : "";
                    e.time = timeCol >= 0 ? cursor.getLong(timeCol) : 0L;
                    e.unread = unreadCol >= 0 ? cursor.getInt(unreadCol) : 0;
                    out.put(username, e);
                    if (flagCol >= 0 && logged++ < 15) {
                        long flag = cursor.getLong(flagCol);
                        android.util.Log.i("WeChatGlass", "HL flag " + username
                                + " 0x" + Long.toHexString(flag));
                    }
                }
            }
        } catch (Throwable throwable) {
            android.util.Log.e("WeChatGlass", "HL conv query failed", throwable);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    static List<Entry> friends() {
        return FRIENDS;
    }

    static List<Entry> groups() {
        return GROUPS;
    }

    static List<Entry> system() {
        return SYSTEM;
    }
}
