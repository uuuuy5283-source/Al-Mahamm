package com.almaham.app;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.List;
import java.util.Map;

/** Best-effort stop of other apps' background processes. Android does not allow a normal app to force-stop others. */
final class BackgroundStopper {
 private BackgroundStopper() {}
 static int stop(Context c) {
  ActivityManager am = c.getSystemService(ActivityManager.class);
  if (am == null) return 0;
  int n = 0;
  UsageStatsManager usage = c.getSystemService(UsageStatsManager.class);
  boolean any = false;
  if (usage != null) {
   try {
    long now = System.currentTimeMillis();
    Map<String, UsageStats> stats = usage.queryAndAggregateUsageStats(now - 12L * 60L * 60L * 1000L, now);
    if (stats != null) {
     for (UsageStats s : stats.values()) {
      if (s == null) continue;
      any = true;
      if (killOne(c, am, s.getPackageName())) n++;
     }
    }
   } catch (Exception ignored) {}
  }
  if (!any) {
   try {
    List<ApplicationInfo> apps = c.getPackageManager().getInstalledApplications(0);
    for (ApplicationInfo info : apps) {
     if (killOne(c, am, info.packageName)) n++;
    }
   } catch (Exception ignored) {}
  }
  return n;
 }
 private static boolean killOne(Context c, ActivityManager am, String packageName) {
  if (packageName == null || packageName.equals(c.getPackageName())) return false;
  try {
   ApplicationInfo info = c.getPackageManager().getApplicationInfo(packageName, 0);
   if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return false;
   am.killBackgroundProcesses(packageName);
   return true;
  } catch (Exception ignored) { return false; }
 }
 static String message(int count) {
  if (count <= 0) return "لم يُسمح بإيقاف تطبيقات الخلفية. افتح «إدارة تطبيقات الخلفية» وفعّل إذن الاستخدام، ثم أعد المحاولة. الإيقاف الإجباري النهائي من صفحة كل تطبيق.";
  return "تم طلب إيقاف عمليات الخلفية لـ " + count + " تطبيق. أندرويد قد يعيد تشغيل بعضها، والإيقاف الإجباري يتم من صفحة التطبيق نفسه.";
 }
}
