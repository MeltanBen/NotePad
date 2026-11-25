# NotePad 记事本实验功能扩展报告

## 一、 项目概述
本项目基于经典的 Android NotePad 示例进行功能扩展。在保留原有“新建、修改、删除”笔记的基础上，深度结合了现代移动端 App 的设计理念，实现了基本的 **时间戳展示、笔记查询** ，并扩展了 **待办事项（To-Do）独立模块、以及页面美化** 等功能，并完成了 UI 页面的卡片化与现代化排版重构。

## 二、 基本功能实现

### 1. 时间戳的展示
*   **底层数据**：复用数据库原有的 `modified` 字段。
*   **查询与显示**：在 `NotesList` 的 `PROJECTION` 中加入 `COLUMN_NAME_MODIFICATION_DATE`。在自定义的 `CursorAdapter` 的 `bindView` 方法中，读取毫秒级时间戳，通过 `SimpleDateFormat` 将其格式化为 `yyyy-MM-dd HH:mm`，并显式设置时区为东八区（`Asia/Shanghai`），最终渲染在列表卡片的下方。

相关代码：
```java
//把时间戳转换成日期形式并展示
timeView.setTextColor(android.graphics.Color.parseColor("#999999"));
if (timeView != null) {
    long timestamp = cursor.getLong(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
    sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
    String timeString = sdf.format(new java.util.Date(timestamp));
    timeView.setText(timeString);
}
```
实现效果：


### 2. 笔记模糊查询（搜索）
*   **页面修改**：修改`list_option_menu`，加入了顶部菜单栏的搜索图标，点击搜索按钮进入搜索模式，重新加载页面弹出输入框。
*   **逻辑流转**：输入关键字按回车键触发搜索，后台监听回车触发`doSearch`方法，获取输入关键字后，通过 `Intent` 携带 `SEARCH_KEYWORD` 重新加载 `NotesList`。
*   **数据过滤**：修改`onCreate`，在调用 `managedQuery` 时，动态拼接 SQL 语句 `title LIKE ? OR note LIKE ?`，并通过 `selectionArgs` 传入 `%关键字%` 实现模糊匹配。

相关代码：

1. 搜索按钮响应
```java
else if (item.getItemId()==R.id.menu_search) {//点击放大镜切换到搜索模式，重新加载页面
    String mode = getIntent().getStringExtra("SEARCHMODE");
    Intent intent = new Intent(this, NotesList.class);
    if(mode==null||!mode.equals("SEARCH")){
        intent.putExtra("SEARCHMODE","SEARCH");
    }
    else{
        intent.putExtra("SEARCHMODE","NOTE");
    }
    startActivity(intent);
    finish();
}
```
2. 页面加入输入框
```java
if(searchmode!=null&&searchmode.equals("SEARCH")){
    //添加搜索栏
    android.widget.EditText searchInput = new android.widget.EditText(this);
    searchInput.setHint("在此输入关键字搜索...");
    searchInput.setSingleLine(true);

    getListView().addHeaderView(searchInput, null, false);

    searchInput.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {

                String keyword = v.getText().toString().trim();
                doSearch(keyword); // 调用自己写的搜索方法
                return true;
            }
            return false;
        }
    });
}
```
3. 搜索方法
```java
//执行搜索方法，把keyword传入下一个页面进行查询
public void doSearch(String keyword){

    Intent intent = new Intent(this, NotesList.class);
    if (keyword != null && !keyword.isEmpty()) {
        intent.putExtra("SEARCH_KEYWORD", keyword);
        intent.putExtra("SEARCHMODE", "SEARCH");
    }

    startActivity(intent);
    finish();
}
```
4. 数据过滤
```java
String keyword = getIntent().getStringExtra("SEARCH_KEYWORD");//拿到keyword进行查询
if (keyword != null && !keyword.isEmpty()) {

    selection = selection + " AND ("
            + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR "
            + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)";

    String[] searchArgs = new String[]{"%" + keyword + "%", "%" + keyword + "%"};
    String[] newArgs = new String[selectionArgs.length + searchArgs.length];
    System.arraycopy(selectionArgs, 0, newArgs, 0, selectionArgs.length);
    System.arraycopy(searchArgs, 0, newArgs, selectionArgs.length, searchArgs.length);
    selectionArgs = newArgs;
}

Cursor cursor = managedQuery(
        getIntent().getData(),            // Use the default content URI for the provider.
        PROJECTION,                       // Return the note ID and title for each note.
        selection,                             // No where clause, return all records.
        selectionArgs,                             // No where clause, therefore no where column values.
        NotePad.Notes.DEFAULT_SORT_ORDER  // Use the default sort order.
);
```

## 三、 拓展功能实现

