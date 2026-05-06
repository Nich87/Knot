package app.zipper.knot.hooks;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupRestoreHook implements BaseHook {

  private static final String LOG_TAG = "KnotSync";
  private static final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
  private static final Handler uiHandler = new Handler(Looper.getMainLooper());

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {}

  public static void runBackup(Context context) {
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.RESTORE_PREPARING);
    pd.show();

    syncExecutor.execute(
        () -> {
          final boolean result = executeKnotBackup(context);
          uiHandler.post(
              () -> {
                pd.dismiss();
                notifySyncResult(
                    context, result, ModuleStrings.BACKUP_SUCCESS, ModuleStrings.BACKUP_ERROR);
              });
        });
  }

  public static void runRestore(Context context, File backupFile) {
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.RESTORE_PROCESSING);
    pd.show();

    syncExecutor.execute(
        () -> {
          final boolean result = executeFullRestore(context, backupFile);
          uiHandler.post(
              () -> {
                pd.dismiss();
                if (result) {
                  new AlertDialog.Builder(context, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                      .setTitle(ModuleStrings.RESTORE_SUCCESS)
                      .setMessage(ModuleStrings.MANAGER_RESTART_REQUIRED)
                      .setPositiveButton(
                          ModuleStrings.RESTART_OK,
                          (d, w) -> {
                            android.os.Process.killProcess(android.os.Process.myPid());
                          })
                      .setCancelable(false)
                      .show();
                } else {
                  notifySyncResult(
                      context, false, ModuleStrings.RESTORE_SUCCESS, ModuleStrings.RESTORE_ERROR);
                }
                if (backupFile.getName().startsWith("knot_restore_")) {
                  backupFile.delete();
                }
              });
        });
  }

  private static boolean executeKnotBackup(Context context) {
    try {
      String dirUriStr = SettingsStore.getSettingsDirUri();
      if (dirUriStr == null) return false;

      DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(dirUriStr));
      if (root == null || !root.canWrite()) {
        Log.e(LOG_TAG, "Cannot access backup directory: " + dirUriStr);
        return false;
      }

      String stamp =
          new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
      DocumentFile knotFolder = ensureSubDir(root, "KnotBackup");

      File localDb = context.getDatabasePath("naver_line");
      if (!localDb.exists()) {
        Log.e(LOG_TAG, "Source database not found");
        return false;
      }

      String dbFileName = "Knot_" + stamp + ".db";
      DocumentFile dbOut = knotFolder.createFile("application/octet-stream", dbFileName);
      if (dbOut == null) return false;

      fastChannelCopy(context, localDb, dbOut.getUri());

      for (String suffix : new String[] {"-wal", "-shm"}) {
        File f = new File(localDb.getPath() + suffix);
        if (f.exists()) {
          DocumentFile out = knotFolder.createFile("application/octet-stream", dbFileName + suffix);
          if (out != null) fastChannelCopy(context, f, out.getUri());
        }
      }

      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "Backup failed: " + e.getMessage());
      return false;
    }
  }

  private static boolean executeFullRestore(Context context, File srcFile) {
    try {
      File localDb = context.getDatabasePath("naver_line");

      try (SQLiteDatabase check =
          SQLiteDatabase.openDatabase(
              srcFile.getAbsolutePath(),
              null,
              SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {

      } catch (Exception e) {
        Log.e(LOG_TAG, "Restore failed: Invalid database file");
        return false;
      }

      localDb.delete();
      new File(localDb.getPath() + "-wal").delete();
      new File(localDb.getPath() + "-shm").delete();

      try (InputStream in = new FileInputStream(srcFile);
          OutputStream out = new FileOutputStream(localDb)) {
        byte[] buffer = new byte[32768];
        int length;
        while ((length = in.read(buffer)) > 0) out.write(buffer, 0, length);
      }
      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "Full restore failed: " + e.getMessage());
      return false;
    }
  }

  private static void fastChannelCopy(Context context, File src, Uri dst) throws IOException {
    try (InputStream in = new FileInputStream(src);
        OutputStream out = context.getContentResolver().openOutputStream(dst)) {
      byte[] buffer = new byte[32768];
      int length;
      while ((length = in.read(buffer)) > 0) out.write(buffer, 0, length);
    }
  }

  private static DocumentFile ensureSubDir(DocumentFile parent, String name) {
    DocumentFile dir = parent.findFile(name);
    return (dir != null && dir.isDirectory()) ? dir : parent.createDirectory(name);
  }

  private static ProgressDialog createSyncProgress(Context context, String text) {
    ProgressDialog pd = new ProgressDialog(context, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    pd.setMessage(text);
    pd.setCancelable(false);
    return pd;
  }

  private static void notifySyncResult(Context context, boolean success, String sMsg, String eMsg) {
    Toast.makeText(context, success ? sMsg : eMsg, Toast.LENGTH_LONG).show();
  }
}
