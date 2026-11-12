package com.almaham.app;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.widget.Toast;
public class ShortcutReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c, Intent i) {
  if (i == null || i.getAction() == null) return;
  try {
   if ("lock".equals(i.getAction())) {
    DevicePolicyManager d = c.getSystemService(DevicePolicyManager.class);
    ComponentName admin = new ComponentName(c, AdminReceiver.class);
    if (d != null && d.isAdminActive(admin)) d.lockNow();
    else message(c, "فعّل مسؤول الجهاز من زر إطفاء الشاشة داخل التطبيق أولًا");
   } else if ("power".equals(i.getAction()) && !PowerMenuService.showPowerMenu()) {
    message(c, "فعّل خدمة المهام لتسهيل الاستخدام من التطبيق أولًا");
   } else if ("killbg".equals(i.getAction())) {
    final PendingResult pending = goAsync();
    final Context app = c.getApplicationContext();
    new Thread(() -> {
     try {
      int n = BackgroundStopper.stop(app);
      String text = BackgroundStopper.message(n);
      Notices.info(app, 8, text);
      message(app, text);
     } catch (Exception e) {
      Notices.info(app, 8, "تعذر إيقاف تطبيقات الخلفية");
     } finally {
      pending.finish();
     }
    }, "kill-background").start();
   }
  } catch (Exception e) {
   message(c, "تعذر تنفيذ الاختصار. افتح تطبيق المهام وتحقق من الأذونات.");
  }
 }
 private static void message(Context c, String s) {
  try { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); } catch (Exception ignored) {}
 }
}
