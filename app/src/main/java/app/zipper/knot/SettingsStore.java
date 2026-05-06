package app.zipper.knot;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.json.JSONObject;

public class SettingsStore {

  private static final String SETTINGS_FILE = "knot_settings.json.gz";
  private static final String UNSEND_HISTORY_FILE = "knot_unsend_history.json.gz";
  private static final String READ_HISTORY_FILE = "knot_read_history.json.gz";

  private static volatile String pointerPath = null;
  private static volatile Context appContext = null;
  private static volatile Uri cachedTreeUri = null;
  private static volatile Uri cachedDocUri = null;
  private static volatile Uri cachedHistoryUri = null;
  private static volatile Uri cachedReadHistoryUri = null;
  private static volatile boolean isModuleLoaded = false;

  public static void init(android.app.Activity activity) {
    appContext = activity.getApplicationContext();
    ensurePointerPath(activity);
    cachedTreeUri = null;
    cachedDocUri = null;
    cachedHistoryUri = null;
    cachedReadHistoryUri = null;
    ensureTreeUri();
  }

  private static void ensurePointerPath(Context ctx) {
    if (pointerPath != null) return;
    try {
      File extDir = ctx.getExternalFilesDir(null);
      if (extDir != null) {
        extDir.mkdirs();
        pointerPath = extDir.getAbsolutePath() + "/knot_ptr";
      } else {
        String sdcard = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        pointerPath = sdcard + "/Android/data/jp.naver.line.android/files/knot_ptr";
      }
    } catch (Throwable ignored) {
    }
  }

  public static void load(KnotConfig config) {
    ensurePointerPath(appContext);
    try {
      JSONObject json = readJson(SETTINGS_FILE);
      for (KnotConfig.Item item : config.items) {
        if (json.has(item.key)) {
          Object val = json.get(item.key);
          if (val instanceof Boolean) item.enabled = (Boolean) val;
          else if (val instanceof String) item.value = (String) val;
        }
      }
    } catch (Throwable ignored) {
    }
  }

  public static void setContext(Context context) {
    appContext = context;
    ensurePointerPath(context);
  }

  public static Context getContext() {
    return appContext;
  }

  public static String getSettingsDir() {
    Uri tree = ensureTreeUri();
    return tree != null ? uriToDisplayPath(tree) : null;
  }

  public static String getSettingsDirUri() {
    Uri tree = ensureTreeUri();
    return tree != null ? tree.toString() : null;
  }

  public static void setSettingsDir(String uriString) {
    ensurePointerPath(appContext);
    if (pointerPath == null) return;
    try {
      try (FileWriter w = new FileWriter(pointerPath)) {
        w.write(uriString);
      }
      cachedTreeUri = Uri.parse(uriString);
      cachedDocUri = null;
      cachedHistoryUri = null;
      cachedReadHistoryUri = null;
    } catch (Throwable e) {
      android.util.Log.e("Knot", "SettingsStore.setSettingsDir failed", e);
    }
  }

  public static boolean isConfigured() {
    return ensureTreeUri() != null;
  }

  public static boolean isLoaded() {
    return isModuleLoaded;
  }

  public static void setLoaded(boolean loaded) {
    isModuleLoaded = loaded;
  }

  public static void save(String key, boolean value) {
    try {
      JSONObject json = readJson(SETTINGS_FILE);
      json.put(key, value);
      writeJson(SETTINGS_FILE, json);
    } catch (Throwable e) {
      android.util.Log.e("Knot", "SettingsStore.save failed", e);
    }
  }

  public static boolean get(String key, boolean defaultValue) {
    try {
      JSONObject json = readJson(SETTINGS_FILE);
      if (json.has(key)) return json.getBoolean(key);
    } catch (Throwable ignored) {
    }
    return defaultValue;
  }

  public static void save(String key, String value) {
    try {
      JSONObject json = readJson(SETTINGS_FILE);
      json.put(key, value);
      writeJson(SETTINGS_FILE, json);
    } catch (Throwable e) {
      android.util.Log.e("Knot", "SettingsStore.save string failed", e);
    }
  }

  public static String getString(String key, String defaultValue) {
    try {
      JSONObject json = readJson(SETTINGS_FILE);
      if (json.has(key)) return json.getString(key);
    } catch (Throwable ignored) {
    }
    return defaultValue;
  }

  public static JSONObject loadAll() {
    try {
      return readJson(SETTINGS_FILE);
    } catch (Throwable e) {
      return new JSONObject();
    }
  }

  public static void reset() {
    try {
      writeJson(SETTINGS_FILE, new JSONObject());
    } catch (Throwable ignored) {
    }
    try {
      ensurePointerPath(appContext);
      if (pointerPath != null) {
        File f = new File(pointerPath);
        if (f.exists()) f.delete();
      }
    } catch (Throwable ignored) {
    }
    cachedTreeUri = null;
    cachedDocUri = null;
    cachedHistoryUri = null;
    cachedReadHistoryUri = null;
  }

