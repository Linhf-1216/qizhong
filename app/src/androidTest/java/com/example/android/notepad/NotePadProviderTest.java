/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import static com.example.android.notepad.NotePad.AUTHORITY;
import static com.example.android.notepad.NotePad.Notes.TABLE_NAME;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/**
 * Provides access to a database of notes. Each note has a title, the note
 * itself, a creation date and a modified data.
 */
public class NotePadProviderTest extends ContentProvider implements PipeDataWriter<Cursor> {
    public static final String COLUMN_CREATED = "created_time";
    public static final String COLUMN_TAG     = "tag";

    // Used for debugging and logging
    private static final String TAG = "NotePadProvider";

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "note_pad.db";

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 2;

    /**
     * A projection map used to select columns from the database
     */
    private static HashMap<String, String> sNotesProjectionMap;

    /**
     * A projection map used to select columns from the database
     */
    private static HashMap<String, String> sLiveFolderProjectionMap;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,               // Projection position 0, the note's id
            NotePad.Notes.COLUMN_NAME_NOTE,  // Projection position 1, the note's content
            NotePad.Notes.COLUMN_NAME_TITLE, // Projection position 2, the note's title
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */
    // The incoming URI matches the Notes URI pattern
    private static final int NOTES = 1;

    // The incoming URI matches the Note ID URI pattern
    private static final int NOTE_ID = 2;

    // The incoming URI matches the Live Folder URI pattern
    private static final int LIVE_FOLDER_NOTES = 3;

    /**
     * A UriMatcher instance
     */
    private static final UriMatcher sUriMatcher;
    private static final int NOTES_SEARCH = 4;
    // Handle to a new DatabaseHelper.
    private DatabaseHelper mOpenHelper;


    /**
     * A block that instantiates and sets static objects
     */
    static {

        /*
         * Creates and initializes the URI matcher
         */
        // Create a new instance
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(AUTHORITY, "notes/search/#", NOTES_SEARCH);
        // Add a pattern that routes URIs terminated with "notes" to a NOTES operation
        sUriMatcher.addURI(AUTHORITY, "notes", NOTES);

        // Add a pattern that routes URIs terminated with "notes" plus an integer
        // to a note ID operation
        sUriMatcher.addURI(AUTHORITY, "notes/#", NOTE_ID);

        // Add a pattern that routes URIs terminated with live_folders/notes to a
        // live folder operation
        sUriMatcher.addURI(AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);

        /*
         * Creates and initializes a projection map that returns all columns
         */

        // Creates a new projection map instance. The map returns a column name
        // given a string. The two are usually equal.
        sNotesProjectionMap = new HashMap<String, String>();

        // Maps the string "_ID" to the column name "_ID"
        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes._ID);

        // Maps "title" to "title"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE);

        // Maps "note" to "note"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE);

        // Maps "created" to "created"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE);

