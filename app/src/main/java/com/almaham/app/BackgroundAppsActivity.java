package com.almaham.app;

import android.app.Activity;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BackgroundAppsActivity extends Activity {
 private LinearLayout list;
 private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
 private TextView text(String value, int size) {
  TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(25, 51, 44)); t.setPadding(0, dp(8), 0, dp(8)); return t;
 }
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  ScrollView scroll = new ScrollView(this);
  list = new LinearLayout(this);
  list.setOrientation(LinearLayout.VERTICAL);
  list.setPadding(dp(20), dp(24), dp(20), dp(30));
  scroll.addView(list);
  setContentView(scroll);
  refresh();
 }
 @Override protected void onResume() { super.onResume(); if (list != null) refresh(); }
 private void refresh() {
  try { fill(); }
  catch (Exception e) {
   list.removeAllViews();
   list.addView(text("تعذر قراءة التطبيقات دون إغلاق الشاشة.\n" + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()), 16));
  }
 }
 private void fill() {
  list.removeAllViews();
  list.addView(text("إدارة تطبيقات الخلفية", 26));
  list.addView(text("زر الإشعار «إيقاف الخلفية» يطلب إنهاء عمليات التطبيقات الأخرى. أندرويد لا يسمح بإيقاف إجباري جماعي، لذلك هذه الشاشة تفتح صفحة النظام لكل تطبيق لتضغط «إيقاف إجباري» بنفسك.", 15));
  Button kill = new Button(this); kill.setText("إيقاف عمليات الخلفية الآن"); kill.setAllCaps(false);
  kill.setOnClickListener(v -> new Thread(() -> {
   int n = BackgroundStopper.stop(this);
   String msg = BackgroundStopper.message(n);
   runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
  }, "kill-ui").start());
  list.addView(kill, new LinearLayout.LayoutParams(-1, dp(54)));
  Button access = new Button(this); access.setText("فتح إذن الوصول إلى الاستخدام"); access.setAllCaps(false);
  access.setOnClickListener(v -> { try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception e) { Toast.makeText(this, "تعذر فتح إعداد إذن الاستخدام", Toast.LENGTH_LONG).show(); } });
  list.addView(access, new LinearLayout.LayoutParams(-1, dp(54)));
  UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
  List<UsageStats> recent = new ArrayList<>();
  boolean allowed = false;
  if (manager != null) {
   try {
    long now = System.currentTimeMillis();
    List<UsageStats> probe = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now);
    allowed = probe != null && !probe.isEmpty();
    if (allowed) {
     Map<String, UsageStats> stats = manager.queryAndAggregateUsageStats(now - 24L * 60L * 60L * 1000L, now);
     if (stats != null) for (UsageStats s : stats.values()) if (s.getTotalTimeInForeground() > 0 && !getPackageName().equals(s.getPackageName())) recent.add(s);
    }
   } catch (SecurityException se) { allowed = false; }
  }
  if (!allowed) list.addView(text("إذن استخدام التطبيقات غير مفعّل. فعّله ثم ارجع لهذه الشاشة لتظهر التطبيقات المستخدمة حديثًا.", 14));
  recent.sort((a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
  int shown = 0;
  for (UsageStats s : recent) { if (addApp(s.getPackageName())) shown++; if (shown >= 30) break; }
  if (shown == 0) list.addView(text(allowed ? "لا توجد تطبيقات مستخدمة خلال آخر 24 ساعة." : "بعد منح الإذن ستظهر هنا التطبيقات المستخدمة.", 16));
 }
 private boolean addApp(String packageName) {
  try {
   PackageManager pm = getPackageManager();
   ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
   if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return false;
   LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
   TextView label = text(pm.getApplicationLabel(info) + "\n" + packageName, 16);
   row.addView(label, new LinearLayout.LayoutParams(0, dp(72), 1));
   Button stop = new Button(this); stop.setText("فتح الإيقاف الإجباري"); stop.setAllCaps(false);
   stop.setOnClickListener(v -> { try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))); } catch (Exception e) { Toast.makeText(this, "تعذر فتح إعدادات التطبيق", Toast.LENGTH_LONG).show(); } });
   row.addView(stop, new LinearLayout.LayoutParams(dp(155), dp(58)));
   list.addView(row);
   return true;
  } catch (Exception e) { return false; }
 }
}