  public static JSONObject loadUnsendHistory() {
    try {
      return readJson(UNSEND_HISTORY_FILE);
    } catch (Throwable e) {
      return new JSONObject();
    }
  }

  public static void saveUnsendHistory(JSONObject json) {
    try {
      writeJson(UNSEND_HISTORY_FILE, json);
    } catch (Throwable ignored) {
    }
  }

  public static JSONObject loadReadHistory() {
    try {
      return readJson(READ_HISTORY_FILE);
    } catch (Throwable e) {
      return new JSONObject();
    }
  }

  public static void saveReadHistory(JSONObject json) {
    try {
      writeJson(READ_HISTORY_FILE, json);
    } catch (Throwable ignored) {
    }
  }

  private static String readPointer() {
    ensurePointerPath(appContext);
    if (pointerPath == null) return null;
    try {
      File f = new File(pointerPath);
      if (!f.exists()) return null;
      try (BufferedReader r = new BufferedReader(new FileReader(f))) {
        String line = r.readLine();
        return (line != null) ? line.trim() : null;
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  private static Uri ensureTreeUri() {
    if (cachedTreeUri != null) return cachedTreeUri;
    String saved = readPointer();
    if (saved == null || !saved.startsWith("content://")) return null;
    cachedTreeUri = Uri.parse(saved);
    return cachedTreeUri;
  }

  private static Uri getDocUri(String fileName, boolean createIfMissing) throws Throwable {
    if (SETTINGS_FILE.equals(fileName) && cachedDocUri != null) return cachedDocUri;
    if (UNSEND_HISTORY_FILE.equals(fileName) && cachedHistoryUri != null) return cachedHistoryUri;
    if (READ_HISTORY_FILE.equals(fileName) && cachedReadHistoryUri != null)
      return cachedReadHistoryUri;

    Uri treeUri = ensureTreeUri();
    if (treeUri == null || appContext == null) return null;

    ContentResolver resolver = appContext.getContentResolver();
    String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);

    try (Cursor cursor =
        resolver.query(
            childrenUri,
            new String[] {
              DocumentsContract.Document.COLUMN_DOCUMENT_ID,
              DocumentsContract.Document.COLUMN_DISPLAY_NAME
            },
            null,
            null,
            null)) {
      if (cursor != null) {
        while (cursor.moveToNext()) {
          if (fileName.equals(cursor.getString(1))) {
            Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0));
            if (SETTINGS_FILE.equals(fileName)) cachedDocUri = uri;
            else if (UNSEND_HISTORY_FILE.equals(fileName)) cachedHistoryUri = uri;
            else if (READ_HISTORY_FILE.equals(fileName)) cachedReadHistoryUri = uri;
            return uri;
          }
        }
      }
    }

    if (createIfMissing) {
      Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
      Uri newUri =
          DocumentsContract.createDocument(
              resolver, parentDocUri, "application/octet-stream", fileName);
      if (SETTINGS_FILE.equals(fileName)) cachedDocUri = newUri;
      else if (UNSEND_HISTORY_FILE.equals(fileName)) cachedHistoryUri = newUri;
      else if (READ_HISTORY_FILE.equals(fileName)) cachedReadHistoryUri = newUri;
      return newUri;
    }
    return null;
  }

  private static JSONObject readJson(String fileName) throws Throwable {
    Uri docUri = getDocUri(fileName, false);
    if (docUri == null || appContext == null) return new JSONObject();

    try (InputStream is = appContext.getContentResolver().openInputStream(docUri);
        GZIPInputStream gzis = new GZIPInputStream(is)) {
      StringBuilder sb = new StringBuilder();
      try (BufferedReader r =
          new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8))) {
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
      }
      String text = sb.toString().trim();
      return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    } catch (Throwable t) {
      return new JSONObject();
    }
  }

  private static void writeJson(String fileName, JSONObject json) throws Throwable {
    Uri docUri = getDocUri(fileName, true);
    if (docUri == null || appContext == null) throw new IllegalStateException("Not configured");

    try (OutputStream os = appContext.getContentResolver().openOutputStream(docUri, "wt");
        GZIPOutputStream gzos = new GZIPOutputStream(os)) {
      gzos.write(json.toString().getBytes(StandardCharsets.UTF_8));
      gzos.finish();
    }
  }

  private static String uriToDisplayPath(Uri uri) {
    if (uri == null) return null;
    try {
      String decoded = Uri.decode(uri.toString());
      if (decoded.contains("/tree/primary:")) {
        String path =
            decoded.substring(decoded.indexOf("/tree/primary:") + "/tree/primary:".length());
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return "/sdcard/" + path;
      }
      String docId = DocumentsContract.getTreeDocumentId(uri);
      if (docId != null && docId.contains(":")) {
        return "Internal:/" + docId.substring(docId.indexOf(":") + 1);
      }
      return Uri.decode(uri.getLastPathSegment());
    } catch (Throwable ignored) {
    }
    return uri.getLastPathSegment();
  }
}
