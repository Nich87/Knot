package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.DexScanner;
import app.zipper.knot.utils.LineDBUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

public class ReadReceiptHandler implements BaseHook {

  private static volatile long bypassExpiry = 0L;
  private static volatile boolean isBulkReading = false;
  private static volatile Object cachedAt2eInstance = null;

  @Override
  public void hook(final KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    hookOperationForHistory(cfg, lpparam.classLoader);
    hookReadQueue(cfg, lpparam.classLoader);
    hookThriftReadReceipt(cfg, lpparam.classLoader, config);
    hookReadReceiptManager(cfg, lpparam.classLoader, config);
    hookBadgeClear(cfg, lpparam.classLoader, config);
  }

  private void hookOperationForHistory(LineVersion.Config cfg, ClassLoader cl) {
    try {
      XposedBridge.hookAllMethods(
          XposedHelpers.findClass(cfg.unsend.talkServiceHookClass, cl),
          cfg.unsend.methodReadBuffer,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              try {
                if (!SettingsStore.get("record_read_history", false)) return;

                Object op = param.args[1];
                if (op == null || op instanceof String) return;

                Object type = XposedHelpers.getObjectField(op, cfg.unsend.operationTypeField);
                if (type != null
                    && cfg.readReceipt.operationNotifiedReadName.equals(type.toString())) {
                  processReadOp(op, cfg, history -> SettingsStore.saveReadHistory(history));
                }
              } catch (Throwable ignored) {
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookReadQueue(LineVersion.Config cfg, ClassLoader cl) {
    if (cfg.readReceipt.readReceiptQueueClass.isEmpty()) return;
    try {
      XposedBridge.hookAllMethods(
          XposedHelpers.findClass(cfg.readReceipt.readReceiptQueueClass, cl),
          cfg.readReceipt.methodEnqueueReadReceipt,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              try {
                if (!SettingsStore.get("record_read_history", false)) return;

                Method m = (Method) param.method;
                Class<?>[] types = m.getParameterTypes();
                if (types.length == 5 && types[0] == long.class && types[1] == String.class) {
                  long createdTime = (long) param.args[0];
                  String chatId = (String) param.args[1];
                  String senderMid = (String) param.args[2];
                  String lastMsgId = String.valueOf((long) param.args[3]);
                  recordRead(chatId, senderMid, lastMsgId, createdTime);
                }
              } catch (Throwable ignored) {
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookThriftReadReceipt(LineVersion.Config cfg, ClassLoader cl, KnotConfig config) {
    try {
      XposedBridge.hookAllMethods(
          cl.loadClass(cfg.thrift.talkServiceClientImplClass),
          cfg.thrift.v1,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (param.args == null || param.args[0] == null) return;
              if (shouldBlockReadReceipt(config)) {
                if (param.args[0] instanceof String) param.args[0] = "KNOT_NOP";
                else param.setResult(null);
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookReadReceiptManager(LineVersion.Config cfg, ClassLoader cl, KnotConfig config) {
    try {
      Class<?> managerCls = getManagerClass(cfg, cl);
      if (managerCls == null) return;

      XposedBridge.hookAllMethods(
          managerCls,
          cfg.readReceipt.methodSendReadReceipt,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              Method m = (Method) param.method;
              Class<?>[] params = m.getParameterTypes();
              if (params.length == 3 && params[0] == long.class) {
                if (cachedAt2eInstance == null) cachedAt2eInstance = param.thisObject;
                if (shouldBlockReadReceipt(config)) param.setResult(null);
              }
            }
          });

      XposedBridge.hookAllMethods(
          managerCls,
          cfg.readReceipt.methodExecuteReadReceiptAsync,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (isBypassEnabled(config)) {
                Class<?>[] params = ((Method) param.method).getParameterTypes();
                if (params.length == 1 && params[0] == String.class)
                  bypassExpiry = System.currentTimeMillis() + 100;
              }
            }
          });

      XposedBridge.hookAllMethods(
          managerCls,
          cfg.readReceipt.methodReadAll,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (isBypassEnabled(config) && ((Method) param.method).getParameterCount() == 0)
                isBulkReading = true;
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              if (((Method) param.method).getParameterCount() == 0) isBulkReading = false;
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookBadgeClear(LineVersion.Config cfg, ClassLoader cl, KnotConfig config) {
    if (cfg.readReceipt.badgeClearClass.isEmpty()) return;
    try {
      XposedBridge.hookAllMethods(
          XposedHelpers.findClass(cfg.readReceipt.badgeClearClass, cl),
          "e",
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (!config.preventMarkAsRead.enabled
                  || !SettingsStore.get("prevent_read_state", true)) return;
              if (isBypassActive()) return;

              Class<?>[] params = ((Method) param.method).getParameterTypes();
              if (params.length == 1 && params[0] == String.class && isLocalReadContext())
                param.setResult(null);
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private boolean shouldBlockReadReceipt(KnotConfig config) {
    if (!config.preventMarkAsRead.enabled || !SettingsStore.get("prevent_read_state", true))
      return false;
    return !isBypassActive();
  }

  private boolean isBypassActive() {
    return isBulkReading
        || (SettingsStore.get("send_mark_state", false)
            && bypassExpiry > System.currentTimeMillis());
  }

  private boolean isBypassEnabled(KnotConfig config) {
    return config.preventMarkAsRead.enabled
        && SettingsStore.get("prevent_read_state", true)
        && SettingsStore.get("send_mark_state", false);
  }

  private boolean isLocalReadContext() {
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    for (StackTraceElement element : stack) {
      String name = element.getClassName();
      if (name.contains("ChatHistoryActivity")
          || name.contains("MessageList")
          || name.contains("ChatList")) return true;
    }
    return false;
  }

  private Class<?> getManagerClass(LineVersion.Config cfg, ClassLoader cl) {
    if (!cfg.readReceipt.readReceiptManagerClass.isEmpty()) {
      try {
        return XposedHelpers.findClass(cfg.readReceipt.readReceiptManagerClass, cl);
      } catch (Throwable ignored) {
      }
    }
    return DexScanner.findClass(
        cl, null, c -> containsServiceReference(c) && containsMarkAsReadLogic(c));
  }

  private boolean containsServiceReference(Class<?> c) {
    String target = LineVersion.get().thrift.talkServiceClientInterface;
    for (java.lang.reflect.Field f : c.getDeclaredFields())
      if (target.equals(f.getType().getName())) return true;
    return false;
  }

  private boolean containsMarkAsReadLogic(Class<?> c) {
    String target = LineVersion.get().readReceipt.methodSendReadReceipt;
    for (Method m : c.getDeclaredMethods()) {
      if (target.equals(m.getName())) {
        Class<?>[] p = m.getParameterTypes();
        if (p.length == 3 && p[0] == long.class && p[1] == String.class) return true;
      }
    }
    return false;
  }

  private void recordRead(String chatId, String readerMid, String lastMsgId, long createdTime) {
    JSONObject history = SettingsStore.loadReadHistory();
    String myMid = LineDBUtils.getMyMid();
    List<LineDBUtils.MessageRecord> records =
        LineDBUtils.fetchMessagesForRecording(chatId, lastMsgId, myMid, false, -1);
    if (!records.isEmpty()) saveReadEvents(chatId, readerMid, records, createdTime, history);
  }

  private void processReadOp(
      Object op, LineVersion.Config cfg, java.util.function.Consumer<JSONObject> saver) {
    try {
      long createdTime = XposedHelpers.getLongField(op, cfg.unsend.operationCreatedTimeField);
      String chatId = (String) XposedHelpers.getObjectField(op, cfg.unsend.operationParam1Field);
      String readerMid = (String) XposedHelpers.getObjectField(op, cfg.unsend.operationParam2Field);
      String lastMsgId = (String) XposedHelpers.getObjectField(op, cfg.unsend.operationParam3Field);

      JSONObject history = SettingsStore.loadReadHistory();
      List<LineDBUtils.MessageRecord> records =
          LineDBUtils.fetchMessagesForRecording(
              chatId, lastMsgId, LineDBUtils.getMyMid(), false, -1);
      if (!records.isEmpty()) {
        saveReadEvents(chatId, readerMid, records, createdTime, history);
        saver.accept(history);
      }
    } catch (Throwable ignored) {
    }
  }

  private void saveReadEvents(
      String chatId,
      String readerMid,
      List<LineDBUtils.MessageRecord> records,
      long readTime,
      JSONObject history) {
    try {
      JSONObject chats = history.optJSONObject("c");
      if (chats == null) history.put("c", chats = new JSONObject());
      JSONObject chat = chats.optJSONObject(chatId);
      if (chat == null) chats.put(chatId, chat = new JSONObject());
      JSONObject messages = chat.optJSONObject("m");
      if (messages == null) chat.put("m", messages = new JSONObject());

      String name = LineDBUtils.resolveMemberName(readerMid);
      String timeStr =
          new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
              .format(new Date(readTime));

      for (LineDBUtils.MessageRecord record : records) {
        if (!chat.has("n")) chat.put("n", record.chatName);
        JSONObject msg = messages.optJSONObject(record.id);
        if (msg == null) {
          messages.put(record.id, msg = new JSONObject());
          msg.put("c", record.text);
          msg.put("sn", record.senderName);
          msg.put("ct", record.timestamp);
          msg.put("r", new JSONObject());
        }
        JSONObject readers = msg.optJSONObject("r");
        if (!readers.has(readerMid)) {
          JSONObject info = new JSONObject();
          info.put("n", name != null ? name : "Unknown");
          info.put("t", timeStr);
          readers.put(readerMid, info);
        }
      }
      SettingsStore.saveReadHistory(history);
    } catch (Throwable ignored) {
    }
  }
}
