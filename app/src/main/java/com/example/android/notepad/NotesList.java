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

import com.example.android.notepad.NotePad;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends ListActivity {
    //主题颜色
    private int themeColor = android.graphics.Color.parseColor("#4CAF50");

    // For logging and debugging
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_IS_TODO
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;

    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        int currentTheme = prefs.getInt("theme_mode", 1);
        themeColor = prefs.getInt("theme_color", android.graphics.Color.parseColor("#4CAF50"));

        if (currentTheme == 1) {
            setTheme(android.R.style.Theme_Holo);
        } else {
            setTheme(android.R.style.Theme_Holo_Light_DarkActionBar);
        }
        super.onCreate(savedInstanceState);

        getListView().setDivider(null);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);

        /* Performs a managed query. The Activity handles closing and requerying the cursor
         * when needed.
         *
         * Please see the introductory note about performing provider operations on the UI thread.
         */

        String searchmode = getIntent().getStringExtra("SEARCHMODE");
        String mode=getIntent().getStringExtra("MODE");



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
                        doSearch(keyword); // 调用我们自己写的搜索方法
                        return true;
                    }
                    return false;
                }
            });
        }
        String selection = null;
        String[] selectionArgs = null;

        if (mode == null || "NOTE".equals(mode)) {

            selection = NotePad.Notes.COLUMN_NAME_IS_TODO + " = ?";
            selectionArgs = new String[]{"0"};
        } else {

            selection = NotePad.Notes.COLUMN_NAME_IS_TODO + " IN (?, ?)";
            selectionArgs = new String[]{"1", "2"};
        }

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

        /*
         * The following two arrays create a "map" between columns in the cursor and view IDs
         * for items in the ListView. Each element in the dataColumns array represents
         * a column name; each element in the viewID array represents the ID of a View.
         * The SimpleCursorAdapter maps them in ascending order to determine where each column
         * value will appear in the ListView.
         */

        // The names of the cursor columns to display in the view, initialized to the title column
        String[] dataColumns = { NotePad.Notes.COLUMN_NAME_TITLE ,NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE} ;

        // The view IDs that will display the cursor columns, initialized to the TextView in
        // noteslist_item.xml
        int[] viewIDs = { android.R.id.text1 , android.R.id.text2};

        // Creates the backing adapter for the ListView.
        SimpleCursorAdapter adapter
                = new MyCursorAdapter(
                this,                             // The Context for the ListView
                R.layout.noteslist_item,          // Points to the XML for a list item
                cursor,                           // The cursor to get items from
                dataColumns,
                viewIDs
        );

        // Sets the ListView's adapter to be the cursor adapter that was just created.
        setListAdapter(adapter);

        if (currentTheme == 1) {
            getListView().setBackgroundColor(android.graphics.Color.BLACK);
        } else {
            getListView().setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"));
        }


        final TextView fab = new TextView(this);
        fab.setText("+");
        fab.setTextSize(36);
        fab.setTextColor(android.graphics.Color.WHITE);
        fab.setGravity(android.view.Gravity.CENTER);


        android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
        bgShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bgShape.setColor(themeColor);
        fab.setBackground(bgShape);


        int size = 140;
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(size, size);
        params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        params.setMargins(0, 0, 40, 200);

        addContentView(fab, params);

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
        android.view.View bottomBar = android.view.LayoutInflater.from(this).inflate(R.layout.bottom_nav, null);


        android.widget.FrameLayout.LayoutParams params0 = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                150);
        params0.gravity = android.view.Gravity.BOTTOM;

        addContentView(bottomBar, params0);
        View emptyFooter = new View(this);
        emptyFooter.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                150
        ));
        getListView().addFooterView(emptyFooter);
        if (currentTheme == 1) {
            bottomBar.setBackgroundColor(Color.parseColor("#222222"));
        } else {
            bottomBar.setBackgroundColor(Color.parseColor("#E0E0E0"));
        }

        TextView btnAll = (TextView) bottomBar.findViewById(R.id.btn_all_notes);
        TextView btnTodo = (TextView) bottomBar.findViewById(R.id.btn_todo);

        if(mode==null||mode.equals("NOTE")){
            btnAll.setTextColor(themeColor);
            btnTodo.setTextColor(Color.parseColor("#999999"));
        }
        else{
            btnAll.setTextColor(Color.parseColor("#999999"));
            btnTodo.setTextColor(themeColor);
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            if (currentTheme == 1) {
                actionBar.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.parseColor("#222222")));
            } else {
                actionBar.setBackgroundDrawable(new ColorDrawable(themeColor));
            }
        }


        btnAll.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (mode == null||mode.equals("NOTE")) return;
                Intent intent = new Intent(NotesList.this, NotesList.class);
                intent.putExtra("MODE", "NOTE");
                startActivity(intent);
                finish();
            }
        });

        btnTodo.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (mode!=null&&mode.equals("TODO")) return;
                Intent intent = new Intent(NotesList.this, NotesList.class);
                intent.putExtra("MODE", "TODO");
                startActivity(intent);
                finish();
            }
        });
    }

    // 单独的待办弹窗方法
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

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
        menu.add(0, 666, 0, "切换深/浅模式");
        menu.add(0, 999, 0, "更换主题色");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // The paste menu item is enabled if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);


        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // If the clipboard contains an item, enables the Paste option on the menu.
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // If the clipboard is empty, disables the menu's Paste option.
            mPasteItem.setEnabled(false);
        }

        // Gets the number of notes currently being displayed.
        final boolean haveItems = getListAdapter().getCount() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {

            // This is the selected item.
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            Intent[] specifics = new Intent[1];

            // Sets the Intent in the array to be an EDIT action on the URI of the selected note.
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // Creates an array of menu items with one element. This will contain the EDIT option.
            MenuItem[] items = new MenuItem[1];

            // Creates an Intent with no specific action, using the URI of the selected note.
            Intent intent = new Intent(null, uri);

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                Menu.NONE,                  // A unique item ID is not required.
                Menu.NONE,                  // The alternatives don't need to be in order.
                null,                       // The caller's name is not excluded from the group.
                specifics,                  // These specific options must appear first.
                intent,                     // These Intent objects map to the options in specifics.
                Menu.NONE,                  // No flags are required.
                items                       // The menu items generated from the specifics-to-
                                            // Intents mapping
            );
                // If the Edit menu item exists, adds shortcuts for it.
                if (items[0] != null) {

                    // Sets the Edit menu item shortcut to numeric "1", letter "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // If the list is empty, removes any existing alternative actions from the menu
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        // Displays the menu
        return true;
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     *
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        if (item.getItemId() == R.id.menu_add) {
//            /*
//             * 显式启动 NoteEditor，并携带 ACTION_INSERT 和当前数据。
//             */
//            Intent intent = new Intent(this, NoteEditor.class);
//            intent.setAction(Intent.ACTION_INSERT);
//            intent.setData(getIntent().getData());
//            startActivity(intent);
//            return true;
//        } else
        if (item.getItemId() == R.id.menu_paste) {
            /*
             * 显式启动 NoteEditor，并携带 ACTION_PASTE 和当前数据。
             */
            Intent intent = new Intent(this, NoteEditor.class);
            intent.setAction(Intent.ACTION_PASTE);
            intent.setData(getIntent().getData());
            startActivity(intent);
            return true;
        } else if (item.getItemId()==R.id.menu_search) {//点击放大镜切换到搜索模式，重新加载页面
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


        } else if(item.getItemId()==666){
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
        return super.onOptionsItemSelected(item);
    }
    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        int realPosition = info.position - getListView().getHeaderViewsCount();
        if(realPosition<0)return;
        Cursor cursor = (Cursor) getListAdapter().getItem(realPosition);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }
        String title = cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));

        if ("TODO".equals(getIntent().getStringExtra("MODE"))) {
            menu.setHeaderTitle(title);
            menu.add(0, 1, 0, "修改");
            menu.add(0, 2, 0, "删除");
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(), 
                                        Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();

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
        if (id == R.id.context_open) {
            // 显式启动 NoteEditor，并携带 ACTION_EDIT 和笔记 URI
            Intent intent = new Intent(this, NoteEditor.class);
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(noteUri);
            startActivity(intent);
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // Gets a handle to the clipboard service.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // Copies the notes URI to the clipboard. In effect, this copies the note itself
            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            // Returns to the caller and skips further processing.
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // Deletes the note from the provider by passing in a URI in note ID format.
            // Please see the introductory note about performing provider operations on the
            // UI thread.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );

            // Returns to the caller and skips further processing.
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     *
     * This method handles incoming actions of either PICK (get data from the provider) or
     * GET_CONTENT (get or create data). If the incoming action is EDIT, this method sends a
     * new Intent to start NoteEditor.
     * @param l The ListView that contains the clicked item
     * @param v The View of the individual item
     * @param position The position of v in the displayed list
     * @param id The row ID of the clicked item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        int headerCount = getListView().getHeaderViewsCount();
        int footerCount = getListView().getFooterViewsCount();
        int totalCount = getListAdapter().getCount();
        if (position >= totalCount + headerCount) {
            return;
        }
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

        // Constructs a new URI from the incoming URI and the row ID
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // Gets the action from the incoming Intent
        String action = getIntent().getAction();

        // Handles requests for note data
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // Sets the result to return to the component that called this Activity. The
            // result contains the new URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // 显式启动 NoteEditor，并携带 ACTION_EDIT 和笔记 URI
            Intent intent = new Intent(this, NoteEditor.class);
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(uri);
            startActivity(intent);
        }
    }

    private class MyCursorAdapter extends SimpleCursorAdapter {
        public MyCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);
            android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            int currentTheme = prefs.getInt("theme_mode", 1);

            TextView timeView = (TextView) view.findViewById(android.R.id.text2);
            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            View listbackground = view.findViewById(R.id.listitem);
            CheckBox cbTodo = (CheckBox) view.findViewById(R.id.cb_todo);

            // 画背景
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(30f);
            if (currentTheme == 1) {
                text1.setTextColor(android.graphics.Color.WHITE);
                drawable.setColor(android.graphics.Color.parseColor("#222222"));
            } else {
                text1.setTextColor(android.graphics.Color.BLACK);
                drawable.setColor(Color.WHITE);
            }
            listbackground.setBackground(drawable);

            // 获取状态
            int isTodoCol = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_IS_TODO);
            int isTodo = (isTodoCol != -1) ? cursor.getInt(isTodoCol) : 0;

            // 文字加删除线
            if (isTodo == 2) {
                text1.setPaintFlags(text1.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                text1.setTextColor(android.graphics.Color.GRAY);
            } else {
                text1.setPaintFlags(text1.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                if (currentTheme == 1) {
                    text1.setTextColor(android.graphics.Color.WHITE);
                } else {
                    text1.setTextColor(android.graphics.Color.parseColor("#222222"));
                }
            }

            if (cbTodo != null) {
                cbTodo.setOnCheckedChangeListener(null);
                cbTodo.setOnClickListener(null);

                if (isTodo == 1 || isTodo == 2) {
                    cbTodo.setVisibility(View.VISIBLE);
                    cbTodo.setChecked(isTodo == 2);
                } else {
                    cbTodo.setVisibility(View.GONE);
                }

                //把时间戳转换成日期形式并展示
                timeView.setTextColor(android.graphics.Color.parseColor("#999999"));
                if (timeView != null) {
                    long timestamp = cursor.getLong(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
                    String timeString = sdf.format(new java.util.Date(timestamp));
                    timeView.setText(timeString);
                }
            }
        }
    }

}
