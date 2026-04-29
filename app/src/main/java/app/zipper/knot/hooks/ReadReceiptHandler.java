package app.zipper.knot.hooks;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReadReceiptHandler implements BaseHook {

  private static volatile long bypassExpiry = 0L;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    try {
      Class<?> readQueueCls =
          lpparam.classLoader.loadClass(cfg.readReceipt.readReceiptQueueClass);
      XposedBridge.hookAllMethods(
          readQueueCls, cfg.readReceipt.methodEnqueueReadReceipt,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              try {
                if (!SettingsStore.get("record_read_history", false))
                  return;
                if (param.args == null || param.args.length < 4)
                  return;
                long createdTime = (long)param.args[0];
                String chatId = (String)param.args[1];
                String senderMid = (String)param.args[2];
                long lastMsgId = (long)param.args[3];
                if (chatId == null || senderMid == null)
                  return;
                recordReadReceipt(chatId, senderMid, String.valueOf(lastMsgId),
                                  createdTime);
              } catch (Throwable t) {
                XposedBridge.log("Knot: record error: " + t);
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook " +
                       cfg.readReceipt.readReceiptQueueClass + "#" +
                       cfg.readReceipt.methodEnqueueReadReceipt + ": " + t);
    }

    try {
      Class<?> thriftCls =
          lpparam.classLoader.loadClass(cfg.thrift.talkServiceClientImplClass);
      XposedBridge.hookAllMethods(
          thriftCls, cfg.thrift.v1, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (param.args == null || param.args[0] == null)
                return;
              String method = param.args[0].toString();

              boolean isTarget = method.contains("sendChatChecked") ||
                                 method.contains("Checked") ||
                                 method.contains("ReadReceipt");
              if (!isTarget)
                return;

              if (!SettingsStore.get("prevent_read_state", true))
                return;
              if (SettingsStore.get("send_mark_state", false) &&
                  bypassExpiry > System.currentTimeMillis())
                return;

              param.args[0] = "KNOT_NOP";
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      Class<?> managerCls = null;
      if (cfg.readReceipt.readReceiptManagerClass != null &&
          !cfg.readReceipt.readReceiptManagerClass.isEmpty()) {
        try {
          managerCls = XposedHelpers.findClass(
              cfg.readReceipt.readReceiptManagerClass, lpparam.classLoader);
        } catch (Throwable t) {
          XposedBridge.log("Knot: Configured manager class not found: " +
                           cfg.readReceipt.readReceiptManagerClass);
        }
      }

      if (managerCls == null) {
        managerCls = app.zipper.knot.utils.DexScanner.findClass(
            lpparam.classLoader, lpparam.appInfo.sourceDir,
            c -> containsServiceReference(c) && containsMarkAsReadLogic(c));

        if (managerCls != null) {
          XposedBridge.log("Knot: Discovered manager class via scan: " +
                           managerCls.getName() +
                           " (Please update VersionConfig)");
        }
      }

      if (managerCls != null) {
        XposedBridge.hookAllMethods(
            managerCls, cfg.readReceipt.methodSendReadReceipt,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (!SettingsStore.get("prevent_read_state", true))
                  return;

                java.lang.reflect.Method m =
                    (java.lang.reflect.Method)param.method;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3 &&
                    params[0] == long.class &&params[1] == String.class &&
                    params[2] == boolean.class) {
                  if (SettingsStore.get("send_mark_state", false) &&
                      bypassExpiry > System.currentTimeMillis())
                    return;
                  param.setResult(null);
                }
              }
            });
        XposedBridge.log("Knot: Successfully hooked manager class: " +
                         managerCls.getName());

        XposedBridge.hookAllMethods(
            managerCls, cfg.readReceipt.methodExecuteReadReceiptAsync,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (SettingsStore.get("prevent_read_state", true) &&
                    SettingsStore.get("send_mark_state", false)) {
                  Class<?>[] params = ((java.lang.reflect.Method)param.method)
                                          .getParameterTypes();
                  if (params.length == 1 && params[0] == String.class)
                    bypassExpiry = System.currentTimeMillis() + 2000;
                }
              }
            });

        XposedBridge.hookAllMethods(
            managerCls, cfg.readReceipt.methodReadAll, new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (SettingsStore.get("prevent_read_state", true) &&
                    SettingsStore.get("send_mark_state", false)) {
                  if (((java.lang.reflect.Method)param.method)
                          .getParameterCount() == 0)
                    bypassExpiry = System.currentTimeMillis() + 2000;
                }
              }
            });
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Manager hook error: " + t);
    }

    try {
      Class<?> talkClient =
          lpparam.classLoader.loadClass(cfg.thrift.talkServiceClientImplClass);
      XC_MethodHook sendHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
          if (SettingsStore.get("prevent_read_state", true) &&
              SettingsStore.get("send_mark_state", false)) {
            bypassExpiry = System.currentTimeMillis() + 2000;
          }
        }
      };
      XposedBridge.hookAllMethods(talkClient, cfg.thrift.v1, sendHook);
      XposedBridge.hookAllMethods(talkClient, cfg.thrift.sendMessage, sendHook);
    } catch (Throwable ignored) {
    }
  }

  private static boolean containsServiceReference(Class<?> c) {
    LineVersion.Config cfg = LineVersion.get();
    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
      if (cfg.thrift.talkServiceClientInterface.equals(f.getType().getName()))
        return true;
    }
    return false;
  }

  private static boolean containsMarkAsReadLogic(Class<?> c) {
    LineVersion.Config cfg = LineVersion.get();
    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
      if (!cfg.readReceipt.methodSendReadReceipt.equals(m.getName()))
        continue;
      Class<?>[] p = m.getParameterTypes();
      if (p.length == 3 && p[0] == long.class &&p[1] == String.class &&
          p[2] == boolean.class)
        return true;
    }
    return false;
  }

  private static void recordReadReceipt(String chatId, String senderMid,
                                        String msgId, long timestamp) {
    try {
      Context context = AndroidAppHelper.currentApplication();
      if (context == null)
        return;

      boolean recordOthers = SettingsStore.get("record_others_read", false);
      String fromMid =
          app.zipper.knot.utils.LineDBUtils.resolveMessageSender(msgId);

      if (!recordOthers && fromMid != null && !fromMid.trim().isEmpty()) {
        return;
      }

      String messageText =
          app.zipper.knot.utils.LineDBUtils.resolveMessageContent(msgId);

      JSONObject history = SettingsStore.loadReadHistory();
      JSONArray list = history.optJSONArray("read_history");
      if (list == null) {
        list = new JSONArray();
        history.put("read_history", list);
      }

      for (int i = 0; i < list.length(); i++) {
        JSONObject o = list.optJSONObject(i);
        if (o != null && chatId.equals(o.optString("chatId")) &&
            senderMid.equals(o.optString("memberMid")) &&
            msgId.equals(o.optString("msgId"))) {
          return;
        }
      }

      JSONObject entry = new JSONObject();
      entry.put("chatId", chatId);
      entry.put("memberMid", senderMid);
      entry.put("msgId", msgId);
      entry.put("messageText", messageText != null ? messageText : "");
      entry.put("timestamp",
                timestamp > 0 ? timestamp : System.currentTimeMillis());
      list.put(entry);

      history.put("read_history", list);

      SettingsStore.saveReadHistory(history);
    } catch (Throwable t) {
      XposedBridge.log("Knot: save error: " + t);
    }
  }
}
