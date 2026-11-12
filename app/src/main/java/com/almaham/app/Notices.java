package com.almaham.app;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
final class Notices {
 static final String RECORD = "recording_v2", INFO = "information", CONTROLS = "controls_v2";
 static void channels(Context c) {
  NotificationManager nm = c.getSystemService(NotificationManager.class);
  if (nm == null) return;
  NotificationChannel recording = new NotificationChannel(RECORD, "المهام — التسجيل", NotificationManager.IMPORTANCE_DEFAULT);
  recording.setSound(null, null); recording.enableVibration(false); nm.createNotificationChannel(recording);
  NotificationChannel controls = new NotificationChannel(CONTROLS, "المهام — الاختصارات الدائمة", NotificationManager.IMPORTANCE_DEFAULT);
  controls.setSound(null, null); controls.enableVibration(false); nm.createNotificationChannel(controls);
  nm.createNotificationChannel(new NotificationChannel(INFO, "المهام — الملخص والتنبيهات", NotificationManager.IMPORTANCE_DEFAULT));
 }
 static boolean allowed(Context c) {
  NotificationManager nm = c.getSystemService(NotificationManager.class);
  return (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    && nm != null && nm.areNotificationsEnabled();
 }
 static PendingIntent open(Context c) {
  return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
 }
 static Notification.Builder base(Context c, String channel, String text) {
  channels(c);
  return new Notification.Builder(c, channel).setSmallIcon(com.almaham.app.R.drawable.ic_notification)
    .setContentTitle("المهام").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
    .setContentIntent(open(c)).setAutoCancel(true);
 }
 static PendingIntent action(Context c, String task) {
  return PendingIntent.getBroadcast(c, task.hashCode(), new Intent(c, ShortcutReceiver.class).setAction(task), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
 }
 static PendingIntent recordAction(Context c) {
  if (RecordingService.active) {
   return PendingIntent.getService(c, 2, new Intent(c, RecordingService.class).setAction(RecordingService.STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
  Intent open = new Intent(c, MainActivity.class).putExtra("start_recording", true)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
  return PendingIntent.getActivity(c, 21, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
 }
 static void shortcuts(Context c) {
  channels(c);
  if (!allowed(c)) return;
  NotificationManager nm = c.getSystemService(NotificationManager.class);
  if (nm == null) return;
  try {
   Notification.Builder n = base(c, CONTROLS, "جاهز — وسّع الإشعار: تسجيل، إيقاف الخلفية، إطفاء الشاشة").setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true);
   n.addAction(new Notification.Action.Builder(null, RecordingService.active ? "إيقاف وحفظ" : "بدء التسجيل", recordAction(c)).build());
   n.addAction(new Notification.Action.Builder(null, "إيقاف الخلفية", action(c, "killbg")).build());
   n.addAction(new Notification.Action.Builder(null, "إطفاء الشاشة", action(c, "lock")).build());
   nm.notify(20, n.build());
  } catch (Exception ignored) {}
 }
 static Notification recording(Context c, int number, long start) {
  PendingIntent stop = PendingIntent.getService(c, 2, new Intent(c, RecordingService.class).setAction(RecordingService.STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  return base(c, RECORD, "جارٍ تسجيل الصوت — التسجيل رقم " + number + ". أوقفه من هنا بعد ثانيتين على الأقل.").setOngoing(true).setAutoCancel(false)
    .setWhen(start).setUsesChronometer(true).addAction(new Notification.Action.Builder(null, "إيقاف وحفظ", stop).build()).build();
 }
 static void info(Context c, int id, String text) {
  if (!allowed(c)) return;
  try {
   NotificationManager nm = c.getSystemService(NotificationManager.class);
   if (nm != null) nm.notify(id, base(c, INFO, text).build());
  } catch (Exception ignored) {}
 }
}
