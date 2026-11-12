package com.almaham.app;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {
 public static final String START = "com.almaham.START", STOP = "com.almaham.STOP";
 public static volatile boolean active = false, saving = false;
 public static volatile long started = 0;
 public static volatile int number = 0;
 private MediaRecorder recorder;
 private FileOutputStream output;
 private PowerManager.WakeLock wake;
 private AudioFocusRequest focus;
 private File internal;
 private String filename;
 private boolean finishing = false;

 @Override public IBinder onBind(Intent i) { return null; }

 @Override public int onStartCommand(Intent intent, int flags, int startId) {
  if (intent != null && STOP.equals(intent.getAction())) {
   if (active || recorder != null) finishRecording();
   else stopSelf();
   return START_NOT_STICKY;
  }
  if (saving || active || finishing) return START_NOT_STICKY;
  if (intent == null || !START.equals(intent.getAction())) {
   stopSelf();
   return START_NOT_STICKY;
  }
  if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
   fail("لم يُمنح إذن الميكروفون. افتح التطبيق واسمح به ثم أعد المحاولة.", false);
   return START_NOT_STICKY;
  }
  try {
   number = Store.last(this) + 1;
   Notification notice = Notices.recording(this, number, System.currentTimeMillis());
   if (Build.VERSION.SDK_INT >= 29) startForeground(1, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
   else startForeground(1, notice);
  } catch (Exception e) {
   Log.e("AlMahamm", "foreground start failed", e);
   fail("تعذر بدء خدمة التسجيل. افتح التطبيق واضغط بدء التسجيل من الشاشة. " + text(e), false);
   return START_NOT_STICKY;
  }
  try {
   beginRecording();
  } catch (Exception e) {
   Log.e("AlMahamm", "recording start failed", e);
   abort("تعذر بدء التسجيل. " + text(e) + " تأكد من إذن الميكروفون وأن تطبيقًا آخر لا يستخدمه.");
  }
  return START_NOT_STICKY;
 }

 private void beginRecording() throws Exception {
  String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
  File dir = new File(getFilesDir(), "recordings");
  if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("تعذر إنشاء مجلد التسجيلات داخل التطبيق");
  String lastError = "الميكروفون رفض التسجيل";
  boolean ok = false;
  File failed = null;
  int[] sources = {MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT, MediaRecorder.AudioSource.MIC};
  boolean[] aac = {true, true, true, false};
  boolean[] fd = {false, true, true, true};
  for (int i = 0; i < sources.length; i++) {
   closeFailedAttempt();
   if (failed != null && failed.exists()) failed.delete();
   filename = String.format(Locale.US, "Al-Maham_%04d_%s%s", number, stamp, aac[i] ? ".m4a" : ".3gp");
   internal = new File(dir, filename);
   failed = internal;
   if (internal.exists()) internal.delete();
   if (fd[i]) output = new FileOutputStream(internal);
   try {
    if (startRecorder(sources[i], aac[i], fd[i])) { ok = true; failed = null; break; }
   } catch (Exception e) {
    lastError = text(e);
    Log.w("AlMahamm", "attempt " + i + " failed", e);
   }
  }
  if (!ok) {
   closeFailedAttempt();
   if (internal != null && internal.exists()) internal.delete();
   throw new IOException(lastError);
  }
  requestFocus();
  try {
   wake = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlMaham:ManualRecording");
   wake.setReferenceCounted(false);
   wake.acquire(30L * 60L * 1000L);
  } catch (Exception wakeFailure) {
   Log.w("AlMahamm", "wake lock unavailable; recording continues", wakeFailure);
  }
  started = SystemClock.elapsedRealtime();
  active = true;
  Store.prefs(this).edit().putString("current_file", filename).putString("status", "جارٍ تسجيل مقطع جديد").apply();
  Notices.shortcuts(this);
 }

 private boolean startRecorder(int source, boolean aac, boolean useFd) throws Exception {
  recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
  recorder.setAudioSource(source);
  if (aac) {
   recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
   recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
   recorder.setAudioChannels(1);
   recorder.setAudioSamplingRate(44100);
   recorder.setAudioEncodingBitRate(128000);
  } else {
   recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
   recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
  }
  if (useFd) recorder.setOutputFile(output.getFD());
  else recorder.setOutputFile(internal.getAbsolutePath());
  recorder.prepare();
  recorder.start();
  return true;
 }

 private void finishRecording() {
  if (finishing) return;
  finishing = true;
  active = false;
  Exception stopError = null;
  try {
   if (recorder != null) recorder.stop();
  } catch (Exception e) {
   stopError = e;
   Log.w("AlMahamm", "recorder stop", e);
  }
  releaseRecorder();
  abandonFocus();
  releaseWake();
  if (internal == null || !internal.isFile() || internal.length() < 64) {
   deletePartial();
   String why = stopError == null ? "الميكروفون لم يكتب صوتًا. سجّل ثانيتين على الأقل ثم أوقف." : "لم يكتمل التسجيل: " + text(stopError);
   fail("لم يُحفظ التسجيل. " + why, true);
   return;
  }
  saving = true;
  final Context app = getApplicationContext();
  final File file = internal;
  final int num = number;
  final String name = filename;
  Notices.info(this, 2, "جارٍ حفظ التسجيل رقم " + num + "…");
  new Thread(() -> persist(app, file, num, name), "recording-save").start();
 }

 private void persist(Context app, File file, int num, String name) {
  String uri = Uri.fromFile(file).toString();
  boolean external = false;
  StringBuilder notes = new StringBuilder();
  String folder = Store.folder(app);
  if (!folder.isEmpty()) {
   try {
    Uri tree = Uri.parse(folder);
    Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    Uri doc = DocumentsContract.createDocument(app.getContentResolver(), parent, mime(name), name);
    if (doc == null) throw new IOException("تعذر إنشاء الملف في المجلد");
    RecordingFiles.copyTo(app, file, doc);
    uri = doc.toString();
    external = true;
    notes.append("في المجلد المختار");
   } catch (Exception e) {
    Log.w("AlMahamm", "folder copy failed", e);
    notes.append("تعذر المجلد (").append(text(e)).append(")");
   }
  }
  if (!external) {
   String copied = copyPublic(app, file, name, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "Music/Al-Mahamm");
   if (copied == null && Build.VERSION.SDK_INT >= 29) {
    copied = copyPublic(app, file, name, MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS + "/Al-Mahamm");
   }
   if (copied != null) {
    uri = copied;
    external = true;
    if (notes.length() > 0) notes.append("\n");
    notes.append(copied.contains("audio") ? "في Music/Al-Mahamm" : "في Downloads/Al-Mahamm");
   } else if (notes.length() > 0) notes.append("\nتعذر النسخ إلى Music وDownloads. النسخة الداخلية محفوظة.");
   else notes.append("تعذر النسخ إلى Music وDownloads. النسخة الداخلية محفوظة.");
  }
  boolean stored = Store.saved(app, num, name, uri);
  String message = "تم حفظ التسجيل رقم " + num + " داخل التطبيق (شاشة تسجيلاتي)"
    + (external ? " و" + notes : ".\n" + notes);
  if (!stored) message = "الملف موجود في تسجيلاتي لكن تعذر تحديث العداد.\n" + message;
  Store.prefs(app).edit().putString("status", message).remove("current_file").apply();
  Notices.info(app, 2, message);
  new Handler(Looper.getMainLooper()).post(() -> {
   saving = false;
   try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
   Notices.shortcuts(app);
   try { stopSelf(); } catch (Exception ignored) {}
  });
 }

 private static String copyPublic(Context app, File file, String name, Uri collection, String relative) {
  if (Build.VERSION.SDK_INT < 29) return null;
  Uri dest = null;
  try {
   ContentValues values = new ContentValues();
   values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
   values.put(MediaStore.MediaColumns.MIME_TYPE, mime(name));
   values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
   values.put(MediaStore.MediaColumns.IS_PENDING, 1);
   dest = app.getContentResolver().insert(collection, values);
   if (dest == null) return null;
   RecordingFiles.copyTo(app, file, dest);
   ContentValues done = new ContentValues();
   done.put(MediaStore.MediaColumns.IS_PENDING, 0);
   app.getContentResolver().update(dest, done, null, null);
   return dest.toString();
  } catch (Exception e) {
   Log.w("AlMahamm", "public copy failed " + relative, e);
   if (dest != null) try { app.getContentResolver().delete(dest, null, null); } catch (Exception ignored) {}
   return null;
  }
 }

 private static String mime(String name) { return name != null && name.endsWith(".3gp") ? "audio/3gpp" : "audio/mp4"; }

 private void abort(String message) {
  if (finishing) return;
  finishing = true;
  active = false;
  closeFailedAttempt();
  abandonFocus();
  releaseWake();
  deletePartial();
  fail(message, true);
 }

 private void fail(String message, boolean foregroundStarted) {
  Store.prefs(this).edit().putString("status", message).remove("current_file").apply();
  Notices.info(this, 3, message);
  if (foregroundStarted) try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
  Notices.shortcuts(this);
  stopSelf();
 }

 private void releaseRecorder() {
  if (recorder != null) {
   try { recorder.release(); } catch (Exception ignored) {}
   recorder = null;
  }
  if (output != null) {
   try { output.close(); } catch (Exception ignored) {}
   output = null;
  }
 }

 private void closeFailedAttempt() {
  if (recorder != null) {
   try { recorder.reset(); } catch (Exception ignored) {}
   try { recorder.release(); } catch (Exception ignored) {}
   recorder = null;
  }
  if (output != null) {
   try { output.close(); } catch (Exception ignored) {}
   output = null;
  }
 }

 private void releaseWake() {
  if (wake != null) try { if (wake.isHeld()) wake.release(); } catch (Exception ignored) {}
  wake = null;
 }

 private void deletePartial() {
  try { if (internal != null && internal.exists()) internal.delete(); } catch (Exception ignored) {}
 }

 private void requestFocus() {
  try {
   AudioManager am = getSystemService(AudioManager.class);
   focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
     .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
     .build();
   am.requestAudioFocus(focus);
  } catch (Exception ignored) {}
 }

 private void abandonFocus() {
  try { if (focus != null) getSystemService(AudioManager.class).abandonAudioFocusRequest(focus); } catch (Exception ignored) {}
  focus = null;
 }

 private static String text(Exception e) {
  String m = e.getMessage();
  return e.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
 }

 @Override public void onDestroy() {
  if (!finishing && (active || recorder != null)) finishRecording();
  else releaseRecorder();
  active = false;
  super.onDestroy();
 }
}
