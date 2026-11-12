package com.almaham.app;
import android.content.Context;
import android.net.Uri;
import java.io.*;

final class RecordingFiles {
 static File directory(Context c) throws IOException {
  File dir = new File(c.getFilesDir(), "recordings");
  if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("تعذر فتح التسجيلات الداخلية");
  return dir;
 }
 /** Copies the whole file. Does not read it back: some SD and MediaStore providers reject a read and that used to discard a good copy. */
 static long copyTo(Context c, File source, Uri destination) throws IOException {
  if (source == null || !source.isFile() || source.length() == 0) throw new IOException("ملف التسجيل فارغ");
  long count = 0;
  try (InputStream in = new FileInputStream(source); OutputStream out = c.getContentResolver().openOutputStream(destination, "wt")) {
   if (out == null) throw new IOException("لم يسمح النظام بفتح الملف للكتابة");
   byte[] buffer = new byte[65536];
   int n;
   while ((n = in.read(buffer)) != -1) {
    out.write(buffer, 0, n);
    count += n;
   }
   out.flush();
  }
  if (count != source.length()) throw new IOException("النسخة ناقصة");
  return count;
 }
}
