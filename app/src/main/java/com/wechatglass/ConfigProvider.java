package com.wechatglass;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class ConfigProvider extends ContentProvider {

    private static final String AUTHORITY =
            "io.github.yingxingyswx_design.wechatglass.config";

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String key = uri.getLastPathSegment();
        String value = null;
        if (key != null && getContext() != null) {
            value = getContext().getSharedPreferences("settings", 0).getString(key, null);
        }
        MatrixCursor c = new MatrixCursor(new String[]{"value"});
        c.addRow(new Object[]{value});
        return c;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
