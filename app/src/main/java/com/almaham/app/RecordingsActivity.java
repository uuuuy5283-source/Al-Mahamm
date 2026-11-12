package com.almaham.app;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;

public class RecordingsActivity extends Activity {
 private MediaPlayer player;
 private File selected;
 private LinearLayout box;
 private TextView state;
 private boolean exporting = false;
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  if (b != null) {
   String name = b.getString("selected");
   if (name != null) try { selected = new File(RecordingFiles.directory(this), new File(name).getName()); } catch (Exception ignored) {}
  }
  ScrollView scroll = new ScrollView(this);
  box = new LinearLayout(this);
  box.setOrientation(LinearLayout.VERTICAL);
  box.setPadding(24, 48, 24, 24);
  box.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
  scroll.addView(box);
  setContentView(scroll);
  load();
 }
 private void load() {
  box.removeAllViews();
  state = new TextView(this);
  state.setText("تسجيلاتي — النسخ داخل التطبيق\nالتشغيل والمشاركة يعملان حتى لو فشل منتقي المجلد. الملفات الداخلية تُحذف فقط عند إزالة التطبيق أو مسح بياناته.");
  state.setTextSize(18);
  box.addView(state);
  Button stop = new Button(this); stop.setText("إيقاف التشغيل الصوتي"); stop.setOnClickListener(v -> stopPlayer()); box.addView(stop);
  try {
   File[] files = RecordingFiles.directory(this).listFiles((d, n) -> n.endsWith(".m4a") || n.endsWith(".3gp"));
   if (files == null || files.length == 0) { state.append("\nلا توجد تسجيلات محفوظة في هذه النسخة بعد."); return; }
   Arrays.sort(files, (a, c) -> Long.compare(c.lastModified(), a.lastModified()));
   for (File f : files) {
    if (RecordingService.active && f.getName().equals(Store.prefs(this).getString("current_file", ""))) continue;
    TextView title = new TextView(this); title.setText(f.getName() + " • " + Math.max(1, f.length() / 1024) + " KB"); box.addView(title);
    Button play = new Button(this); play.setText("تشغيل"); play.setOnClickListener(v -> play(f)); box.addView(play);
    Button share = new Button(this); share.setText("مشاركة أو حفظ في تطبيق آخر"); share.setOnClickListener(v -> share(f)); box.addView(share);
    Button save = new Button(this); save.setText("حفظ نسخة في مجلد…"); save.setOnClickListener(v -> export(f)); box.addView(save);
   }
  } catch (Exception e) { state.append("\n" + e.getMessage()); }
 }
 private void play(File file) {
  stopPlayer();
  try {
   player = new MediaPlayer();
   player.setDataSource(file.getAbsolutePath());
   player.setOnPreparedListener(p -> { p.start(); state.setText("تشغيل: " + file.getName()); });
   player.setOnCompletionListener(p -> stopPlayer());
   player.setOnErrorListener((p, w, e) -> { stopPlayer(); state.setText("تعذر تشغيل المقطع. إن كان قصيرًا جدًا سجّل مدة أطول."); return true; });
   player.prepareAsync();
  } catch (Exception e) { stopPlayer(); state.setText("تعذر تشغيل الملف: " + e.getMessage()); }
 }
 private void share(File file) {
  try {
   Uri uri = RecordingProvider.uriFor(file);
   Intent send = new Intent(Intent.ACTION_SEND);
   send.setType(file.getName().endsWith(".3gp") ? "audio/3gpp" : "audio/mp4");
   send.putExtra(Intent.EXTRA_STREAM, uri);
   send.putExtra(Intent.EXTRA_TITLE, file.getName());
   send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   send.setClipData(ClipData.newRawUri(file.getName(), uri));
   startActivity(Intent.createChooser(send, "مشاركة التسجيل"));
  } catch (Exception e) { state.setText("تعذر فتح المشاركة: " + e.getMessage()); }
 }
 private void export(File file) {
  if (exporting) return;
  if (RecordingService.active || RecordingService.saving) { state.setText("أوقف التسجيل وانتظر اكتمال الحفظ قبل التصدير"); return; }
  selected = file;
  try {
   Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*").putExtra(Intent.EXTRA_TITLE, file.getName());
   startActivityForResult(i, 61);
  } catch (Exception e) { state.setText("منتقي الحفظ غير متاح. استخدم زر المشاركة بدلًا منه.\n" + e.getMessage()); }
 }
 @Override protected void onActivityResult(int req, int res, Intent data) {
  super.onActivityResult(req, res, data);
  if (req != 61) return;
  try {
   if (res != RESULT_OK || data == null || data.getData() == null) { state.setText("تم إلغاء الحفظ. التسجيل داخل التطبيق لم يتغير."); return; }
   final File source = selected;
   final Uri uri = data.getData();
   if (source == null || !source.isFile()) { state.setText("اختر التسجيل من جديد. نسخته الداخلية محفوظة."); return; }
   exporting = true;
   state.setText("جارٍ النسخ…");
   new Thread(() -> {
    String message;
    try { RecordingFiles.copyTo(this, source, uri); message = "تم نسخ الملف:\n" + uri; }
    catch (Exception e) { message = "تعذر النسخ إلى المجلد المختار. الملف داخل التطبيق ما زال موجودًا. جرّب زر المشاركة.\n" + e.getClass().getSimpleName() + ": " + e.getMessage(); }
    final String feedback = message;
    runOnUiThread(() -> { exporting = false; if (!isDestroyed()) state.setText(feedback); });
   }, "export-recording").start();
  } catch (Exception e) {
   exporting = false;
   state.setText("تعذر إكمال الحفظ دون إغلاق التطبيق. الملف الداخلي محفوظ.\n" + e.getClass().getSimpleName());
  }
 }
 @Override protected void onSaveInstanceState(Bundle b) { if (selected != null) b.putString("selected", selected.getName()); super.onSaveInstanceState(b); }
 private void stopPlayer() { if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; } }
 @Override protected void onStop() { stopPlayer(); super.onStop(); }
}
