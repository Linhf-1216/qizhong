package com.example.android.notepad;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class NotesList extends ListActivity {
    private EditText etSearch;
    private Button btnSearch;
    private SimpleCursorAdapter adapter;
    private Cursor cursor;
    private static final String[] PROJECTION = new String[]{
            BaseColumns._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_CREATED_TIME
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_list);

        // 初始化控件
        etSearch = findViewById(R.id.et_search_note);
        btnSearch = findViewById(R.id.btn_search);
        Spinner spinnerCategory = findViewById(R.id.spinner_category);

        // 初始化列表适配器（关键：绑定删除图标点击事件）
        initListAdapter();

        // 搜索功能
        btnSearch.setOnClickListener(v -> performSearch());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 分类筛选
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = (String) parent.getItemAtPosition(position);
                loadNotesWithFilter(etSearch.getText().toString().trim(),
                        selectedCategory.equals("全部") ? null : selectedCategory);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 列表项点击事件（打开编辑页）
        ListView listView = findViewById(android.R.id.list);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id);
            Intent intent = new Intent(NotesList.this, NoteEditor.class);
            intent.setData(noteUri);
            startActivity(intent);
        });
    }

    // 初始化列表适配器，为删除图标绑定事件
    private void initListAdapter() {
        cursor = getContentResolver().query(
                NotePad.Notes.CONTENT_URI,
                PROJECTION,
                null,
                null,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // 适配器：将数据绑定到列表项
        adapter = new SimpleCursorAdapter(
                this,
                R.layout.note_list_item, // 使用带删除图标的布局
                cursor,
                new String[]{NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_CREATED_TIME},
                new int[]{android.R.id.text1, R.id.tv_note_timestamp},
                0
        ) {
            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                super.bindView(view, context, cursor);
                // 格式化时间戳
                @SuppressLint("Range") long time = cursor.getLong(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CREATED_TIME));
                String timeStr = time <= 0 ? "未知时间" :
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(time));
                ((TextView) view.findViewById(R.id.tv_note_timestamp)).setText(timeStr);

                // 获取当前笔记的ID（关键：从Cursor中获取当前条目的ID）
                @SuppressLint("Range") long noteId = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));

                // 绑定删除图标点击事件
                ImageView ivDelete = view.findViewById(R.id.iv_delete);
                ivDelete.setOnClickListener(v -> {
                    // 生成当前笔记的Uri
                    Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, noteId);
                    // 显示删除确认框
                    showDeleteDialog(noteUri);
                });
            }
        };

        // 设置适配器到列表
        ListView listView = findViewById(android.R.id.list);
        listView.setAdapter(adapter);
    }

    // 显示删除确认对话框
    private void showDeleteDialog(Uri noteUri) {
        new AlertDialog.Builder(this)
                .setTitle("删除笔记")
                .setMessage("确定要删除这条笔记吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    // 执行删除
                    int rowsDeleted = getContentResolver().delete(noteUri, null, null);
                    if (rowsDeleted > 0) {
                        Toast.makeText(NotesList.this, "删除成功", Toast.LENGTH_SHORT).show();
                        // 刷新列表
                        performSearch();
                    } else {
                        Toast.makeText(NotesList.this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 搜索和筛选逻辑（保持不变）
    private void performSearch() {
        String keyword = etSearch.getText().toString().trim();
        Spinner spinnerCategory = findViewById(R.id.spinner_category);
        String selectedCategory = (String) spinnerCategory.getSelectedItem();
        loadNotesWithFilter(keyword, selectedCategory.equals("全部") ? null : selectedCategory);
    }

    private void loadNotesWithFilter(String keyword, String category) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        StringBuilder selection = new StringBuilder();
        ArrayList<String> selectionArgs = new ArrayList<>();

        if (!TextUtils.isEmpty(keyword)) {
            selection.append("(")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?)");
            selectionArgs.add("%" + keyword + "%");
            selectionArgs.add("%" + keyword + "%");
        }

        if (!TextUtils.isEmpty(category)) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
            selectionArgs.add(category);
        }

        cursor = getContentResolver().query(
                NotePad.Notes.CONTENT_URI,
                PROJECTION,
                selection.length() > 0 ? selection.toString() : null,
                selectionArgs.size() > 0 ? selectionArgs.toArray(new String[0]) : null,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        adapter.changeCursor(cursor);
    }

    // 生命周期方法（保持不变）
    @Override
    protected void onResume() {
        super.onResume();
        performSearch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    // 添加笔记菜单（保持不变）
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.list_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_note) {
            startActivity(new Intent(this, NoteEditor.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}