### 1. 待办事项（To-Do）模块
*   **数据库升级**：在 `NotePad.java` 中新增 `COLUMN_NAME_IS_TODO` 常量；在 `NotePadProvider` 中将 `DATABASE_VERSION` 升至 `3`，建表语句加入 `is_todo INTEGER DEFAULT 0` 字段；同时将其注册进 `sNotesProjectionMap` 白名单中。
*  **列表项修改**：复用笔记的列表项，加上复选框，笔记页面不显示复选框代办页面显示。
*  **多页面分流**：利用底部导航栏（`bottom_nav.xml`）切换 `MODE` 标记，并重新加载页面来区分代办栏和笔记栏。
    *   普通笔记模式：查询条件追加 `is_todo = 0`。
    *   待办事项模式：查询条件追加 `is_todo IN (1, 2)`（1为未完成，2为已完成）。
*  **新增代办项**：点击新增按钮，弹出新增代办对话框。
*  **修改状态**：点击列表项弹出是否完成对话框，点击确认可以切换该代办事项状态，同时修改数据库。
*  **长按状态**：长按弹出修改/删除。

相关代码：

1. 笔记和代办区分
```java
if (mode == null || "NOTE".equals(mode)) {

    selection = NotePad.Notes.COLUMN_NAME_IS_TODO + " = ?";
    selectionArgs = new String[]{"0"};
} else {

    selection = NotePad.Notes.COLUMN_NAME_IS_TODO + " IN (?, ?)";
    selectionArgs = new String[]{"1", "2"};
}
```

2. 新增区分
```java
fab.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        if(mode==null||mode.equals("NOTE")){
            Intent intent = new Intent(NotesList.this, NoteEditor.class);
            intent.setAction(Intent.ACTION_INSERT);
            intent.setData(getIntent().getData());
            startActivity(intent);
        }
        else{
            showAddTodoDialog();

        }

    }
});
```

3. 新增对话框
```java
private void showAddTodoDialog() {
    final android.widget.EditText input = new android.widget.EditText(this);
    input.setHint("请输入待办事项...");
    input.setPadding(40, 30, 40, 30);

    new android.app.AlertDialog.Builder(this)
            .setTitle("新建待办")
            .setView(input)
            .setPositiveButton("添加", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String todoText = input.getText().toString().trim();
                    if (!todoText.isEmpty()) {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(NotePad.Notes.COLUMN_NAME_TITLE, todoText);
                        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "待办事项：请尽快完成。");
                        values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 1); // 1 代表待办

                        long now = System.currentTimeMillis();
                        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
                        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);

                        getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);

                        // 刷新当前页面
                        startActivity(getIntent());
                        finish();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
}
```
4. 长按修改/删除
```java
if ("TODO".equals(getIntent().getStringExtra("MODE"))) {

    if (id == 1) {

        Cursor c = getContentResolver().query(noteUri, new String[]{NotePad.Notes.COLUMN_NAME_TITLE}, null, null, null);
        String oldTitle = "";
        if (c != null && c.moveToFirst()) {
            oldTitle = c.getString(0);
            c.close();
        }

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(oldTitle);
        input.setSelection(oldTitle.length());

        new android.app.AlertDialog.Builder(this)
                .setTitle("修改待办")
                .setView(input)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String newTitle = input.getText().toString().trim();
                        if (!newTitle.isEmpty()) {
                            android.content.ContentValues values = new android.content.ContentValues();
                            values.put(NotePad.Notes.COLUMN_NAME_TITLE, newTitle);

                            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

                            getContentResolver().update(noteUri, values, null, null);
                            recreate();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        return true;

    } else if (id == 2) {

        new android.app.AlertDialog.Builder(this)
                .setTitle("删除待办")
                .setMessage("确定要删除这条待办吗？")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        getContentResolver().delete(noteUri, null, null);
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        return true;
    }
}
```
5. 状态修改
```java
if(getIntent().getStringExtra("MODE")!=null&&getIntent().getStringExtra("MODE").equals("TODO")){
    new android.app.AlertDialog.Builder(this)
            .setTitle("更改状态")
            .setMessage("是否将这个代办标记为【已完成】/【未完成】？")
            .setPositiveButton("切换状态", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {

                    android.net.Uri noteUri = android.content.ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id);

                    android.database.Cursor c = getContentResolver().query(noteUri, new String[]{NotePad.Notes.COLUMN_NAME_IS_TODO}, null, null, null);
                    int currentStatus = 1;
                    if (c != null && c.moveToFirst()) {
                        currentStatus = c.getInt(0);
                        c.close();
                    }

                    // 如果原来是 1 就变 2，是 2 就变 1
                    int newStatus = (currentStatus == 1) ? 2 : 1;

                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, newStatus);
                    getContentResolver().update(noteUri, values, null, null);

                    recreate(); // 刷新页面
                }
            })
            .setNegativeButton("取消", null)
            .show();
    return; // 结束，不往下执行跳转编辑页面的代码

}
```
页面展示：

