package com.xiaodingdang.photopdf;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

    private final List<PdfItem> pdfItems = new ArrayList<>();
    private final Set<String> groups = new LinkedHashSet<>();
    private LinearLayout root;
    private LinearLayout list;
    private String currentGroup = "全部";
    private Uri cameraUri;
    private File cameraFile;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("photo_pdf_data", MODE_PRIVATE);
        loadState();
        buildUi();
        refreshList();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("照片PDF整理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(18), 0, dp(18), 0);
        title.setBackgroundColor(Color.rgb(11, 99, 206));
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(12), dp(12), dp(12), dp(8));
        actions.setGravity(Gravity.CENTER);
        root.addView(actions);

        Button camera = primaryButton("拍照转PDF");
        camera.setOnClickListener(v -> startCameraFlow());
        actions.addView(camera, weightParams());

        Button album = primaryButton("相册多选转PDF");
        album.setOnClickListener(v -> pickImages());
        actions.addView(album, weightParams());

        Button group = primaryButton("新建分组");
        group.setOnClickListener(v -> promptNewGroup());
        actions.addView(group, weightParams());

        TextView hint = new TextView(this);
        hint.setText("长按PDF条目后拖动可排序；每个条目可打开、分享、改名、分组或删除。");
        hint.setTextSize(13);
        hint.setTextColor(Color.rgb(90, 96, 105));
        hint.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(hint);

        HorizontalScrollView tabsWrap = new HorizontalScrollView(this);
        tabsWrap.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), 0, dp(12), dp(8));
        tabs.setTag("tabs");
        tabsWrap.addView(tabs);
        root.addView(tabsWrap);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), 0, dp(12), dp(24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setBackgroundColor(Color.rgb(11, 99, 206));
        return b;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    private void refreshTabs() {
        HorizontalScrollView wrap = (HorizontalScrollView) root.getChildAt(3);
        LinearLayout tabs = (LinearLayout) wrap.getChildAt(0);
        tabs.removeAllViews();
        addTab(tabs, "全部");
        for (String group : groups) addTab(tabs, group);
    }

    private void addTab(LinearLayout tabs, String name) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(name.equals(currentGroup) ? Color.WHITE : Color.rgb(11, 99, 206));
        b.setBackgroundColor(name.equals(currentGroup) ? Color.rgb(11, 99, 206) : Color.WHITE);
        b.setOnClickListener(v -> {
            currentGroup = name;
            refreshList();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0, 0, dp(8), 0);
        tabs.addView(b, lp);
    }

    private void refreshList() {
        refreshTabs();
        list.removeAllViews();
        List<PdfItem> visible = visibleItems();
        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无PDF。可以拍照生成，或一次导入多张照片生成一个PDF。");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(16);
            empty.setTextColor(Color.rgb(90, 96, 105));
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
            return;
        }
        for (PdfItem item : visible) addRow(item);
    }

    private List<PdfItem> visibleItems() {
        List<PdfItem> result = new ArrayList<>();
        for (PdfItem item : pdfItems) {
            if ("全部".equals(currentGroup) || currentGroup.equals(item.group)) result.add(item);
        }
        return result;
    }

    private void addRow(PdfItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackgroundColor(Color.WHITE);
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

        TextView name = new TextView(this);
        name.setText(item.name);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Color.rgb(24, 34, 48));
        row.addView(name);

        TextView meta = new TextView(this);
        meta.setText("分组：" + item.group + "    " + item.createdAt + "    " + fileSize(item.path));
        meta.setTextSize(12);
        meta.setTextColor(Color.rgb(100, 108, 118));
        meta.setPadding(0, dp(4), 0, dp(8));
        row.addView(meta);

        LinearLayout ops = new LinearLayout(this);
        ops.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(ops);
        addSmallButton(ops, "打开", v -> openPdf(item));
        addSmallButton(ops, "分享", v -> sharePdf(item));
        addSmallButton(ops, "改名", v -> renamePdf(item));
        addSmallButton(ops, "分组", v -> chooseGroup(item));
        addSmallButton(ops, "上移", v -> move(item, -1));
        addSmallButton(ops, "下移", v -> move(item, 1));
        addSmallButton(ops, "删除", v -> deletePdf(item));
    }

    private void addSmallButton(LinearLayout ops, String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        ops.addView(b, lp);
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
            item.group = currentGroup.equals("全部") ? "默认" : currentGroup;
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
                        saveState();
                        refreshList();
                    } else {
                        item.name = next;
                        saveState();
                        refreshList();
                    }
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
        groups.add("默认");
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
                item.group = obj.optString("group", "默认");
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

    private File uniqueFile(File dir, String base, String ext) {
        File file = new File(dir, base + ext);
        int i = 2;
        while (file.exists()) file = new File(dir, base + "_" + i++ + ext);
        return file;
    }

    private String sanitize(String text) {
        return text.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String stripPdf(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
    }

    private String fileSize(String path) {
        long len = new File(path).length();
        if (len > 1024 * 1024) return String.format(Locale.CHINA, "%.1f MB", len / 1024f / 1024f);
        return String.format(Locale.CHINA, "%.0f KB", len / 1024f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class PdfItem {
        String id;
        String name;
        String path;
        String group;
        String createdAt;
    }
}
