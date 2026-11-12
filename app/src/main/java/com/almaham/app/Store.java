package com.almaham.app;
import android.content.Context;
import android.content.SharedPreferences;
import java.time.YearMonth;
final class Store {
 static SharedPreferences prefs(Context c) { return c.getSharedPreferences("almaham", Context.MODE_PRIVATE); }
 static int last(Context c) { return prefs(c).getInt("last",0); }
 static String folder(Context c) { return prefs(c).getString("folder", ""); }
 static int count(Context c, String month) { return prefs(c).getInt("count_"+month,0); }
 static boolean saved(Context c, int number, String name, String uri) {
  String month=YearMonth.now().toString();
  return prefs(c).edit().putInt("last",number).putInt("count_"+month,count(c,month)+1)
    .putString("last_name",name).putString("last_uri",uri).commit();
 }
}
