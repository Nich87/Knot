package app.zipper.knot.utils;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XposedBridge;
import java.io.File;

public class LineDBUtils {

  public static String resolveMemberName(String mid) {
    if (mid == null)
      return null;
    try {
      Context context = AndroidAppHelper.currentApplication();
      if (context == null)
        return null;
      File dbFile = context.getDatabasePath("contact");
      if (dbFile.exists()) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor =
            db.rawQuery("SELECT profile_name FROM contacts WHERE mid = ?",
                        new String[] {mid});
        if (cursor.moveToFirst()) {
          String name = cursor.getString(0);
          cursor.close();
          db.close();
          return name;
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: DB name resolution failed: " + t);
    }
    return null;
  }

  public static String resolveChatName(String chatId) {
    return resolveMemberName(chatId);
  }

  public static String resolveMessageContent(String serverId) {
    if (serverId == null)
      return null;
    try {
      Context context = AndroidAppHelper.currentApplication();
      if (context == null)
        return null;
      File dbFile = context.getDatabasePath("naver_line");
      if (dbFile.exists()) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = db.rawQuery(
            "SELECT content, parameter FROM chat_history WHERE server_id = ?",
            new String[] {serverId});
        if (cursor.moveToFirst()) {
          String content = cursor.getString(0);
          String parameter = cursor.getString(1);
          cursor.close();
          db.close();

          if (content != null && !content.isEmpty() &&
              !"null".equals(content)) {
            return content;
          }

          if (parameter != null) {
            if (parameter.contains("STKPKGID")) {
              return ModuleStrings.MSG_STICKER;
            } else if (parameter.contains("IMAGE") ||
                       parameter.contains("image")) {
              return ModuleStrings.MSG_IMAGE;
            } else if (parameter.contains("VIDEO") ||
                       parameter.contains("video")) {
              return ModuleStrings.MSG_VIDEO;
            } else if (parameter.contains("FILE") ||
                       parameter.contains("file")) {
              return ModuleStrings.MSG_FILE;
            } else if (parameter.contains("LOCATION") ||
                       parameter.contains("location")) {
              return ModuleStrings.MSG_LOCATION;
            }
          }
          return null;
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable t) {
    }
    return null;
  }

  public static String resolveMessageSender(String serverId) {
    if (serverId == null)
      return null;
    try {
      Context context = AndroidAppHelper.currentApplication();
      if (context == null)
        return null;
      File dbFile = context.getDatabasePath("naver_line");
      if (dbFile.exists()) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor =
            db.rawQuery("SELECT from_mid FROM chat_history WHERE server_id = ?",
                        new String[] {serverId});
        if (cursor.moveToFirst()) {
          String fromMid = cursor.getString(0);
          cursor.close();
          db.close();
          return fromMid;
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable ignored) {
    }
    return null;
  }
}
