package app.zipper.knot.hooks;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.TextView;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.utils.LineDBUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SearchResultCountHook implements BaseHook {

  private static final int LINE_SEARCH_DISPLAY_CAP = 500;
  private static final int LINE_SEARCH_CAPPED_COUNT = LINE_SEARCH_DISPLAY_CAP + 1;
  private static final long RECENT_COUNT_TTL_NANOS = 60_000_000_000L;
  private static final Map<Integer, Long> recentExactCounts = new ConcurrentHashMap<>();
  private static final Map<String, CountCache> recentFtsCounts = new ConcurrentHashMap<>();

  private static class CountCache {
    final int count;
    final long timestamp;

    CountCache(int count, long timestamp) {
      this.count = count;
      this.timestamp = timestamp;
    }
  }

  @Override
  public void hook(KnotConfig options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!options.searchMin1Char.enabled) return;

    LineVersion.Config config = LineVersion.get();
    if (config == null) return;

    hookFtsInChatCount(config, lpparam.classLoader);
    hookSearchResultCount(config, lpparam.classLoader);
    hookSearchResultTitleCount(config, lpparam.classLoader);
  }

  private void hookFtsInChatCount(LineVersion.Config config, ClassLoader classLoader) {
    if (config.chat.searchFtsInChatQueryClass.isEmpty()
        || config.chat.searchFtsQueryField.isEmpty()
        || config.chat.searchFtsChatIdField.isEmpty()
        || config.chat.searchFtsLimitField.isEmpty()) return;

    try {
      Class<?> queryClass =
          XposedHelpers.findClass(config.chat.searchFtsInChatQueryClass, classLoader);
      XposedBridge.hookAllMethods(
          queryClass,
          "invoke",
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              cacheFtsInChatCount(param, config);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook FTS count hook error: " + t);
    }
  }

  private void hookSearchResultCount(LineVersion.Config config, ClassLoader classLoader) {
    if (config.chat.searchResultClass.isEmpty() || config.chat.searchResultCountField.isEmpty())
      return;

    try {
      XposedHelpers.findAndHookConstructor(
          config.chat.searchResultClass,
          classLoader,
          String.class,
          int.class,
          String.class,
          List.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              replaceCappedResultCount(param, config);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook result count hook error: " + t);
    }
  }

  private void hookSearchResultTitleCount(LineVersion.Config config, ClassLoader classLoader) {
    if (config.chat.searchResultTitleViewHolderClass.isEmpty()
        || config.chat.searchResultTitleBindMethod.isEmpty()
        || config.chat.searchResultTitleBindingField.isEmpty()
        || config.chat.searchResultTitleTextViewField.isEmpty()) return;

    try {
      Class<?> titleViewHolderClass =
          XposedHelpers.findClass(config.chat.searchResultTitleViewHolderClass, classLoader);
      XposedBridge.hookAllMethods(
          titleViewHolderClass,
          config.chat.searchResultTitleBindMethod,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              replaceCappedHeaderCount(param, config);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook title count hook error: " + t);
    }
  }

  private static void cacheFtsInChatCount(
      XC_MethodHook.MethodHookParam param, LineVersion.Config config) {
    try {
      if (param.args == null || param.args.length == 0 || param.args[0] == null) return;

      Object result = param.getResult();
      if (!(result instanceof List<?>)) return;

      int limit = XposedHelpers.getIntField(param.thisObject, config.chat.searchFtsLimitField);
      if (((List<?>) result).size() < limit || limit < LINE_SEARCH_CAPPED_COUNT) return;

      String chatId =
          (String) XposedHelpers.getObjectField(param.thisObject, config.chat.searchFtsChatIdField);
      String ftsQuery =
          (String) XposedHelpers.getObjectField(param.thisObject, config.chat.searchFtsQueryField);
      if (chatId == null || ftsQuery == null || ftsQuery.isEmpty()) return;

      Integer count = fetchFtsInChatCount(param.args[0], chatId, ftsQuery);
      if (count == null || count <= LINE_SEARCH_DISPLAY_CAP) return;

      rememberRecentFtsCount(chatId, count);
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook cache FTS count error: " + t);
    }
  }

  private static Integer fetchFtsInChatCount(Object dbHandle, String chatId, String ftsQuery) {
    Object statement = null;
    try {
      statement =
          XposedHelpers.callMethod(
              dbHandle,
              "E1",
              "SELECT COUNT(*)"
                  + " FROM fts_message"
                  + " JOIN message_chat_relation"
                  + " ON fts_message.rowid = message_chat_relation.message_id"
                  + " WHERE message_chat_relation.chat_id = ?"
                  + " AND fts_message.formatted_message MATCH ?");
      XposedHelpers.callMethod(statement, "X1", 1, chatId);
      XposedHelpers.callMethod(statement, "X1", 2, ftsQuery);
      Object hasRow = XposedHelpers.callMethod(statement, "A1");
      if (!Boolean.TRUE.equals(hasRow)) return null;

      Object count = XposedHelpers.callMethod(statement, "getLong", 0);
      if (!(count instanceof Number)) return null;
      long value = ((Number) count).longValue();
      if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
      return (int) value;
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook fetch FTS count error: " + t);
      return null;
    } finally {
      if (statement != null) {
        try {
          XposedHelpers.callMethod(statement, "close");
        } catch (Throwable ignored) {
        }
      }
    }
  }

  private static void replaceCappedResultCount(
      XC_MethodHook.MethodHookParam param, LineVersion.Config config) {
    try {
      if (param.args == null || param.args.length < 3) return;
      if (!(param.args[1] instanceof Integer)) return;

      String chatId = (String) param.args[0];
      String keyword = (String) param.args[2];
      int currentCount = (Integer) param.args[1];

      if (currentCount == LINE_SEARCH_CAPPED_COUNT && !isOneCharacterKeyword(keyword)) {
        Integer actualCount = resolveActualCount(chatId, keyword);
        if (actualCount != null && actualCount > LINE_SEARCH_DISPLAY_CAP) {
          XposedHelpers.setIntField(
              param.thisObject, config.chat.searchResultCountField, actualCount);
          param.args[1] = actualCount;
          rememberExactCount(actualCount);
          return;
        }
      }

      if (currentCount > LINE_SEARCH_DISPLAY_CAP) rememberExactCount(currentCount);
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook replace result count error: " + t);
    }
  }

  private static Integer resolveActualCount(String chatId, String keyword) {
    if (chatId == null || !hasMeaningfulKeyword(keyword)) return null;

    String senderMid = SearchByMemberHook.getActiveFilterMid(chatId);
    if (senderMid != null && !senderMid.isEmpty()) {
      return fetchLocalLikeMatchCount(chatId, keyword);
    }

    Integer actualCount = consumeRecentFtsCount(chatId);
    if (actualCount != null) return actualCount;
    return fetchLocalLikeMatchCount(chatId, keyword);
  }

  private static void replaceCappedHeaderCount(
      XC_MethodHook.MethodHookParam param, LineVersion.Config config) {
    try {
      if (param.args == null || param.args.length < 3 || param.args[0] == null) return;

      int count = XposedHelpers.getIntField(param.args[0], "b");
      if (!shouldShowExactHeaderCount(count)) return;

      Object title = XposedHelpers.getObjectField(param.args[0], "a");
      if (!(title instanceof String)) return;

      Object binding =
          XposedHelpers.getObjectField(param.thisObject, config.chat.searchResultTitleBindingField);
      if (binding == null) return;

      Object titleView =
          XposedHelpers.getObjectField(binding, config.chat.searchResultTitleTextViewField);
      if (!(titleView instanceof TextView)) return;

      boolean rtl = Boolean.TRUE.equals(param.args[2]);
      ((TextView) titleView).setText(formatSearchResultTitle((String) title, count, rtl));
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook replace title count error: " + t);
    }
  }

  private static boolean shouldShowExactHeaderCount(int count) {
    if (count <= LINE_SEARCH_DISPLAY_CAP) return false;
    if (count != LINE_SEARCH_CAPPED_COUNT) return true;
    return hasRecentExactCount(count);
  }

  private static String formatSearchResultTitle(String title, int count, boolean rtl) {
    StringBuilder sb = new StringBuilder(title);
    if (rtl) sb.append('\u200f');
    sb.append(' ').append(count);
    return sb.toString();
  }

  private static void rememberExactCount(int count) {
    if (count <= LINE_SEARCH_DISPLAY_CAP) return;

    long now = System.nanoTime();
    recentExactCounts.put(count, now);
    pruneRecentCounts(now);
  }

  private static boolean hasRecentExactCount(int count) {
    Long timestamp = recentExactCounts.get(count);
    if (timestamp == null) return false;

    long now = System.nanoTime();
    if (now - timestamp <= RECENT_COUNT_TTL_NANOS) return true;

    recentExactCounts.remove(count, timestamp);
    return false;
  }

  private static void rememberRecentFtsCount(String chatId, int count) {
    long now = System.nanoTime();
    recentFtsCounts.put(chatId, new CountCache(count, now));
    pruneRecentCounts(now);
  }

  private static Integer consumeRecentFtsCount(String chatId) {
    CountCache cache = recentFtsCounts.remove(chatId);
    if (cache == null) return null;

    if (System.nanoTime() - cache.timestamp > RECENT_COUNT_TTL_NANOS) return null;
    return cache.count;
  }

  private static void pruneRecentCounts(long now) {
    for (Map.Entry<Integer, Long> entry : recentExactCounts.entrySet()) {
      if (now - entry.getValue() > RECENT_COUNT_TTL_NANOS) {
        recentExactCounts.remove(entry.getKey(), entry.getValue());
      }
    }
    for (Map.Entry<String, CountCache> entry : recentFtsCounts.entrySet()) {
      if (now - entry.getValue().timestamp > RECENT_COUNT_TTL_NANOS) {
        recentFtsCounts.remove(entry.getKey(), entry.getValue());
      }
    }
  }

  private static Integer fetchLocalLikeMatchCount(String chatId, String keyword) {
    try {
      Context context = android.app.AndroidAppHelper.currentApplication();
      if (context == null) return null;

      File dbFile = context.getDatabasePath("naver_line");
      if (!dbFile.exists()) return null;

      String senderMid = SearchByMemberHook.getActiveFilterMid(chatId);
      StringBuilder sql =
          new StringBuilder(
              "SELECT COUNT(*) FROM chat_history"
                  + " WHERE chat_id = ?"
                  + " AND content IS NOT NULL"
                  + " AND content LIKE ? ESCAPE '\\'");
      List<String> args = new ArrayList<>();
      args.add(chatId);
      args.add("%" + escapeSqlLike(keyword) + "%");

      if (senderMid != null && !senderMid.isEmpty()) {
        String myMid = LineDBUtils.getMyMid();
        if (senderMid.equals(myMid)) {
          sql.append(" AND (from_mid = ? OR from_mid IS NULL)");
        } else {
          sql.append(" AND from_mid = ?");
        }
        args.add(senderMid);
      }

      try (SQLiteDatabase db =
              SQLiteDatabase.openDatabase(
                  dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
          Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
        if (!cursor.moveToFirst()) return null;
        long count = cursor.getLong(0);
        if (count > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) count;
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchResultCountHook fetch local LIKE count error: " + t);
      return null;
    }
  }

  private static String escapeSqlLike(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' || c == '%' || c == '_') escaped.append('\\');
      escaped.append(c);
    }
    return escaped.toString();
  }

  private static boolean hasMeaningfulKeyword(String keyword) {
    if (keyword == null || keyword.isEmpty()) return false;

    for (int i = 0; i < keyword.length(); ) {
      int codePoint = keyword.codePointAt(i);
      i += Character.charCount(codePoint);

      if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) continue;
      if (Character.isISOControl(codePoint)) continue;
      if (Character.getType(codePoint) == Character.FORMAT) continue;
      return true;
    }
    return false;
  }

  private static boolean isOneCharacterKeyword(String keyword) {
    if (keyword == null) return false;

    int meaningfulCodePoints = 0;
    for (int i = 0; i < keyword.length(); ) {
      int codePoint = keyword.codePointAt(i);
      i += Character.charCount(codePoint);

      if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) continue;
      if (Character.isISOControl(codePoint)) continue;
      if (Character.getType(codePoint) == Character.FORMAT) continue;

      meaningfulCodePoints++;
      if (meaningfulCodePoints > 1) return false;
    }
    return meaningfulCodePoints == 1;
  }
}
