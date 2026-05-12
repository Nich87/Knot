package app.zipper.knot.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.LineDBUtils;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class SearchByMemberHook implements BaseHook {

  private static final String MEMBER_ICON_TAG = "knot_search_member_icon";
  private static final String TOOLTIP_KEY = "knot_search_member_tooltip_shown";
  private static final String LINE_PKG = "jp.naver.line.android";

  private static final Map<String, String> chatMemberFilter = new ConcurrentHashMap<>();
  private static final Map<String, String> chatMemberFilterName = new ConcurrentHashMap<>();
  private static final WeakHashMap<Activity, Object> searchHeaderMap = new WeakHashMap<>();
  private static final Set<String> pendingFetchAll = ConcurrentHashMap.newKeySet();
  private static final Map<String, List<Long>> cachedResults = new ConcurrentHashMap<>();
  private static final WeakHashMap<EditText, Boolean> watchedEditTexts = new WeakHashMap<>();
  private static final ThreadLocal<Boolean> creatingResult = new ThreadLocal<>();

  private static volatile PopupWindow activeTooltip = null;
  private static ClassLoader appClassLoader;
  private static String searchKeywordEventClass;

  public static boolean hasActiveFilter() {
    return !chatMemberFilter.isEmpty();
  }

  @Override
  public void hook(KnotConfig options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!options.searchByMember.enabled) return;

    appClassLoader = lpparam.classLoader;
    LineVersion.Config config = LineVersion.get();
    if (config == null || !isValidConfig(config)) return;

    searchKeywordEventClass = config.chat.searchKeywordEventClass;

    setupHeaderHook(config, lpparam.classLoader);
    setupSearchBoxHook(config, lpparam.classLoader);
    setupSearchResultHook(config, lpparam.classLoader);
    setupSearchResultWrapperHook(config, lpparam.classLoader);
  }

  private boolean isValidConfig(LineVersion.Config config) {
    return !config.chat.searchHeaderHelperClass.isEmpty()
        && !config.chat.searchResultClass.isEmpty()
        && !config.chat.searchResultWrapperClass.isEmpty()
        && !config.chat.searchBoxViewClass.isEmpty()
        && !config.chat.searchKeywordEventClass.isEmpty();
  }

  private void setupHeaderHook(LineVersion.Config config, ClassLoader classLoader) {
    try {
      Class<?> helperCls =
          XposedHelpers.findClass(config.chat.searchHeaderHelperClass, classLoader);
      XposedHelpers.findAndHookMethod(
          helperCls,
          "a",
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              handleHeaderCreated(param.thisObject, config);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchByMember setupHeaderHook error: " + t);
    }
  }

  private void handleHeaderCreated(Object helper, LineVersion.Config config) {
    try {
      View headerView = (View) XposedHelpers.getObjectField(helper, "a");
      if (headerView == null) return;

      Activity activity = unwrapActivity(headerView.getContext());
      if (activity == null) return;

      searchHeaderMap.put(activity, helper);
      setupTextWatcherIfNeeded(activity, config);
      injectMemberSearchIcon(activity, headerView, config);
    } catch (Throwable t) {
      XposedBridge.log("Knot: handleHeaderCreated error: " + t);
    }
  }

  private void setupTextWatcherIfNeeded(Activity activity, LineVersion.Config config) {
    EditText et = getSearchEditText(activity);
    if (et == null) return;
    synchronized (watchedEditTexts) {
      if (!watchedEditTexts.containsKey(et)) {
        watchedEditTexts.put(et, Boolean.TRUE);
        addShowAllTextWatcher(et, activity, config);
      }
    }
  }

  private void injectMemberSearchIcon(
      Activity activity, View headerView, LineVersion.Config config) {
    int containerId =
        activity
            .getResources()
            .getIdentifier("chat_ui_header_search_box_container", "id", LINE_PKG);
    if (containerId == 0) return;

    View container = headerView.findViewById(containerId);
    if (!(container instanceof FrameLayout) || ((FrameLayout) container).getChildCount() == 0)
      return;

    View searchBoxView = ((FrameLayout) container).getChildAt(0);
    int iconId = activity.getResources().getIdentifier("v2_common_search_icon", "id", LINE_PKG);
    if (iconId == 0) return;

    ImageView icon = searchBoxView.findViewById(iconId);
    if (icon == null || MEMBER_ICON_TAG.equals(icon.getTag())) return;

    icon.setTag(MEMBER_ICON_TAG);
    icon.setEnabled(true);
    icon.setOnClickListener(v -> onMemberIconClicked(activity, config, icon));

    updateIconVisualState(activity, config, icon);
    showSearchMemberTooltip(activity, icon);
  }

  private void onMemberIconClicked(Activity activity, LineVersion.Config config, ImageView icon) {
    dismissTooltip(true);
    try {
      String chatId = resolveChatId(activity, config);
      if (chatId == null || chatId.isEmpty()) return;

      if (chatMemberFilter.containsKey(chatId)) {
        clearMemberFilter(activity, chatId, icon);
      } else {
        List<LineDBUtils.MemberInfo> members = LineDBUtils.getChatMembers(chatId);
        if (members.isEmpty()) {
          Toast.makeText(activity, ModuleStrings.SEARCH_BY_MEMBER_EMPTY, Toast.LENGTH_SHORT).show();
          return;
        }
        showMemberPicker(activity, chatId, members, icon);
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: onMemberIconClicked error: " + t);
    }
  }

  private void updateIconVisualState(Activity activity, LineVersion.Config config, ImageView icon) {
    String chatId = resolveChatId(activity, config);
    if (chatId != null && chatMemberFilter.containsKey(chatId)) {
      icon.setColorFilter(0xFF2196F3, android.graphics.PorterDuff.Mode.SRC_ATOP);
      String name = chatMemberFilterName.get(chatId);
      if (name != null) {
        EditText et = getSearchEditText(activity);
        if (et != null) et.setHint(ModuleStrings.SEARCH_BY_MEMBER_FILTERING + name);
      }
    }
  }

  private void setupSearchBoxHook(LineVersion.Config config, ClassLoader classLoader) {
    try {
      Class<?> sbCls = XposedHelpers.findClass(config.chat.searchBoxViewClass, classLoader);
      XposedHelpers.findAndHookMethod(
          sbCls,
          "setEditingLayout",
          boolean.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              ImageView icon = (ImageView) XposedHelpers.getObjectField(param.thisObject, "d");
              if (icon != null && MEMBER_ICON_TAG.equals(icon.getTag())) icon.setEnabled(true);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: setupSearchBoxHook error: " + t);
    }
  }

  private void setupSearchResultHook(LineVersion.Config config, ClassLoader classLoader) {
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
            protected void beforeHookedMethod(MethodHookParam param) {
              handleSearchResultCreation(param);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: setupSearchResultHook error: " + t);
    }
  }

  @SuppressWarnings("unchecked")
  private void handleSearchResultCreation(XC_MethodHook.MethodHookParam param) {
    try {
      if (Boolean.TRUE.equals(creatingResult.get())) return;

      String chatId = (String) param.args[0];
      if (chatId == null) return;

      String senderMid = chatMemberFilter.get(chatId);
      if (senderMid == null) return;

      List<Long> ids = (List<Long>) param.args[3];
      if (ids == null || ids.isEmpty()) {
        String keyword = (String) param.args[2];
        handleEmptyIds(chatId, senderMid, keyword, param);
      } else {
        List<Long> filtered = filterLocalIdsByMid(ids, senderMid);
        cachedResults.put(chatId, filtered);
        param.args[3] = filtered;
        param.args[1] = filtered.size();
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: handleSearchResultCreation error: " + t);
    }
  }

  private void handleEmptyIds(
      String chatId, String senderMid, String keyword, XC_MethodHook.MethodHookParam param) {
    boolean emptyKeyword = keyword == null || keyword.trim().isEmpty();
    if (pendingFetchAll.remove(chatId) || emptyKeyword) {
      List<Long> allIds = fetchAllMemberLocalIds(chatId, senderMid);
      cachedResults.put(chatId, allIds);
      param.args[3] = allIds;
      param.args[1] = allIds.size();
    } else {
      List<Long> cached = cachedResults.get(chatId);
      if (cached != null && !cached.isEmpty()) {
        param.args[3] = cached;
        param.args[1] = cached.size();
      }
    }
  }

  private void setupSearchResultWrapperHook(LineVersion.Config config, ClassLoader classLoader) {
    try {
      Class<?> resultCls = XposedHelpers.findClass(config.chat.searchResultClass, classLoader);
      XposedHelpers.findAndHookConstructor(
          config.chat.searchResultWrapperClass,
          classLoader,
          String.class,
          String.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              handleSearchResultWrapperCreated(param, resultCls);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: setupSearchResultWrapperHook error: " + t);
    }
  }

  private void handleSearchResultWrapperCreated(
      XC_MethodHook.MethodHookParam param, Class<?> resultCls) {
    try {
      String chatId = (String) param.args[0];
      if (chatId == null) return;
      String senderMid = chatMemberFilter.get(chatId);
      if (senderMid == null) return;

      List<Long> ids =
          pendingFetchAll.remove(chatId)
              ? fetchAllMemberLocalIds(chatId, senderMid)
              : cachedResults.get(chatId);

      if (ids == null) return;
      if (pendingFetchAll.contains(chatId)) cachedResults.put(chatId, ids);

      creatingResult.set(Boolean.TRUE);
      try {
        Object result = XposedHelpers.newInstance(resultCls, chatId, ids.size(), "", ids);
        XposedHelpers.setObjectField(param.thisObject, "c", Optional.of(result));
      } finally {
        creatingResult.remove();
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: handleSearchResultWrapperCreated error: " + t);
    }
  }

  private static List<Long> filterLocalIdsByMid(List<Long> ids, String senderMid) {
    try {
      android.app.Application app = android.app.AndroidAppHelper.currentApplication();
      if (app == null) return ids;
      File dbFile = app.getDatabasePath("naver_line");
      if (!dbFile.exists()) return ids;

      try (SQLiteDatabase db =
          SQLiteDatabase.openDatabase(
              dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
        String myMid = LineDBUtils.getMyMid();
        boolean isOwn = senderMid.equals(myMid);

        StringBuilder sb = new StringBuilder("SELECT id FROM chat_history WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
          if (i > 0) sb.append(",");
          sb.append("?");
        }
        sb.append(isOwn ? ") AND (from_mid = ? OR from_mid IS NULL)" : ") AND from_mid = ?");

        String[] args = new String[ids.size() + 1];
        for (int i = 0; i < ids.size(); i++) args[i] = String.valueOf(ids.get(i));
        args[ids.size()] = senderMid;

        Set<Long> kept = new HashSet<>();
        try (Cursor cursor = db.rawQuery(sb.toString(), args)) {
          while (cursor.moveToNext()) kept.add(cursor.getLong(0));
        }

        List<Long> result = new ArrayList<>();
        for (Long id : ids) if (kept.contains(id)) result.add(id);
        return result;
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: filterLocalIdsByMid error: " + t);
      return ids;
    }
  }

  private static List<Long> fetchAllMemberLocalIds(String chatId, String senderMid) {
    try {
      android.app.Application app = android.app.AndroidAppHelper.currentApplication();
      if (app == null) return new ArrayList<>();
      File dbFile = app.getDatabasePath("naver_line");
      if (!dbFile.exists()) return new ArrayList<>();

      String myMid = LineDBUtils.getMyMid();
      boolean isOwn = senderMid.equals(myMid);
      String sql =
          isOwn
              ? "SELECT id FROM chat_history WHERE chat_id = ? AND (from_mid = ? OR from_mid IS NULL) ORDER BY id DESC"
              : "SELECT id FROM chat_history WHERE chat_id = ? AND from_mid = ? ORDER BY id DESC";

      try (SQLiteDatabase db =
              SQLiteDatabase.openDatabase(
                  dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
          Cursor cursor = db.rawQuery(sql, new String[] {chatId, senderMid})) {
        List<Long> result = new ArrayList<>();
        while (cursor.moveToNext()) result.add(cursor.getLong(0));
        return result;
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: fetchAllMemberLocalIds error: " + t);
      return new ArrayList<>();
    }
  }

  private void showMemberPicker(
      Activity activity, String chatId, List<LineDBUtils.MemberInfo> members, ImageView icon) {
    String[] names = members.stream().map(m -> m.name).toArray(String[]::new);
    int themeId =
        isContextDark(activity)
            ? AlertDialog.THEME_DEVICE_DEFAULT_DARK
            : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
    new AlertDialog.Builder(activity, themeId)
        .setTitle(ModuleStrings.SEARCH_BY_MEMBER_TITLE)
        .setItems(
            names,
            (d, i) -> {
              LineDBUtils.MemberInfo member = members.get(i);
              chatMemberFilter.put(chatId, member.mid);
              chatMemberFilterName.put(chatId, member.name);
              icon.setColorFilter(0xFF2196F3, android.graphics.PorterDuff.Mode.SRC_ATOP);
              EditText et = getSearchEditText(activity);
              if (et != null) et.setHint(ModuleStrings.SEARCH_BY_MEMBER_FILTERING + member.name);
              triggerReSearch(activity, chatId);
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void clearMemberFilter(Activity activity, String chatId, ImageView icon) {
    pendingFetchAll.remove(chatId);
    cachedResults.remove(chatId);
    chatMemberFilterName.remove(chatId);
    chatMemberFilter.remove(chatId);
    icon.clearColorFilter();
    EditText et = getSearchEditText(activity);
    if (et != null) et.setHint("");
    triggerReSearch(activity, null);
    Toast.makeText(activity, ModuleStrings.SEARCH_BY_MEMBER_FILTER_CLEARED, Toast.LENGTH_SHORT)
        .show();
  }

  private EditText getSearchEditText(Activity activity) {
    try {
      Object helper = searchHeaderMap.get(activity);
      if (helper == null) return null;
      Object controller = XposedHelpers.getObjectField(helper, "i");
      if (controller == null) return null;
      Object sbView = XposedHelpers.callMethod(controller, "d");
      if (sbView == null) return null;
      return (EditText) XposedHelpers.getObjectField(sbView, "b");
    } catch (Throwable t) {
      XposedBridge.log("Knot: getSearchEditText error: " + t);
      return null;
    }
  }

  private void addShowAllTextWatcher(EditText et, Activity activity, LineVersion.Config config) {
    et.addTextChangedListener(
        new android.text.TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          @Override
          public void onTextChanged(CharSequence s, int st, int b, int c) {}

          @Override
          public void afterTextChanged(android.text.Editable s) {
            if (s.length() != 0) return;
            String chatId = resolveChatId(activity, config);
            if (chatId != null && chatMemberFilter.containsKey(chatId))
              triggerReSearch(activity, chatId);
          }
        });
  }

  private void triggerReSearch(Activity activity, String chatId) {
    try {
      if (chatId != null) pendingFetchAll.add(chatId);
      Object helper = searchHeaderMap.get(activity);
      if (helper == null) return;

      Object eventBus = XposedHelpers.getObjectField(helper, "b");
      Object controller = XposedHelpers.getObjectField(helper, "i");
      if (controller == null) return;
      Object sbView = XposedHelpers.callMethod(controller, "d");
      if (sbView == null) return;
      String currentText = (String) XposedHelpers.callMethod(sbView, "getSearchText");
      if (currentText == null) currentText = "";

      boolean filterActive = chatId != null && chatMemberFilter.containsKey(chatId);
      String keyword = filterActive ? "\u200B" : currentText;
      String sentinel = (keyword.equals("\u200B") || currentText.isEmpty()) ? "\u200B\u200B" : "";

      Class<?> evtCls = XposedHelpers.findClass(searchKeywordEventClass, appClassLoader);
      XposedHelpers.callMethod(eventBus, "b", XposedHelpers.newInstance(evtCls, sentinel));
      XposedHelpers.callMethod(eventBus, "b", XposedHelpers.newInstance(evtCls, keyword));
    } catch (Throwable t) {
      XposedBridge.log("Knot: triggerReSearch error: " + t);
    }
  }

  private static void showSearchMemberTooltip(Activity activity, View anchor) {
    if (SettingsStore.get(TOOLTIP_KEY, false)) return;
    if (activeTooltip != null && activeTooltip.isShowing()) return;

    anchor.post(
        () -> {
          if (anchor.getWidth() == 0 || !anchor.isShown()) return;
          if (SettingsStore.get(TOOLTIP_KEY, false)) return;
          try {
            LineVersion.Config cfg = LineVersion.get();
            if (cfg == null) return;
            String pkg = cfg.linePkg;
            float dp = activity.getResources().getDisplayMetrics().density;

            ImageView arrowView = new ImageView(activity);
            int arrowId =
                activity.getResources().getIdentifier(cfg.res.resTooltipArrowUp, "drawable", pkg);
            if (arrowId != 0) arrowView.setImageResource(arrowId);
            arrowView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

            LinearLayout.LayoutParams arrowLp =
                new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            arrowLp.gravity = Gravity.START;
            arrowLp.leftMargin =
                Math.max(0, anchor.getWidth() / 2 - arrowView.getMeasuredWidth() / 2);

            TextView text = new TextView(activity);
            text.setText(ModuleStrings.TOOLTIP_SEARCH_BY_MEMBER);
            text.setTextColor(Color.WHITE);
            text.setTextSize(13f);
            int ph = (int) (12 * dp), pv = (int) (7 * dp);
            text.setPadding(ph, pv, ph, pv);
            int bgId =
                activity
                    .getResources()
                    .getIdentifier(cfg.res.resTooltipBackground, "drawable", pkg);
            if (bgId != 0) text.setBackgroundResource(bgId);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            container.addView(arrowView, arrowLp);
            container.addView(
                text,
                new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

            PopupWindow popup =
                new PopupWindow(
                    container,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            popup.setOutsideTouchable(true);
            popup.setFocusable(false);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            container.setOnClickListener(v -> dismissTooltip(true));
            popup.showAsDropDown(anchor, 0, 0);
            activeTooltip = popup;
          } catch (Throwable t) {
            XposedBridge.log("Knot: SearchByMember tooltip error: " + t);
          }
        });
  }

  private static void dismissTooltip(boolean markShown) {
    PopupWindow p = activeTooltip;
    activeTooltip = null;
    if (p != null)
      try {
        p.dismiss();
      } catch (Throwable ignored) {
      }
    if (markShown) SettingsStore.save(TOOLTIP_KEY, true);
  }

  private Activity unwrapActivity(Context ctx) {
    while (ctx instanceof ContextWrapper) {
      if (ctx instanceof Activity) return (Activity) ctx;
      ctx = ((ContextWrapper) ctx).getBaseContext();
    }
    return null;
  }

  private String resolveChatId(Activity activity, LineVersion.Config config) {
    try {
      android.os.Bundle extras = activity.getIntent().getExtras();
      if (extras != null) {
        android.os.Parcelable req = extras.getParcelable("chat-history-request");
        if (req != null) {
          Object id = XposedHelpers.callMethod(req, "getChatId");
          if (id instanceof String && !((String) id).isEmpty()) return (String) id;
        }
      }
    } catch (Throwable ignored) {
    }
    for (String key : new String[] {"chatId", "chat_id", "extra-chat-id"}) {
      String id = activity.getIntent().getStringExtra(key);
      if (id != null && !id.isEmpty()) return id;
    }
    if (config != null) {
      try {
        Object field = XposedHelpers.getObjectField(activity, config.chat.chatIdField);
        if (field != null)
          return (String) XposedHelpers.callMethod(field, config.chat.methodGetChatId);
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  private static boolean isContextDark(Context ctx) {
    try {
      int nightMode =
          ctx.getResources().getConfiguration().uiMode
              & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
      return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
