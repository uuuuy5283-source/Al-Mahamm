package com.almaham.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Shares an internal recording without the system folder picker. */
public class RecordingProvider extends ContentProvider {
 static final String AUTH = "com.almaham.app.fixed.recordings";
 static Uri uriFor(File file) {
  return new Uri.Builder().scheme("content").authority(AUTH).appendPath(file.getName()).build();
 }
 @Override public boolean onCreate() { return true; }
 @Override public String getType(Uri uri) {
  String name = uri.getLastPathSegment();
  if (name != null && name.endsWith(".3gp")) return "audio/3gpp";
  return "audio/mp4";
 }
 private File file(Uri uri) throws FileNotFoundException {
  String name = uri.getLastPathSegment();
  if (name == null || getContext() == null) throw new FileNotFoundException();
  name = Uri.decode(name);
  if (name.contains("/") || name.contains("\\") || name.contains("..")) throw new FileNotFoundException();
  File f = new File(new File(getContext().getFilesDir(), "recordings"), name);
  if (!f.isFile()) throw new FileNotFoundException(name);
  return f;
 }
 @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
  return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
 }
 @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
  try {
   File f = file(uri);
   MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
   c.addRow(new Object[]{f.getName(), f.length()});
   return c;
  } catch (Exception e) { return null; }
 }
 @Override public Uri insert(Uri uri, ContentValues values) { return null; }
 @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
 @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
