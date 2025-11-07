package com.example.android.notepad;

import android.net.Uri;
import android.provider.BaseColumns;

public final class NotePad {
    private NotePad() {}

    // 1. ContentProvider的AUTHORITY（与Manifest注册一致，实验必需）
    public static final String AUTHORITY = "com.example.android.notepad.NotePadProvider";

    // 2. 笔记表常量定义（含实验核心常量与动态文件夹兼容常量）
    public static final class Notes implements BaseColumns {
        private Notes() {}

        // 实验核心常量（已存在，保留）
        public static final String TABLE_NAME = "notes";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME);
        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_NOTE = "note";
        public static final String COLUMN_NAME_CREATED_TIME = "created_time";
        public static final String DEFAULT_SORT_ORDER = COLUMN_NAME_CREATED_TIME + " DESC";

        // 此前补充的动态文件夹兼容常量（保留）
        public static final Uri LIVE_FOLDER_URI = Uri.parse("content://" + AUTHORITY + "/live_folders/notes");

        // 修复：补充CONTENT_ID_URI_PATTERN（单条笔记URI匹配模式，仅兼容编译）
        public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse("content://" + AUTHORITY + "/notes/#");
        public static final String CONTENT_URI_STRING = CONTENT_URI.toString();
        public static final String CATEGORY_WORK = "工作";
        public static final String CATEGORY_LIFE = "生活";
        public static final String CATEGORY_STUDY = "学习";
        public static final String CATEGORY_OTHER = "其他";
        public static final String COLUMN_NAME_CATEGORY = "category";
        // 新增分类排序
        public static final String SORT_ORDER_BY_CATEGORY = COLUMN_NAME_CATEGORY + " ASC, " + DEFAULT_SORT_ORDER;
    }
}