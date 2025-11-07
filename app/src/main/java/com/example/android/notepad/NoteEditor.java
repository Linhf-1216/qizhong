package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class NoteEditor extends Activity {
    private Spinner spinnerCategory;
    private String[] categories = {"默认分类", "工作", "生活", "学习", "其他"};
    private Button btnSave;
    private EditText etTitle, etContent;
    private Uri currentUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        etTitle = findViewById(R.id.et_note_title);
        etContent = findViewById(R.id.et_note_content);
        btnSave = findViewById(R.id.btn_save);
        spinnerCategory = findViewById(R.id.spinner_category);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        currentUri = getIntent().getData();
        if (currentUri != null) {
            loadNoteData();
        }

        btnSave.setOnClickListener(v -> saveNote());
    }

    private void loadNoteData() {
        Cursor cursor = getContentResolver().query(
                currentUri,
                null,
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            String title = cursor.getString(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_NOTE));
            String category = cursor.getString(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_CATEGORY));

            etTitle.setText(title);
            etContent.setText(content);

            // 设置分类选中项
            for (int i = 0; i < categories.length; i++) {
                if (categories[i].equals(category)) {
                    spinnerCategory.setSelection(i);
                    break;
                }
            }
            cursor.close();
        }
    }

    private void saveNote() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, content);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, spinnerCategory.getSelectedItem().toString());

        if (currentUri == null) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATED_TIME, System.currentTimeMillis());
            currentUri = getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
        } else {
            getContentResolver().update(currentUri, values, null, null);
        }

        getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);

        if (currentUri != null) {
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    // 编辑页删除确认
    private void deleteNote() {
        new AlertDialog.Builder(this)
                .setTitle("删除笔记")
                .setMessage("确定要删除这条笔记吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (currentUri != null) {
                        // 执行删除（使用当前笔记的Uri）
                        int rowsDeleted = getContentResolver().delete(currentUri, null, null);
                        if (rowsDeleted > 0) {
                            getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
                            Toast.makeText(NoteEditor.this, "删除成功", Toast.LENGTH_SHORT).show();
                            finish(); // 关闭编辑页，返回列表
                        } else {
                            Toast.makeText(NoteEditor.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.note_editor_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            saveNote();
            return true;
        } else if (id == R.id.menu_delete) {
            // 只有编辑已有笔记时才显示删除按钮（新增笔记无需删除）
            if (currentUri != null) {
                deleteNote(); // 调用删除方法
            } else {
                Toast.makeText(this, "新增笔记无需删除", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}