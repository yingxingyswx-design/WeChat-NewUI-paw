package com.wechatglass;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ConfigProvider extends ContentProvider {

    private static final String AUTHORITY =
            "io.github.yingxingyswx_design.wechatglass.config";
    /** Hard cap so a giant photo cannot blow the Binder transaction. */
    private static final int MAX_WALLPAPER_BYTES = 24 * 1024 * 1024;

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String key = uri.getLastPathSegment();
        if ("wallpaper".equals(key)) {
            // WeChat has no read permission for the picker URI; this provider
            // runs in the module app's process, which does, and hands the bytes
            // over through Binder.
            MatrixCursor cursor = new MatrixCursor(new String[]{"data"});
            byte[] data = readWallpaper();
            if (data != null) {
                cursor.addRow(new Object[]{data});
            }
            return cursor;
        }
        String value = null;
        if (key != null && getContext() != null) {
            value = getContext().getSharedPreferences("settings", 0).getString(key, null);
        }
        MatrixCursor c = new MatrixCursor(new String[]{"value"});
        c.addRow(new Object[]{value});
        return c;
    }

    private byte[] readWallpaper() {
        if (getContext() == null) {
            return null;
        }
        String uri = getContext().getSharedPreferences("settings", 0)
                .getString("wallpaper_uri", null);
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        InputStream in = null;
        try {
            in = getContext().getContentResolver().openInputStream(Uri.parse(uri));
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > MAX_WALLPAPER_BYTES) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
