package app.zipper.knot.utils;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    if (chatId == null)
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
            db.rawQuery("SELECT chat_name FROM chats WHERE chat_id = ? LIMIT 1",
                        new String[] {chatId});
        if (cursor.moveToFirst()) {
          String name = cursor.getString(0);
          cursor.close();
          db.close();
          return name;
        }
        cursor.close();
        cursor = db.rawQuery(
            "SELECT chat_name FROM chat_history WHERE chat_id = ? LIMIT 1",
            new String[] {chatId});
        if (cursor.moveToFirst()) {
          String name = cursor.getString(0);
          cursor.close();
          db.close();
          return name;
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable ignored) {
    }
    return null;
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
          return resolveMessageText(content, parameter);
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable t) {
    }
    return null;
  }

  public static String resolveMessageText(String content, String parameter) {
    if (content != null && !content.isEmpty() && !"null".equals(content)) {
      return content;
    }

    if (parameter != null) {
      if (parameter.contains("STKPKGID")) {
        return ModuleStrings.MSG_STICKER;
      } else if (parameter.contains("IMAGE") || parameter.contains("image")) {
        return ModuleStrings.MSG_IMAGE;
      } else if (parameter.contains("VIDEO") || parameter.contains("video")) {
        return ModuleStrings.MSG_VIDEO;
      } else if (parameter.contains("FILE") || parameter.contains("file")) {
        return ModuleStrings.MSG_FILE;
      } else if (parameter.contains("LOCATION") ||
                 parameter.contains("location")) {
        return ModuleStrings.MSG_LOCATION;
      }
    }
    return null;
  }

  public static String getMyMid() {
    try {
      Context context = AndroidAppHelper.currentApplication();
      if (context == null)
        return null;
      File dbFile = context.getDatabasePath("naver_line");
      if (dbFile.exists()) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = db.rawQuery("SELECT mid FROM user_profile", null);
        if (cursor.moveToFirst()) {
          String mid = cursor.getString(0);
          cursor.close();
          db.close();
          return mid;
        }
        cursor.close();
        cursor = db.rawQuery("SELECT mid FROM profile", null);
        if (cursor.moveToFirst()) {
          String mid = cursor.getString(0);
          cursor.close();
          db.close();
          return mid;
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  public static class MessageRecord {
    public final String id;
    public final String text;
    public final String senderMid;
    public final String senderName;
    public final String chatId;
    public final String chatName;
    public final String timestamp;

    public MessageRecord(String id, String text, String senderMid,
                         String senderName, String chatId, String chatName,
                         String timestamp) {
      this.id = id;
      this.text = text;
      this.senderMid = senderMid;
      this.senderName = senderName;
      this.chatId = chatId;
      this.chatName = chatName;
      this.timestamp = timestamp;
    }
  }

  public static List<MessageRecord>
  fetchMessagesForRecording(String targetChatId, String latestMsgId,
                            String myMid, boolean includeOthers,
                            long minMsgId) {
    List<MessageRecord> results = new ArrayList<>();
    if (targetChatId == null || latestMsgId == null)
      return results;

    try {
      Context context = AndroidAppHelper.currentApplication();
      if (context == null)
        return results;

      File dbFile = context.getDatabasePath("naver_line");
      if (!dbFile.exists())
        return results;

      SQLiteDatabase db = SQLiteDatabase.openDatabase(
          dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

      try {
        String currentChatName = resolveChatName(targetChatId);
        SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        List<String> queryArgs = new ArrayList<>();
        queryArgs.add(targetChatId);
        queryArgs.add(latestMsgId);

        String sql;
        if (minMsgId == -1) {
          sql = "SELECT server_id, content, parameter, from_mid, created_time "
                + "FROM chat_history WHERE chat_id = ? AND server_id = ?";
        } else {
          sql = "SELECT server_id, content, parameter, from_mid, created_time "
                + "FROM chat_history WHERE chat_id = ? AND (server_id = ? OR "
                + "(CAST(server_id AS INTEGER) < CAST(? AS INTEGER) AND "
                + "CAST(server_id AS INTEGER) > ?)) ";
          queryArgs.add(latestMsgId);
          queryArgs.add(String.valueOf(minMsgId));

          if (!includeOthers && myMid != null) {
            sql += " AND from_mid = ? ";
            queryArgs.add(myMid);
          }
          sql += " ORDER BY CAST(server_id AS INTEGER) DESC LIMIT 100";
        }

        Cursor cursor = db.rawQuery(sql, queryArgs.toArray(new String[0]));
        try {
          while (cursor.moveToNext()) {
            String mId = cursor.getString(0);
            String rawContent = cursor.getString(1);
            String rawParam = cursor.getString(2);
            String fromMid = cursor.getString(3);
            long timeLong = cursor.getLong(4);

            if (!includeOthers && fromMid != null && !fromMid.equals(myMid))
              continue;

            String resolvedText = resolveMessageText(rawContent, rawParam);
            String senderName = resolveMemberName(fromMid);
            String formattedTime = dateFormat.format(new Date(timeLong));

            results.add(new MessageRecord(
                mId, resolvedText != null ? resolvedText : "",
                fromMid != null ? fromMid : "",
                senderName != null ? senderName : "Unknown", targetChatId,
                currentChatName != null ? currentChatName : "Unknown",
                formattedTime));
          }
        } finally {
          cursor.close();
        }
      } finally {
        db.close();
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Message resolution error: " + t.getMessage());
    }
    return results;
  }
}
