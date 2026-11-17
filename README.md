# NotePad 记事本实验功能扩展报告

## 一、 项目概述
本项目基于经典的 Android NotePad 示例进行功能扩展。在保留原有“新建、修改、删除”笔记的基础上，深度结合了现代移动端 App 的设计理念，扩展了 **数据时间戳展示、全局关键字搜索、待办事项（To-Do）独立模块、以及页面美化** 等功能，并完成了 UI 页面的卡片化与现代化排版重构。

## 二、 核心功能实现方案

### 1. 时间戳的存储与展示
*   **底层数据**：复用数据库原有的 `created` 和 `modified` 字段。
*   **查询与显示**：在 `NotesList` 的 `PROJECTION` 中加入 `COLUMN_NAME_MODIFICATION_DATE`。在自定义的 `CursorAdapter` 的 `bindView` 方法中，读取毫秒级时间戳，通过 `SimpleDateFormat` 将其格式化为 `yyyy-MM-dd HH:mm`，并显式设置时区为东八区（`Asia/Shanghai`），最终渲染在列表卡片的下方。

### 2. 笔记模糊查询（搜索）
*   **触发机制**：点击顶部菜单栏的搜索图标，弹出输入框。
*   **逻辑流转**：获取输入关键字后，通过 `Intent` 携带 `SEARCH_KEYWORD` 重新加载 `NotesList`。
*   **数据过滤**：在调用 `managedQuery` 时，动态拼接 SQL 语句 `title LIKE ? OR note LIKE ?`，并通过 `selectionArgs` 传入 `%关键字%` 实现模糊匹配。

### 3. 待办事项（To-Do）模块分离
*   **数据库升级**：在 `NotePad.java` 中新增 `COLUMN_NAME_IS_TODO` 常量；在 `NotePadProvider` 中将 `DATABASE_VERSION` 升至 `3`，建表语句加入 `is_todo INTEGER DEFAULT 0` 字段；同时将其注册进 `sNotesProjectionMap` 白名单中。
*   **多页面分流**：利用底部导航栏（`bottom_nav.xml`）切换 `MODE` 标记。
    *   普通笔记模式：查询条件追加 `is_todo = 0`。
    *   待办事项模式：查询条件追加 `is_todo IN (1, 2)`（1为未完成，2为已完成）。
*   **状态流转与交互**：为避免 `ListView` 嵌套 `CheckBox` 导致的视图复用错位问题，采用**点击整行（`onListItemClick`）弹出 Dialog** 的方式来进行状态切换。
*   **专属长按菜单**：重写 `onCreateContextMenu` 与 `onContextItemSelected`，在 `TODO` 模式下动态注入“修改标题”和“删除待办”功能，并利用 `info.id` 精准定位数据库记录。

### 4. 偏好设置与 UI 美化
*   **深色/浅色模式切换**：利用 `SharedPreferences` 持久化保存 `theme_mode` 状态。在 `onCreate` 的 `super.onCreate` 之前动态调用 `setTheme()` 切换 `Theme_Holo` 与 `Theme_Holo_Light`，并对 `ActionBar` 与 `Window` 底层背景进行同步着色。
*   **主题换肤（强调色）**：新增 `theme_color` 字段，通过顶部菜单选择颜色（绿、蓝、橙、紫）并持久化存储。全局的悬浮 `+` 按钮、底部导航栏高亮文字、以及列表时间戳均动态读取 `themeColor` 变量进行着色。
*   **列表卡片化**：使用 XML 的 `padding` 与背景层叠，并配合 `setBackgroundColor`，移除了原生的 `ListView` 分割线，实现了类似现代化 App 的圆角悬浮卡片效果。
*   **编辑器重构**：重构 `note_editor.xml`，将原本单一的输入框拆分为大号黑体标题框（`R.id.title`）与常规正文框（`R.id.note`）。同步重写 `NoteEditor.java` 中的 `onResume`、`onPause` 及 `updateNote` 逻辑，确保标题与正文分别、准确地存入数据库。

## 三、 开发难点与解决方案
1.  **数据库结构变更导致的崩溃**：修改 `Provider` 建表语句并升级版本号后，旧数据表缺少新字段会导致 `Cursor` 索引报错。通过在测试阶段彻底卸载旧版 App 并清理数据解决。
2.  **列表滑动导致的 View 复用错位**：在使用 `CursorAdapter` 绑定 `ListView` 时，游标指针在滑动期间是动态共享的。将点击事件直接绑定在视图中的控件（如 `CheckBox`）上极易引发数据错乱。解决方案是将交互逻辑统一交由 `onListItemClick` 处理，利用系统底层校验过的绝对 `id` 进行操作。

## 四、 实现效果展示
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



