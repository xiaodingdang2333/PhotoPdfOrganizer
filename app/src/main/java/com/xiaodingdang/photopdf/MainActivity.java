package com.xiaodingdang.photopdf;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_PICK_IMAGES = 1001;
    private static final int REQ_CAMERA = 1002;
    private static final int REQ_CAMERA_PERMISSION = 1003;

    private static final String GROUP_ALL = "全部";
    private static final String GROUP_DEFAULT = "默认";

    private final List<PdfItem> pdfItems = new ArrayList<>();
    private final Set<String> groups = new LinkedHashSet<>();
    private LinearLayout root;
    private LinearLayout list;
    private String currentGroup = GROUP_ALL;
    private Uri cameraUri;
    private File cameraFile;
    private SharedPreferences prefs;

    private final int blue = Color.rgb(11, 99, 206);
    private final int blue2 = Color.rgb(22, 132, 237);
    private final int bg = Color.rgb(244, 247, 251);
    private final int text = Color.rgb(24, 34, 48);
    private final int muted = Color.rgb(102, 112, 133);
    private final int line = Color.rgb(216, 226, 239);
    private final int soft = Color.rgb(238, 246, 255);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(blue);
        window.setNavigationBarColor(Color.WHITE);
        prefs = getSharedPreferences("photo_pdf_data", MODE_PRIVATE);
        loadState();
        buildUi();
        refreshList();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        setContentView(root);

        LinearLayout topbar = new LinearLayout(this);
        topbar.setOrientation(LinearLayout.HORIZONTAL);
        topbar.setGravity(Gravity.CENTER_VERTICAL);
        topbar.setPadding(dp(18), 0, dp(18), 0);
        topbar.setBackgroundColor(blue);
        root.addView(topbar, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView appIcon = new TextView(this);
        appIcon.setText("PDF");
        appIcon.setTextColor(blue);
        appIcon.setTextSize(11);
        appIcon.setTypeface(Typeface.DEFAULT_BOLD);
        appIcon.setGravity(Gravity.CENTER);
        appIcon.setBackground(round(Color.WHITE, Color.WHITE, dp(9), 0));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconLp.setMargins(0, 0, dp(12), 0);
        topbar.addView(appIcon, iconLp);

        TextView title = new TextView(this);
        title.setText("照片PDF整理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        topbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(26));
        scroll.addView(content);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row1);
        row1.addView(actionCard("📷", "拍照转PDF", "调用相机，单张照片生成PDF", true, v -> startCameraFlow()), cardWeight(true));
        row1.addView(actionCard("🖼", "相册多选转PDF", "多张图片合成一个PDF", true, v -> pickImages()), cardWeight(false));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, -2);
        row2Lp.setMargins(0, dp(10), 0, 0);
        content.addView(row2, row2Lp);
        row2.addView(actionCard("+", "新建分组", "合同、发票、资料等", false, v -> promptNewGroup()), cardWeight(true));
        row2.addView(actionCard("↕", "排序模式", "长按条目拖动排序", false, v -> toast("长按PDF条目后拖动排序")), cardWeight(false));

        TextView hint = new TextView(this);
        hint.setText("长按PDF条目后拖动可排序；每个条目可打开、分享、改名、分组或删除。");
        hint.setTextSize(12);
        hint.setTextColor(muted);
        hint.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(12), 0, dp(8));
        content.addView(hint, hintLp);

        HorizontalScrollView tabsWrap = new HorizontalScrollView(this);
        tabsWrap.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setTag("tabs");
        tabsWrap.addView(tabs);
        content.addView(tabsWrap);

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(-1, dp(34));
        sectionLp.setMargins(0, dp(4), 0, dp(6));
        content.addView(section, sectionLp);

        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("历史PDF");
        sectionTitle.setTextColor(text);
        sectionTitle.setTextSize(16);
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(0, -1, 1));

        TextView sortNote = new TextView(this);
        sortNote.setText("当前：自定义排序");
        sortNote.setTextSize(12);
        sortNote.setTextColor(muted);
        sortNote.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        section.addView(sortNote, new LinearLayout.LayoutParams(-2, -1));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
    }

    private LinearLayout actionCard(String icon, String title, String copy, boolean primary, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setClickable(true);
        card.setOnClickListener(listener);
        card.setBackground(primary ? gradient(blue, blue2, dp(14)) : round(Color.WHITE, line, dp(14), 1));

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(primary ? 22 : 24);
        iconView.setTextColor(primary ? Color.WHITE : blue);
        iconView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(iconView);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(primary ? Color.WHITE : blue);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        card.addView(titleView);

        TextView copyView = new TextView(this);
        copyView.setText(copy);
        copyView.setTextSize(11);
        copyView.setTextColor(primary ? Color.argb(220, 255, 255, 255) : muted);
        copyView.setMaxLines(2);
        card.addView(copyView);
        return card;
    }

    private LinearLayout.LayoutParams cardWeight(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(76), 1);
        lp.setMargins(left ? 0 : dp(5), 0, left ? dp(5) : 0, 0);
        return lp;
    }

    private void refreshTabs() {
        HorizontalScrollView wrap = (HorizontalScrollView) ((LinearLayout) ((ScrollView) root.getChildAt(1)).getChildAt(0)).getChildAt(3);
        LinearLayout tabs = (LinearLayout) wrap.getChildAt(0);
        tabs.removeAllViews();
        addTab(tabs, GROUP_ALL);
        for (String group : groups) addTab(tabs, group);
    }

    private void addTab(LinearLayout tabs, String name) {
        TextView tab = new TextView(this);
        tab.setText(name);
        tab.setGravity(Gravity.CENTER);
        tab.setTextSize(13);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        tab.setSingleLine(true);
        boolean active = name.equals(currentGroup);
        tab.setTextColor(active ? Color.WHITE : blue);
        tab.setBackground(round(active ? blue : Color.WHITE, active ? blue : line, dp(999), 1));
        tab.setPadding(dp(13), 0, dp(13), 0);
        tab.setClickable(true);
        tab.setOnClickListener(v -> {
            currentGroup = name;
            refreshList();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0, 0, dp(8), dp(8));
        tabs.addView(tab, lp);
    }

    private void refreshList() {
        refreshTabs();
        list.removeAllViews();
        List<PdfItem> visible = visibleItems();
        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("当前分组暂无PDF\n可以拍照或从相册多选照片生成。");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(15);
            empty.setLineSpacing(dp(4), 1.0f);
            empty.setTextColor(muted);
            empty.setBackground(round(Color.WHITE, Color.rgb(188, 210, 239), dp(18), 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(150));
            lp.setMargins(0, dp(20), 0, 0);
            list.addView(empty, lp);
            return;
        }
        for (PdfItem item : visible) addRow(item);
    }

    private List<PdfItem> visibleItems() {
        List<PdfItem> result = new ArrayList<>();
        for (PdfItem item : pdfItems) {
            if (GROUP_ALL.equals(currentGroup) || currentGroup.equals(item.group)) result.add(item);
        }
        return result;
    }

    private void addRow(PdfItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(13), dp(13), dp(13), dp(13));
        row.setBackground(round(Color.WHITE, line, dp(14), 1));
        row.setTag(item.id);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, 0, 0, dp(10));
        list.addView(row, rowLp);

        row.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText("pdf_id", item.id);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(data, new View.DragShadowBuilder(v), item.id, 0);
            } else {
                v.startDrag(data, new View.DragShadowBuilder(v), item.id, 0);
            }
            return true;
        });
        row.setOnDragListener((v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DROP && event.getLocalState() instanceof String) {
                reorder((String) event.getLocalState(), (String) v.getTag());
                return true;
            }
            return true;
        });

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(main);

        TextView thumb = new TextView(this);
        thumb.setText("PDF");
        thumb.setTextColor(blue);
        thumb.setTextSize(12);
        thumb.setTypeface(Typeface.DEFAULT_BOLD);
        thumb.setGravity(Gravity.CENTER);
        thumb.setBackground(round(Color.rgb(248, 252, 255), Color.rgb(199, 221, 248), dp(9), 1));
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(46), dp(58));
        thumbLp.setMargins(0, 0, dp(12), 0);
        main.addView(thumb, thumbLp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        main.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        TextView name = new TextView(this);
        name.setText(item.name);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(text);
        name.setSingleLine(true);
        info.addView(name);

        TextView meta = new TextView(this);
        meta.setText("分组：" + item.group + "    " + item.createdAt + "\n" + fileSize(item.path));
        meta.setTextSize(12);
        meta.setTextColor(muted);
        meta.setPadding(0, dp(5), 0, 0);
        meta.setLineSpacing(dp(2), 1.0f);
        info.addView(meta);

        LinearLayout ops = new LinearLayout(this);
        ops.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams opsLp = new LinearLayout.LayoutParams(-1, -2);
        opsLp.setMargins(0, dp(12), 0, 0);
        row.addView(ops, opsLp);
        addSmallButton(ops, "打开", v -> openPdf(item));
        addSmallButton(ops, "分享", v -> sharePdf(item));
        addSmallButton(ops, "改名", v -> renamePdf(item));
        addSmallButton(ops, "分组", v -> chooseGroup(item));

        LinearLayout ops2 = new LinearLayout(this);
        ops2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ops2Lp = new LinearLayout.LayoutParams(-1, -2);
        ops2Lp.setMargins(0, dp(6), 0, 0);
        row.addView(ops2, ops2Lp);
        addSmallButton(ops2, "上移", v -> move(item, -1));
        addSmallButton(ops2, "下移", v -> move(item, 1));
        addSmallButton(ops2, "删除", v -> deletePdf(item));
        addSmallButton(ops2, "排序", v -> toast("长按卡片拖动排序"));
    }

    private void addSmallButton(LinearLayout ops, String label, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(12);
        btn.setTextColor("删除".equals(label) ? Color.rgb(223, 43, 43) : Color.rgb(52, 64, 84));
        btn.setBackground(round(Color.rgb(251, 253, 255), line, dp(10), 1));
        btn.setClickable(true);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        ops.addView(btn, lp);
    }

    private void startCameraFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        openCamera();
    }

    private void openCamera() {
        try {
            cameraFile = new File(getCacheDir(), "camera_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (Exception e) {
            toast("无法打开相机：" + e.getMessage());
        }
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQ_PICK_IMAGES);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else if (requestCode == REQ_CAMERA_PERMISSION) {
            toast("需要相机权限才能拍照生成PDF");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQ_CAMERA && cameraUri != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(cameraUri);
            promptPdfNameAndCreate(uris);
        } else if (requestCode == REQ_PICK_IMAGES && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) promptPdfNameAndCreate(uris);
        }
    }

    private void promptPdfNameAndCreate(ArrayList<Uri> uris) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("照片PDF_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date()));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("PDF文件名")
                .setView(input)
                .setPositiveButton("生成", (d, w) -> createPdf(uris, input.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void createPdf(List<Uri> imageUris, String name) {
        if (name.length() == 0) name = "照片PDF";
        try {
            File dir = new File(getFilesDir(), "pdfs");
            if (!dir.exists()) dir.mkdirs();
            File out = uniqueFile(dir, sanitize(name), ".pdf");
            PdfDocument doc = new PdfDocument();
            int pageWidth = 595;
            int pageHeight = 842;
            int pageNo = 1;
            for (Uri uri : imageUris) {
                Bitmap bitmap = decodeScaledBitmap(uri);
                if (bitmap == null) continue;
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create();
                PdfDocument.Page page = doc.startPage(pageInfo);
                float scale = Math.min((pageWidth - 40f) / bitmap.getWidth(), (pageHeight - 40f) / bitmap.getHeight());
                float drawW = bitmap.getWidth() * scale;
                float drawH = bitmap.getHeight() * scale;
                android.graphics.RectF dest = new android.graphics.RectF(
                        (pageWidth - drawW) / 2f,
                        (pageHeight - drawH) / 2f,
                        (pageWidth + drawW) / 2f,
                        (pageHeight + drawH) / 2f);
                page.getCanvas().drawColor(Color.WHITE);
                page.getCanvas().drawBitmap(bitmap, null, dest, null);
                doc.finishPage(page);
                bitmap.recycle();
            }
            FileOutputStream fos = new FileOutputStream(out);
            doc.writeTo(fos);
            fos.close();
            doc.close();

            PdfItem item = new PdfItem();
            item.id = String.valueOf(System.currentTimeMillis());
            item.name = stripPdf(out.getName());
            item.path = out.getAbsolutePath();
            item.group = currentGroup.equals(GROUP_ALL) ? GROUP_DEFAULT : currentGroup;
            item.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
            groups.add(item.group);
            pdfItems.add(0, item);
            saveState();
            refreshList();
            toast("已生成：" + out.getName());
        } catch (Exception e) {
            toast("生成失败：" + e.getMessage());
        }
    }

    private Bitmap decodeScaledBitmap(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, bounds);
        if (in != null) in.close();
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (max / sample > 2200) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        InputStream in2 = getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(in2, null, opts);
        if (in2 != null) in2.close();
        return bitmap;
    }

    private void promptNewGroup() {
        EditText input = new EditText(this);
        input.setHint("例如：发票、合同、资料");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("新建分组")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.length() == 0) return;
                    groups.add(name);
                    currentGroup = name;
                    saveState();
                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void chooseGroup(PdfItem item) {
        List<String> choices = new ArrayList<>(groups);
        choices.add("新建分组...");
        new AlertDialog.Builder(this)
                .setTitle("选择分组")
                .setItems(choices.toArray(new String[0]), (d, which) -> {
                    if (which == choices.size() - 1) {
                        EditText input = new EditText(this);
                        input.setSingleLine(true);
                        new AlertDialog.Builder(this)
                                .setTitle("新建分组")
                                .setView(input)
                                .setPositiveButton("保存", (d2, w2) -> {
                                    String name = input.getText().toString().trim();
                                    if (name.length() == 0) return;
                                    groups.add(name);
                                    item.group = name;
                                    saveState();
                                    refreshList();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    } else {
                        item.group = choices.get(which);
                        saveState();
                        refreshList();
                    }
                })
                .show();
    }

    private void renamePdf(PdfItem item) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setText(item.name);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String next = input.getText().toString().trim();
                    if (next.length() == 0) return;
                    File oldFile = new File(item.path);
                    File nextFile = uniqueFile(oldFile.getParentFile(), sanitize(next), ".pdf");
                    if (oldFile.renameTo(nextFile)) {
                        item.name = stripPdf(nextFile.getName());
                        item.path = nextFile.getAbsolutePath();
                    } else {
                        item.name = next;
                    }
                    saveState();
                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deletePdf(PdfItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除PDF")
                .setMessage("确定删除“" + item.name + "”？")
                .setPositiveButton("删除", (d, w) -> {
                    new File(item.path).delete();
                    pdfItems.remove(item);
                    saveState();
                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openPdf(PdfItem item) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(item.path));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "打开PDF"));
        } catch (Exception e) {
            toast("没有可打开PDF的应用");
        }
    }

    private void sharePdf(PdfItem item) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(item.path));
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享PDF"));
    }

    private void reorder(String draggedId, String targetId) {
        if (draggedId.equals(targetId)) return;
        int from = findIndex(draggedId);
        int to = findIndex(targetId);
        if (from < 0 || to < 0) return;
        PdfItem item = pdfItems.remove(from);
        pdfItems.add(to, item);
        saveState();
        refreshList();
    }

    private void move(PdfItem item, int delta) {
        int index = pdfItems.indexOf(item);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= pdfItems.size()) return;
        Collections.swap(pdfItems, index, target);
        saveState();
        refreshList();
    }

    private int findIndex(String id) {
        for (int i = 0; i < pdfItems.size(); i++) {
            if (pdfItems.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private void loadState() {
        groups.clear();
        groups.add(GROUP_DEFAULT);
        pdfItems.clear();
        try {
            JSONArray groupArr = new JSONArray(prefs.getString("groups", "[]"));
            for (int i = 0; i < groupArr.length(); i++) groups.add(groupArr.getString(i));
            JSONArray arr = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                PdfItem item = new PdfItem();
                item.id = obj.optString("id");
                item.name = obj.optString("name");
                item.path = obj.optString("path");
                item.group = obj.optString("group", GROUP_DEFAULT);
                item.createdAt = obj.optString("createdAt");
                if (new File(item.path).exists()) {
                    pdfItems.add(item);
                    groups.add(item.group);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveState() {
        try {
            JSONArray groupArr = new JSONArray();
            for (String group : groups) groupArr.put(group);
            JSONArray arr = new JSONArray();
            for (PdfItem item : pdfItems) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("name", item.name);
                obj.put("path", item.path);
                obj.put("group", item.group);
                obj.put("createdAt", item.createdAt);
                arr.put(obj);
            }
            prefs.edit().putString("groups", groupArr.toString()).putString("items", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private GradientDrawable round(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private File uniqueFile(File dir, String base, String ext) {
        File file = new File(dir, base + ext);
        int i = 2;
        while (file.exists()) file = new File(dir, base + "_" + i++ + ext);
        return file;
    }

    private String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String stripPdf(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
    }

    private String fileSize(String path) {
        long len = new File(path).length();
        if (len > 1024 * 1024) return String.format(Locale.CHINA, "%.1f MB", len / 1024f / 1024f);
        return String.format(Locale.CHINA, "%.0f KB", len / 1024f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class PdfItem {
        String id;
        String name;
        String path;
        String group;
        String createdAt;
    }
}
