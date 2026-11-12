package com.almaham.app;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))return;
  Notices.channels(c);Notices.shortcuts(c);MonthlyReceiver.check(c);
  if(Store.prefs(c).getBoolean("boot_notice",true))Notices.info(c,5,"التطبيق جاهز بعد تشغيل الهاتف — اضغط لفتحه. لا يوجد تسجيل تلقائي.");
 }
}