        // Maps "modified" to "modified"
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);

        /*
         * Creates an initializes a projection map for handling Live Folders
         */

        // Creates a new projection map instance
        sLiveFolderProjectionMap = new HashMap<String, String>();

        // Maps "_ID" to "_ID AS _ID" for a live folder
        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);

        // Maps "NAME" to "title AS NAME"
        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
                LiveFolders.NAME);
    }

    /**
     *
     * This class helps open, create, and upgrade the database file. Set to package visibility
     * for testing purposes.
     */
    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        /**
         *
         * Creates the underlying database with table name and column names taken from the
         * NotePad class.
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            // 创建表时直接包含 created_time 列，新安装应用无需升级
            String CREATE_NOTES_TABLE = "CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                    + NotePad.Notes._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT NOT NULL, "
                    + "created_time INTEGER DEFAULT 0);"; // 直接包含该列
            db.execSQL(CREATE_NOTES_TABLE);
        }

        /**
         *
         * Demonstrates that the provider must consider what happens when the
         * underlying datastore is changed. In this sample, the database is upgraded the database
         * by destroying the existing data.
         * A real application should upgrade the database in place.
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // 修复升级逻辑顺序错误
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_CREATED + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_TAG + " TEXT DEFAULT ''");
            }

            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }

    /**
     * Initializes the provider by creating a new DatabaseHelper.
     */
    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "onCreate: Context is null, cannot initialize DatabaseHelper");
            return false;
        }
        mOpenHelper = new DatabaseHelper(context);
        return mOpenHelper != null;
    }

    /**
     * Handles query requests from clients.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        if (mOpenHelper == null) {
            Log.e(TAG, "query: DatabaseHelper is not initialized");
            return null;
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (db == null || !db.isOpen()) {
            Log.e(TAG, "query: Failed to open database");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                qb.setProjectionMap(sNotesProjectionMap);
                break;

            case NOTE_ID:
                qb.setProjectionMap(sNotesProjectionMap);
                if (uri.getPathSegments().size() <= NotePad.Notes.NOTE_ID_PATH_POSITION) {
                    Log.e(TAG, "query: Invalid URI path for NOTE_ID");
                    throw new IllegalArgumentException("Invalid URI: " + uri);
                }
                qb.appendWhere(NotePad.Notes._ID + "=" +
                        uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
                break;

            case NOTES_SEARCH:
                if (uri.getPathSegments().isEmpty()) {
                    Log.e(TAG, "query: Empty path for NOTES_SEARCH");
                    throw new IllegalArgumentException("Invalid URI: " + uri);
                }
                String kw = uri.getLastPathSegment();
                kw = (kw == null) ? "" : kw;
                Cursor searchCursor = db.query(TABLE_NAME, projection,
                        "title LIKE ? OR note LIKE ?",
                        new String[]{"%" + kw + "%", "%" + kw + "%"},
                        null, null, sortOrder);
                if (searchCursor != null) {
                    Context context = getContext();
                    if (context != null) {
                        searchCursor.setNotificationUri(context.getContentResolver(), uri);
                    }
                }
                return searchCursor;

            case LIVE_FOLDER_NOTES:
                qb.setProjectionMap(sLiveFolderProjectionMap);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String orderBy = TextUtils.isEmpty(sortOrder) ? NotePad.Notes.DEFAULT_SORT_ORDER : sortOrder;

        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        Context context = getContext();
        if (c != null && context != null) {
            c.setNotificationUri(context.getContentResolver(), uri);
        }
        return c;
    }

    /**
     * Returns the MIME type of the data at the given URI.
     */
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return NotePad.Notes.CONTENT_TYPE;
            case NOTE_ID:
                return NotePad.Notes.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Describes the MIME types supported for streaming note data.
     */
    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription("Note",
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    /**
     * Returns the supported stream types for the given URI.
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;
            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Opens a typed asset file for streaming note data.
     */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);
        if (mimeTypes != null) {
            Cursor c = query(uri, READ_NOTE_PROJECTION, null, null, null);
            if (c == null) {
                Log.e(TAG, "openTypedAssetFile: Query returned null Cursor for URI: " + uri);
                throw new FileNotFoundException("Query returned null for " + uri);
            }
            if (!c.moveToFirst()) {
                c.close();
                Log.e(TAG, "openTypedAssetFile: No data found for URI: " + uri);
                throw new FileNotFoundException("No data for " + uri);
            }
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    /**
     * Writes note data to a pipe for streaming.
     */
    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
                                Bundle opts, Cursor c) {
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            String title = c.getString(READ_NOTE_TITLE_INDEX);
            String note = c.getString(READ_NOTE_NOTE_INDEX);
            pw.println(title != null ? title : "");
            pw.println("");
            pw.println(note != null ? note : "");
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "writeDataToPipe: Encoding error", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
                Log.w(TAG, "writeDataToPipe: Close error", e);
            }
        }
    }

    /**
     * Inserts a new note into the database.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        if (mOpenHelper == null) {
            Log.e(TAG, "insert: DatabaseHelper is not initialized");
            throw new SQLException("Database not initialized");
        }
        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();
        Long now = System.currentTimeMillis();

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE)) {
            Resources r = Resources.getSystem();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE)) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
        }
        if (!values.containsKey(COLUMN_CREATED)) {
            values.put(COLUMN_CREATED, System.currentTimeMillis());
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(TABLE_NAME, NotePad.Notes.COLUMN_NAME_NOTE, values);
        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);
            Context context = getContext();
            if (context != null) {
                context.getContentResolver().notifyChange(noteUri, null);
            }
            return noteUri;
        }
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Deletes notes from the database.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        if (mOpenHelper == null) {
            Log.e(TAG, "delete: DatabaseHelper is not initialized");
            return 0;
        }
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.delete(TABLE_NAME, where, whereArgs);
                break;
            case NOTE_ID:
                finalWhere = NotePad.Notes._ID + " = " +
                        uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere += " AND " + where;
                }
                count = db.delete(TABLE_NAME, finalWhere, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        Context context = getContext();
        if (context != null) {
            context.getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * Updates notes in the database.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        if (mOpenHelper == null) {
            Log.e(TAG, "update: DatabaseHelper is not initialized");
            return 0;
        }
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.update(TABLE_NAME, values, where, whereArgs);
                break;
            case NOTE_ID:
                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                finalWhere = NotePad.Notes._ID + " = " + noteId;
                if (where != null) {
                    finalWhere += " AND " + where;
                }
                count = db.update(TABLE_NAME, values, finalWhere, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        Context context = getContext();
        if (context != null) {
            context.getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * For testing: returns the database helper.
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}