### 2. 偏好设置与 UI 美化
*   **深色/浅色模式切换**：利用 `SharedPreferences` 持久化保存 `theme_mode` 状态。在 `onCreate` 的 `super.onCreate` 之前动态调用 `setTheme()` 切换 `Theme_Holo` 与 `Theme_Holo_Light`，并对 `ActionBar` 与 `Window` 底层背景进行同步着色。
*   **主题换肤（强调色）**：新增 `theme_color` 字段，通过顶部菜单选择颜色（绿、蓝、橙、紫）并持久化存储。全局的悬浮 `+` 按钮、底部导航栏高亮文字、以及列表时间戳均动态读取 `themeColor` 变量进行着色。
*   **列表卡片化**：使用 XML 的 `padding` 与背景层叠，并配合 `setBackgroundColor`，移除了原生的 `ListView` 分割线，实现了类似现代化 App 的圆角悬浮卡片效果。
*   **编辑器重构**：重构 `note_editor.xml`，将原本单一的输入框拆分为大号黑体标题框（`R.id.title`）与常规正文框（`R.id.note`）。同步重写 `NoteEditor.java` 中的 `onResume`、`onPause` 及 `updateNote` 逻辑，确保标题与正文分别、准确地存入数据库。如果用户没有指定标题，默认选择正文的前几个字作为标题。

代码实现：
1. note_editor.xml
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <EditText
        android:id="@+id/title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@null"
        android:hint="标题"
        android:textSize="28sp"
        android:textStyle="bold"
        android:maxLines="2"
        android:paddingBottom="16dp" />

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:layout_marginBottom="16dp"/>

    <EditText
        android:id="@+id/note"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@null"
        android:gravity="top|start"
        android:hint="开始输入内容..."
        android:textSize="16sp"
        android:lineSpacingExtra="6dp" />

</LinearLayout>
```
2. 主题选择框
```java
else if(item.getItemId()==666){
    android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
    int currentTheme = prefs.getInt("theme_mode", 1);
    int newTheme = (currentTheme == 1) ? 0 : 1;
    prefs.edit().putInt("theme_mode", newTheme).apply();

    recreate();
    return true;

}
else if (item.getItemId() == 999) {
    final String[] colorNames = {"默认绿", "天空蓝", "活力橙", "紫罗兰"};
    final int[] colorValues = {
            android.graphics.Color.parseColor("#4CAF50"),
            android.graphics.Color.parseColor("#2196F3"),
            android.graphics.Color.parseColor("#FF9800"),
            android.graphics.Color.parseColor("#9C27B0")
    };

    new android.app.AlertDialog.Builder(this)
            .setTitle("选择主题颜色")
            .setItems(colorNames, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                    prefs.edit().putInt("theme_color", colorValues[which]).apply();
                    recreate(); // 刷新页面
                }
            })
            .show();
    return true;
}
```
3. 颜色变化
```java
android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
int currentTheme = prefs.getInt("theme_mode", 1);
themeColor = prefs.getInt("theme_color", android.graphics.Color.parseColor("#4CAF50"));

if (currentTheme == 1) {
    setTheme(android.R.style.Theme_Holo);
} else {
    setTheme(android.R.style.Theme_Holo_Light_DarkActionBar);
}
```
页面展示：

## 四、 开发难点与解决方案
1.  **数据库结构变更导致的崩溃**：修改 `Provider` 建表语句并升级版本号后，旧数据表缺少新字段会导致 `Cursor` 索引报错。通过在测试阶段彻底卸载旧版 App 并清理数据解决。
2.  **列表滑动导致的 View 复用错位**：在使用 `CursorAdapter` 绑定 `ListView` 时，游标指针在滑动期间是动态共享的。将点击事件直接绑定在视图中的控件（如 `CheckBox`）上极易引发数据错乱。解决方案是将交互逻辑统一交由 `onListItemClick` 处理，利用系统底层校验过的绝对 `id` 进行操作。

## 五、 实现效果展示
1. 普通页面
   
   <img width="614" height="1144" alt="屏幕截图 2026-11-17 111022" src="https://github.com/user-attachments/assets/6301ea09-d6e1-47c3-8dd3-b7977c330d60" />
   
2. 代办页面
   
   <img width="612" height="1144" alt="屏幕截图 2026-11-17 111237" src="https://github.com/user-attachments/assets/77ba477f-858c-4243-8ae2-5c79e26ec536" />
   
3. 编辑页面
   
   <img width="620" height="1156" alt="屏幕截图 2026-11-17 111212" src="https://github.com/user-attachments/assets/aeefe00e-6504-4644-bb5e-7d886259bfac" />
   
4. 切换模式
   
   <img width="614" height="400" alt="屏幕截图 2026-11-17 111127" src="https://github.com/user-attachments/assets/5f9eca87-2be8-4efa-8069-bb81d46e4de0" /><img width="578" height="530" alt="屏幕截图 2026-11-17 111154" src="https://github.com/user-attachments/assets/69ec8014-ead0-48eb-9644-7300cded5640" />
   
5. 夜间模式
   
   <img width="606" height="1128" alt="屏幕截图 2026-11-17 111137" src="https://github.com/user-attachments/assets/628d1ef4-bce4-4ffe-8e9e-9e4cbc5f0da2" />



