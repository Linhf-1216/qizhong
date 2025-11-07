package com.example.android.notepad;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

public class NotePadProvider extends ContentProvider {
    // 数据库基础配置
    private static final String DATABASE_NAME = "NotePad.db";
    private static final int DATABASE_VERSION = 4; //
    private static final String TABLE_NOTES = "notes";

    // 新增：时间戳字段常量（对应文档要求的时间戳显示，与NotePad.Notes保持一致）
    public static final String COLUMN_CREATED_TIME = NotePad.Notes.COLUMN_NAME_CREATED_TIME;

    // 内容URI配置（修复：AUTHORITY与NotePad.java的AUTHORITY统一）
    private static final String AUTHORITY = NotePad.AUTHORITY;
    public static final Uri CONTENT_URI = NotePad.Notes.CONTENT_URI;

    // UriMatcher匹配码
    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;
    private static final UriMatcher sUriMatcher;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        // 修复：仅保留与NotePad.Notes.CONTENT_URI匹配的规则，避免重复匹配
        sUriMatcher.addURI(AUTHORITY, NotePad.Notes.TABLE_NAME, NOTES);
        sUriMatcher.addURI(AUTHORITY, NotePad.Notes.TABLE_NAME + "/#", NOTE_ID);
    }

    // 数据库帮助类（核心修改：修复表名/字段名引用，避免与NotePad.Notes冲突）
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(android.content.Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            // 确保创建表的SQL语句正确包含所有字段
            String CREATE_NOTES_TABLE = "CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT NOT NULL, "
                    + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT, "
                    + NotePad.Notes.COLUMN_NAME_CREATED_TIME + " INTEGER DEFAULT " + System.currentTimeMillis() + ", "
                    + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '默认分类'" // 必须包含
                    + ");";
            db.execSQL(CREATE_NOTES_TABLE);
        }

        @SuppressLint("Range")
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // 升级逻辑：仅在旧版本<2时添加字段，且先检查列是否存在（避免重复）
            if (oldVersion < 2) {
                Cursor cursor = db.rawQuery("PRAGMA table_info(" + NotePad.Notes.TABLE_NAME + ")", null);
                boolean hasTimeColumn = false;
                while (cursor.moveToNext()) {
                    if (COLUMN_CREATED_TIME.equals(cursor.getString(cursor.getColumnIndex("name")))) {
                        hasTimeColumn = true;
                        break;
                    }
                }
                if (!hasTimeColumn) {
                    db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME + " ADD COLUMN " + COLUMN_CREATED_TIME + " INTEGER DEFAULT " + System.currentTimeMillis());
                }
                cursor.close();
            }
            if (oldVersion < 3) {
                Cursor cursor = db.rawQuery("PRAGMA table_info(" + NotePad.Notes.TABLE_NAME + ")", null);
                boolean hasCategoryColumn = false;
                while (cursor.moveToNext()) {
                    if (NotePad.Notes.COLUMN_NAME_CATEGORY.equals(cursor.getString(cursor.getColumnIndex("name")))) {
                        hasCategoryColumn = true;
                        break;
                    }
                }
                if (!hasCategoryColumn) {
                    db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '默认分类'");
                }
                cursor.close();
            }
        }
    }

    // 修复：删除重复的mDbHelper定义，统一使用DatabaseHelper
    private DatabaseHelper mDbHelper;

    @Override
    public boolean onCreate() {
        mDbHelper = new DatabaseHelper(getContext());
        return true;
    }

    // 查询方法（修复：表名引用，确保与DatabaseHelper创建的表一致）
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor cursor;

        int matchCode = sUriMatcher.match(uri);

        // 处理URI匹配（修复：表名使用NotePad.Notes.TABLE_NAME，避免硬编码错误）
        switch (matchCode) {
            case NOTES:
                // 按实验要求：默认按创建时间倒序（使用NotePad.Notes的排序常量）
                if (sortOrder == null || sortOrder.trim().isEmpty()) {
                    sortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
                }
                cursor = db.query(
                        NotePad.Notes.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case NOTE_ID:
                // 补充单条笔记查询逻辑，避免匹配失败
                String noteId = uri.getLastPathSegment();
                selection = TextUtils.isEmpty(selection) ?
                        NotePad.Notes._ID + " = ?" :
                        selection + " AND " + NotePad.Notes._ID + " = ?";
                selectionArgs = (selectionArgs == null || selectionArgs.length == 0) ?
                        new String[]{noteId} :
                        appendArray(selectionArgs, noteId);
                cursor = db.query(
                        NotePad.Notes.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri + ", matchCode: " + matchCode);
        }

        // 设置通知URI（确保列表数据实时更新）
        if (getContext() != null && cursor != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
                return "vnd.android.cursor.dir/vnd.com.example.android.notepad.note";
            case NOTE_ID:
                return "vnd.android.cursor.item/vnd.com.example.android.notepad.note";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    // 插入方法（修复：表名引用，自动添加时间戳）
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        // 若未传入时间戳，自动添加当前时间（使用NotePad.Notes的字段常量）
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CREATED_TIME)) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATED_TIME, System.currentTimeMillis());
        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long rowId = db.insert(NotePad.Notes.TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int rowsDeleted;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                rowsDeleted = db.delete(NotePad.Notes.TABLE_NAME, selection, selectionArgs);
                break;
            case NOTE_ID:
                String id = uri.getLastPathSegment();
                selection = TextUtils.isEmpty(selection) ?
                        NotePad.Notes._ID + " = ?" :
                        selection + " AND " + NotePad.Notes._ID + " = ?";
                selectionArgs = appendArray(selectionArgs, id);
                rowsDeleted = db.delete(NotePad.Notes.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return rowsDeleted;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int rowsUpdated;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                rowsUpdated = db.update(NotePad.Notes.TABLE_NAME, values, selection, selectionArgs);
                break;
            case NOTE_ID:
                String id = uri.getLastPathSegment();
                selection = TextUtils.isEmpty(selection) ?
                        NotePad.Notes._ID + " = ?" :
                        selection + " AND " + NotePad.Notes._ID + " = ?";
                selectionArgs = appendArray(selectionArgs, id);
                rowsUpdated = db.update(NotePad.Notes.TABLE_NAME, values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return rowsUpdated;
    }

    // 工具方法：追加数组元素（用于处理单条笔记的查询/删除/更新）
    private String[] appendArray(String[] original, String add) {
        if (original == null) {
            return new String[]{add};
        }
        String[] newArray = new String[original.length + 1];
        System.arraycopy(original, 0, newArray, 0, original.length);
        newArray[original.length] = add;
        return newArray;
    }
}