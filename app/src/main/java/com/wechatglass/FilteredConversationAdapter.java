package com.wechatglass;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.Arrays;

/**
 * A ListAdapter over WeChat's conversation adapter that exposes only the rows
 * matching the selected category.
 *
 * <p>This is a presentation adapter, not a data change: the inner adapter, its
 * list, its positions and its click handlers are untouched. Each visible
 * position is forwarded to the inner adapter with its original position, so the
 * row's view holder keeps the real index WeChat uses to open the right chat.
 * Rows that do not match are simply not handed to the ListView, which is why
 * they leave no blank slots behind.
 */
final class FilteredConversationAdapter extends BaseAdapter {

    private final BaseAdapter inner;
    private final DataSetObserver observer = new DataSetObserver() {
        @Override
        public void onChanged() {
            rebuild();
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            rebuild();
            notifyDataSetInvalidated();
        }
    };

    /** visible[i] = position in the inner adapter of the i-th shown row. */
    private int[] visible = new int[0];
    /** Friends/groups with no conversation row; drawn by us, after the native rows. */
    private java.util.List<ConversationIndex.Entry> extras = new java.util.ArrayList<>();

    FilteredConversationAdapter(BaseAdapter inner) {
        this.inner = inner;
        inner.registerDataSetObserver(observer);
        rebuild();
    }

    void rebuild() {
        int count = inner.getCount();
        if (count <= 0) {
            // The adapter reports -1 before its data source is bound. Treat it
            // as empty; the next notify rebuilds with the real list.
            visible = new int[0];
            return;
        }
        int[] map = new int[count];
        int kept = 0;
        boolean verbose = rebuilds < 1;
        for (int i = 0; i < count; i++) {
            String talker = HomeConversationFilter.talkerOf(inner, i);
            boolean keep = talker == null || HomeConversationFilter.matches(talker);
            if (keep) {
                map[kept++] = i;
            }
            if (verbose) {
                android.util.Log.i("WeChatGlass", "HL item " + i + " " + talker
                        + " keep=" + (keep ? 1 : 0));
            }
        }
        visible = Arrays.copyOf(map, kept);
        if (rebuilds++ < 6) {
            android.util.Log.i("WeChatGlass", "HL rebuild count=" + count + " kept=" + kept);
        }
    }

    private int rebuilds;

    void detach() {
        try {
            inner.unregisterDataSetObserver(observer);
        } catch (Throwable ignored) {
        }
    }

    BaseAdapter inner() {
        return inner;
    }

    private int real(int position) {
        if (position < 0) {
            return 0;
        }
        if (position >= visible.length) {
            return Math.max(0, inner.getCount() - 1);
        }
        return visible[position];
    }

    @Override
    public int getCount() {
        return visible.length;
    }

    @Override
    public Object getItem(int position) {
        return HomeConversationFilter.itemOf(inner, real(position));
    }

    @Override
    public long getItemId(int position) {
        return inner.getItemId(real(position));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Hand the inner adapter its own position: the view holder it tags is
        // what WeChat's click handler reads, so tapping a row still opens the
        // conversation that row shows.
        return inner.getView(real(position), convertView, parent);
    }

    @Override
    public int getItemViewType(int position) {
        return inner.getItemViewType(real(position));
    }

    @Override
    public int getViewTypeCount() {
        return Math.max(1, inner.getViewTypeCount());
    }

    @Override
    public boolean hasStableIds() {
        return inner.hasStableIds();
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }
}
