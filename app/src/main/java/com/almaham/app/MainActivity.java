package com.almaham.app;
import android.Manifest;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.DocumentsContract;
import java.io.OutputStream;
import android.view.*;
import android.widget.*;
import java.time.YearMonth;
import java.util.Locale;
public class MainActivity extends Activity {
 private static final int AUDIO = 11, NOTIFY = 12, FOLDER = 13, ADMIN = 14;
 private final Handler handler = new Handler(Looper.getMainLooper());
 private TextView status, timer, details, folderLabel;
 private Button record, choose, reset;
 private boolean transitioning = false, checkingFolder = false;
 private long transitionUntil = 0;
 private String pendingDialog;
 private final Runnable tick = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 500); } };
 private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
 private TextView text(LinearLayout parent, String value, int size) {
  TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(25, 51, 44)); t.setPadding(0, dp(8), 0, dp(8)); parent.addView(t); return t;
 }
 private Button button(LinearLayout parent, String title, View.OnClickListener click) {
  Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(click); parent.addView(b, new LinearLayout.LayoutParams(-1, dp(58))); return b;
 }
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(244, 247, 245));
  LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(22), dp(24), dp(22), dp(30)); scroll.addView(box); setContentView(scroll);
  if (Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener((v, insets) -> {
   android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
   v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
  });
  text(box, "المهام | Al-Maham", 28);
  text(box, "تسجيل صوتي يدوي — النسخة 1.5", 16);
  status = text(box, "جاهز للتسجيل", 20); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  timer = text(box, "00:00:00", 36); timer.setGravity(Gravity.CENTER); timer.setTextDirection(View.TEXT_DIRECTION_LTR);
  record = button(box, "بدء التسجيل", v -> toggle());
  details = text(box, "", 16);
  button(box, "تسجيلاتي — تشغيل ومشاركة وحفظ", v -> startActivity(new Intent(this, RecordingsActivity.class)));
  text(box, "مكان حفظ التسجيلات", 21); folderLabel = text(box, "", 15);
  choose = button(box, "اختيار مجلد إضافي (اختياري)", v -> pickFolder());
  reset = button(box, "بدون مجلد إضافي — الحفظ داخل التطبيق وMusic", v -> { clearFolder(); refresh(); });
  text(box, "التسجيل يُحفظ دائمًا داخل التطبيق حتى لو فشل اختيار المجلد أو بطاقة SD. بعد الإيقاف افتح «تسجيلاتي» للتشغيل أو المشاركة. النسخ العامة تذهب إلى Music/Al-Mahamm أو Downloads/Al-Mahamm. إذا أقفل منتقي الملفات برسالة خطأ، تجاوزه؛ الحفظ الداخلي لا يحتاجه.", 14);
  button(box, "السماح بالإشعارات (لزر إيقاف الخلفية)", v -> notificationPermission());
  Switch boot = new Switch(this); boot.setText("إشعار جاهزية التطبيق بعد تشغيل الهاتف"); boot.setChecked(Store.prefs(this).getBoolean("boot_notice", true));
  boot.setOnCheckedChangeListener((b, on) -> Store.prefs(this).edit().putBoolean("boot_notice", on).apply()); box.addView(boot);
  button(box, "منع النظام من إغلاق التطبيق أثناء التسجيل", v -> battery());
  button(box, "إطفاء الشاشة", v -> lockScreen());
  button(box, "إيقاف التشغيل", v -> power());
  button(box, "إيقاف تطبيقات الخلفية الآن", v -> stopBackground());
  button(box, "إدارة تطبيقات الخلفية", v -> startActivity(new Intent(this, BackgroundAppsActivity.class)));
  button(box, "إلغاء صلاحية مسؤول الجهاز", v -> removeAdmin());
  text(box, "في شريط الإشعارات، وسّع إشعار «المهام». الأزرار الثلاثة: بدء التسجيل، إيقاف الخلفية، وإطفاء الشاشة. أندرويد لا يسمح بإخفاء أكثر من ثلاثة أزرار، لذلك قائمة الطاقة تبقى داخل التطبيق. إيقاف الخلفية يطلب من النظام إنهاء عمليات التطبيقات الأخرى؛ بعض الأجهزة تعيد تشغيلها، والإيقاف الإجباري النهائي من صفحة كل تطبيق.", 14);
  text(box, "الميكروفون يعمل فقط بعد ضغطك على بدء التسجيل. سجّل ثانيتين على الأقل ثم أوقف من الزر أو من إشعار التسجيل. لا يبدأ تسجيل تلقائي بعد إعادة تشغيل الهاتف.", 14);
  try { Notices.channels(this); Notices.shortcuts(this); MonthlyReceiver.check(this); } catch (Exception ignored) {}
  if (Build.VERSION.SDK_INT >= 33 && !Store.prefs(this).getBoolean("asked_notifications", false)) notificationPermission();
  maybeStartFromIntent();
 }
 @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); maybeStartFromIntent(); }
 @Override protected void onResume() { super.onResume(); handler.post(tick); try { Notices.shortcuts(this); MonthlyReceiver.check(this); } catch (Exception ignored) {} showPending(); }
 @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
 private void maybeStartFromIntent() {
  Intent intent = getIntent();
  if (intent != null && intent.getBooleanExtra("start_recording", false)) {
   intent.removeExtra("start_recording");
   setIntent(intent);
   handler.post(this::toggle);
  }
 }
 private void refresh() {
  boolean running = RecordingService.active;
  if (transitioning && (running || SystemClock.elapsedRealtime() > transitionUntil)) transitioning = false;
  record.setEnabled(!transitioning && !checkingFolder && !RecordingService.saving);
  record.setText(running ? "إيقاف التسجيل وحفظه" : "بدء التسجيل");
  status.setText(RecordingService.saving ? "جارٍ حفظ التسجيل…" : (running ? "جارٍ التسجيل رقم " + RecordingService.number : Store.prefs(this).getString("status", "جاهز للتسجيل")));
  long sec = running ? Math.max(0, (SystemClock.elapsedRealtime() - RecordingService.started) / 1000) : 0;
  timer.setText(String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60));
  String extra = Notices.allowed(this) ? "" : "\nالإشعارات متوقفة، لذلك زر إيقاف الخلفية غير ظاهر في الشريط.";
  details.setText("إجمالي التسجيلات المحفوظة: " + Store.last(this) + "\nتسجيلات هذا الشهر: " + Store.count(this, YearMonth.now().toString()) + "\nآخر ملف: " + Store.prefs(this).getString("last_name", "لا يوجد") + extra);
  String folder = Store.folder(this);
  folderLabel.setText(folder.isEmpty() ? "الحفظ الأساسي داخل التطبيق + Music أو Downloads. لا يوجد مجلد إضافي." : Uri.decode(folder));
  choose.setEnabled(!running && !transitioning && !checkingFolder && !RecordingService.saving);
  reset.setEnabled(!running && !transitioning && !checkingFolder && !RecordingService.saving);
 }
 private void toggle() {
  if (RecordingService.active) { startService(new Intent(this, RecordingService.class).setAction(RecordingService.STOP)); return; }
  if (RecordingService.saving) { toast("انتظر اكتمال حفظ التسجيل الحالي"); return; }
  if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
   new AlertDialog.Builder(this).setTitle("إذن الميكروفون").setMessage("يحتاج التطبيق إذن الميكروفون لتسجيل الصوت عند ضغطك على بدء فقط.")
     .setPositiveButton("متابعة", (d, w) -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO)).setNegativeButton("إلغاء", null).show();
   return;
  }
  try {
   startForegroundService(new Intent(this, RecordingService.class).setAction(RecordingService.START));
   transitioning = true; transitionUntil = SystemClock.elapsedRealtime() + 4000; refresh();
  } catch (Exception e) { toast("تعذر بدء الخدمة. " + e.getClass().getSimpleName()); }
 }
 private void notificationPermission() {
  if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
   Store.prefs(this).edit().putBoolean("asked_notifications", true).apply();
   requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFY);
  } else {
   Notices.shortcuts(this);
   try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
   catch (Exception e) { toast("فعّل إشعارات المهام من إعدادات الهاتف"); }
  }
 }
 @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
  super.onRequestPermissionsResult(request, permissions, results);
  if (request == AUDIO) {
   if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) toggle();
   else safeDialog("إذن الميكروفون", "لم يُمنح إذن الميكروفون. يمكنك منحه من إعدادات التطبيق.", "الإعدادات", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
  }
  if (request == NOTIFY) {
   Notices.shortcuts(this);
   try { MonthlyReceiver.check(this); } catch (Exception ignored) {}
   if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) safeDialog("الإشعارات", "إذن الإشعارات غير مفعّل. بدونه لن يظهر زر إيقاف الخلفية في الشريط.", "الإعدادات", this::notificationPermissionSettings);
  }
 }
 private void notificationPermissionSettings() {
  try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
  catch (Exception e) { toast("افتح إعدادات الهاتف ثم إشعارات المهام"); }
 }
 private void pickFolder() {
  if (RecordingService.active || RecordingService.saving || checkingFolder) return;
  try {
   startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER);
  } catch (Exception e) { toast("منتقي المجلدات غير متاح. التسجيل يُحفظ داخل التطبيق وفي Music أو Downloads."); }
 }
 private void clearFolder() {
  String old = Store.folder(this);
  Store.prefs(this).edit().remove("folder").apply();
  releaseFolder(old);
 }
 private void releaseFolder(String old) {
  if (old == null || old.isEmpty()) return;
  try { getContentResolver().releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
  catch (Exception ignored) {}
 }
 @Override protected void onActivityResult(int request, int result, Intent data) {
  super.onActivityResult(request, result, data);
  try {
   if (request == FOLDER) handleFolder(result, data);
   if (request == ADMIN && result == RESULT_OK) lockScreen();
  } catch (Exception e) {
   checkingFolder = false;
   toast("تعذر إكمال الاختيار: " + e.getClass().getSimpleName());
  }
 }
 private void handleFolder(int result, Intent data) {
  if (result != RESULT_OK || data == null || data.getData() == null) { toast("تم إلغاء الاختيار. مكان الحفظ لم يتغير."); return; }
  if (RecordingService.active) { toast("أوقف التسجيل قبل تغيير المجلد"); return; }
  final Uri uri = data.getData();
  boolean persisted = false;
  try {
   getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
   persisted = true;
  } catch (Exception first) {
   try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION); persisted = true; }
   catch (Exception ignored) {}
  }
  if (!persisted) {
   showLater("النظام لم يمنح إذنًا دائمًا لهذا المجلد. التسجيل سيبقى محفوظًا داخل التطبيق وفي Music أو Downloads.");
   return;
  }
  final boolean keep = true;
  checkingFolder = true;
  choose.setEnabled(false);
  toast("جارٍ التحقق من المجلد…");
  new Thread(() -> {
   String message;
   try {
    if (!DocumentsContract.isTreeUri(uri)) throw new SecurityException("ليس مجلدًا");
    Uri parent = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
    Uri probe = DocumentsContract.createDocument(getContentResolver(), parent, "application/octet-stream", "Mahamm_test_" + System.currentTimeMillis() + ".tmp");
    if (probe == null) throw new java.io.IOException("لم يُنشأ ملف اختبار");
    try (OutputStream out = getContentResolver().openOutputStream(probe, "wt")) {
     if (out == null) throw new java.io.IOException("الكتابة مرفوضة");
     out.write(new byte[]{1, 2, 3});
     out.flush();
    } finally {
     try { DocumentsContract.deleteDocument(getContentResolver(), probe); } catch (Exception ignored) {}
    }
    rememberFolder(uri);
    message = "تم اختيار المجلد والتحقق من إمكانية الحفظ. النسخة الداخلية تبقى موجودة أيضًا في تسجيلاتي.";
   } catch (Exception e) {
    if (keep) {
     rememberFolder(uri);
     message = "تم حفظ إذن المجلد، لكن اختبار الكتابة لم ينجح الآن. التسجيل يُحفظ داخل التطبيق، وتُعاد محاولة النسخ للمجلد عند الإيقاف.\n" + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
    } else message = e.getMessage();
   }
   final String feedback = message;
   handler.post(() -> { checkingFolder = false; showLater(feedback); refresh(); });
  }, "folder-check").start();
 }
 private void rememberFolder(Uri uri) {
  String old = Store.folder(this);
  String next = uri.toString();
  Store.prefs(this).edit().putString("folder", next).apply();
  if (!next.equals(old)) releaseFolder(old);
 }
 private void showLater(String message) { pendingDialog = message; showPending(); }
 private void showPending() {
  if (pendingDialog == null || isFinishing() || isDestroyed()) return;
  String msg = pendingDialog; pendingDialog = null;
  safeDialog("إذن مجلد الحفظ", msg, null, null);
 }
 private void safeDialog(String title, String message, String action, Runnable ok) {
  try {
   if (isFinishing() || isDestroyed()) { toast(message); return; }
   AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(title).setMessage(message);
   if (action != null && ok != null) b.setPositiveButton(action, (d, w) -> ok.run()).setNegativeButton("إغلاق", null);
   else b.setPositiveButton("حسنًا", null);
   b.show();
  } catch (Exception e) { toast(message); }
 }
 private void lockScreen() {
  try {
   DevicePolicyManager d = getSystemService(DevicePolicyManager.class);
   ComponentName admin = new ComponentName(this, AdminReceiver.class);
   if (d != null && d.isAdminActive(admin)) d.lockNow();
   else startActivityForResult(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "لقفل الشاشة عند ضغطك على زر إطفاء الشاشة فقط."), ADMIN);
  } catch (Exception e) { toast("تعذر قفل الشاشة على هذا الجهاز"); }
 }
 private void removeAdmin() {
  try {
   DevicePolicyManager d = getSystemService(DevicePolicyManager.class);
   ComponentName admin = new ComponentName(this, AdminReceiver.class);
   if (d != null && d.isAdminActive(admin)) { d.removeActiveAdmin(admin); toast("تم إلغاء صلاحية قفل الشاشة"); }
   else toast("صلاحية مسؤول الجهاز غير مفعّلة");
  } catch (Exception e) { toast("تعذر إلغاء الصلاحية"); }
 }
 private void power() {
  try {
   if (PowerMenuService.showPowerMenu()) return;
   safeDialog("فتح قائمة الطاقة الرسمية", "لإظهار قائمة الطاقة، فعّل خدمة «المهام — فتح قائمة الطاقة» من تسهيلات الاستخدام، ثم عد واضغط الزر مجددًا. الخدمة تفتح قائمة أندرويد فقط ولا تقرأ الشاشة.", "فتح الإعدادات", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
  } catch (Exception e) { toast("تعذر فتح قائمة الطاقة"); }
 }
 private void battery() {
  try {
   startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
  } catch (Exception e) {
   try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
   catch (Exception e2) { toast("من إعدادات البطارية اختر بلا قيود لتطبيق المهام، وعلى شاومي فعّل التشغيل التلقائي."); }
  }
 }
 private void stopBackground() {
  toast("جارٍ طلب إيقاف تطبيقات الخلفية…");
  new Thread(() -> {
   int n = BackgroundStopper.stop(this);
   String text = BackgroundStopper.message(n);
   Notices.info(this, 8, text);
   handler.post(() -> toast(text));
  }, "kill-background-ui").start();
 }
 private void toast(String s) { try { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); } catch (Exception ignored) {} }
}
