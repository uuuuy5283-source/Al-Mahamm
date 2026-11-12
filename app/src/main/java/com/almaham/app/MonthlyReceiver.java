package com.almaham.app;
import android.app.*;
import android.content.*;
import java.time.*;
public class MonthlyReceiver extends BroadcastReceiver {
 static PendingIntent pending(Context c){return PendingIntent.getBroadcast(c,50,new Intent(c,MonthlyReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void schedule(Context c){
  long when=YearMonth.now().plusMonths(1).atDay(1).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c));
 }
 static void check(Context c){
  YearMonth previous=YearMonth.now().minusMonths(1);
  String reported=Store.prefs(c).getString("reported_month","");
  if(reported.isEmpty()){
   Store.prefs(c).edit().putString("reported_month",previous.toString()).apply();
  }else if(YearMonth.parse(reported).isBefore(previous)){
   // Advance only when notifications are enabled, so returning to the app can retry.
   if(c.getSystemService(NotificationManager.class).areNotificationsEnabled()){
    StringBuilder summary=new StringBuilder();
    YearMonth first=YearMonth.parse(reported).plusMonths(1);
    // Bound a long absence without losing counts: summarize all missed months together.
    int total=0;for(YearMonth m=first;!m.isAfter(previous);m=m.plusMonths(1))total+=Store.count(c,m.toString());
    if(first.equals(previous))summary.append("ملخص شهر ").append(previous).append(": ").append(total).append(" تسجيل محفوظ");
    else summary.append("ملخص الأشهر من ").append(first).append(" إلى ").append(previous).append(": ").append(total).append(" تسجيل محفوظ");
    Notices.info(c,4,summary.toString());Store.prefs(c).edit().putString("reported_month",previous.toString()).apply();
   }
  }
  schedule(c);
 }
 @Override public void onReceive(Context c,Intent i){check(c);}
}
