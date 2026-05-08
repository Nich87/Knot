package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.json.JSONObject;

public class ReadReceiptHandler implements BaseHook {

  private static volatile long bypassExpiry = 0L;
  private static volatile Object cachedAt2eInstance = null;

  @Override
  public void hook(final KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    try {
      XposedBridge.hookAllMethods(
          XposedHelpers.findClass(cfg.unsend.talkServiceHookClass, lpparam.classLoader),
          cfg.unsend.methodReadBuffer,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {}

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              try {
                if (!SettingsStore.get("record_read_history", false)) return;

                Object op = param.args[1];
                if (op == null || op instanceof String) return;

                Object type = XposedHelpers.getObjectField(op, cfg.unsend.operationTypeField);
                if (type == null) return;

                if (cfg.readReceipt.operationNotifiedReadName.equals(type.toString())) {
                  long createdTime =
                      XposedHelpers.getLongField(op, cfg.unsend.operationCreatedTimeField);
                  String chatId =
                      (String) XposedHelpers.getObjectField(op, cfg.unsend.operationParam1Field);
                  String senderMid =
                      (String) XposedHelpers.getObjectField(op, cfg.unsend.operationParam2Field);
                  String lastMsgId =
                      (String) XposedHelpers.getObjectField(op, cfg.unsend.operationParam3Field);

                  if (chatId != null && senderMid != null && lastMsgId != null) {
                    boolean recordOthers = false;
                    String myMid = app.zipper.knot.utils.LineDBUtils.getMyMid();

                    JSONObject historyJson = SettingsStore.loadReadHistory();
                    JSONObject chats = historyJson.optJSONObject("c");
                    long maxId = -1;
                    if (chats != null) {
                      JSONObject chat = chats.optJSONObject(chatId);
                      if (chat != null) {
                        JSONObject messages = chat.optJSONObject("m");
                        if (messages != null) {
                          java.util.Iterator<String> keys = messages.keys();
                          while (keys.hasNext()) {
                            try {
                              long sid = Long.parseLong(keys.next());
                              if (sid > maxId) maxId = sid;
                            } catch (NumberFormatException ignored) {
                            }
                          }
                        }
                      }
                    }

                    java.util.List<app.zipper.knot.utils.LineDBUtils.MessageRecord> records =
                        app.zipper.knot.utils.LineDBUtils.fetchMessagesForRecording(
                            chatId, lastMsgId, myMid, recordOthers, maxId);

                    if (!records.isEmpty()) {
                      saveReadEvents(chatId, senderMid, records, createdTime, historyJson);
                    }
                  }
                }
              } catch (Throwable t) {
                XposedBridge.log("Knot: ReadHistory Error: " + t.getMessage());
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook Operation for read history: " + t);
    }

    // Hook queue path for NOTIFIED_READ_MESSAGE (26.6.0+)
    // Signature: c(long createdTime, String chatId, String senderMid, long
    // lastMsgId, Enum)
    if (cfg.readReceipt.readReceiptQueueClass != null
        && !cfg.readReceipt.readReceiptQueueClass.isEmpty()
        && cfg.readReceipt.methodEnqueueReadReceipt != null
        && !cfg.readReceipt.methodEnqueueReadReceipt.isEmpty()) {
      try {
        Class<?> queueCls =
            XposedHelpers.findClass(cfg.readReceipt.readReceiptQueueClass, lpparam.classLoader);
        XposedBridge.hookAllMethods(
            queueCls,
            cfg.readReceipt.methodEnqueueReadReceipt,
            new XC_MethodHook() {
              @Override
              protected void afterHookedMethod(MethodHookParam param) {
                try {
                  if (!SettingsStore.get("record_read_history", false)) return;

                  java.lang.reflect.Method m = (java.lang.reflect.Method) param.method;
                  Class<?>[] types = m.getParameterTypes();
                  if (types.length != 5
                      || types[0] != long.class
                      || types[1] != String.class
                      || types[2] != String.class
                      || types[3] != long.class) return;

                  long createdTime = (long) param.args[0];
                  String chatId = (String) param.args[1];
                  String senderMid = (String) param.args[2];
                  String lastMsgId = String.valueOf((long) param.args[3]);

                  if (chatId == null || senderMid == null) return;

                  String myMid = app.zipper.knot.utils.LineDBUtils.getMyMid();

                  JSONObject historyJson = SettingsStore.loadReadHistory();
                  JSONObject chats = historyJson.optJSONObject("c");
                  long maxId = -1;
                  if (chats != null) {
                    JSONObject chat = chats.optJSONObject(chatId);
                    if (chat != null) {
                      JSONObject messages = chat.optJSONObject("m");
                      if (messages != null) {
                        java.util.Iterator<String> keys = messages.keys();
                        while (keys.hasNext()) {
                          try {
                            long sid = Long.parseLong(keys.next());
                            if (sid > maxId) maxId = sid;
                          } catch (NumberFormatException ignored) {
                          }
                        }
                      }
                    }
                  }

                  java.util.List<app.zipper.knot.utils.LineDBUtils.MessageRecord> records =
                      app.zipper.knot.utils.LineDBUtils.fetchMessagesForRecording(
                          chatId, lastMsgId, myMid, false, maxId);

                  if (!records.isEmpty()) {
                    saveReadEvents(chatId, senderMid, records, createdTime, historyJson);
                  }
                } catch (Throwable t) {
                  XposedBridge.log("Knot: ReadQueue hook error: " + t.getMessage());
                }
              }
            });
      } catch (Throwable t) {
        XposedBridge.log("Knot: Failed to hook read receipt queue: " + t);
      }
    }

    // Block read receipt sending (handles both Thrift writer and direct wrapper
    // styles)
    try {
      Class<?> thriftCls = lpparam.classLoader.loadClass(cfg.thrift.talkServiceClientImplClass);
      XposedBridge.hookAllMethods(
          thriftCls,
          cfg.thrift.v1,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (param.args == null || param.args[0] == null) return;

              if (!config.preventMarkAsRead.enabled
                  || !SettingsStore.get("prevent_read_state", true)) return;
              if (SettingsStore.get("send_mark_state", false)
                  && bypassExpiry > System.currentTimeMillis()) return;

              if (param.args[0] instanceof String) param.args[0] = "KNOT_NOP";
              else param.setResult(null);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      Class<?> managerCls = null;
      if (cfg.readReceipt.readReceiptManagerClass != null
          && !cfg.readReceipt.readReceiptManagerClass.isEmpty()) {
        try {
          managerCls =
              XposedHelpers.findClass(cfg.readReceipt.readReceiptManagerClass, lpparam.classLoader);
        } catch (Throwable t) {
          XposedBridge.log(
              "Knot: Configured manager class not found: "
                  + cfg.readReceipt.readReceiptManagerClass);
        }
      }

      if (managerCls == null) {
        managerCls =
            app.zipper.knot.utils.DexScanner.findClass(
                lpparam.classLoader,
                lpparam.appInfo.sourceDir,
                c -> containsServiceReference(c) && containsMarkAsReadLogic(c));

        if (managerCls != null) {
          XposedBridge.log(
              "Knot: Discovered manager class via scan: "
                  + managerCls.getName()
                  + " (Please update VersionConfig)");
        }
      }

      if (managerCls != null) {
        XposedBridge.hookAllMethods(
            managerCls,
            cfg.readReceipt.methodSendReadReceipt,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                java.lang.reflect.Method m = (java.lang.reflect.Method) param.method;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3
                    && params[0] == long.class
                    && params[1] == String.class
                    && params[2] == boolean.class) {
                  if (cachedAt2eInstance == null) cachedAt2eInstance = param.thisObject;

                  if (!config.preventMarkAsRead.enabled
                      || !SettingsStore.get("prevent_read_state", true)) return;
                  if (SettingsStore.get("send_mark_state", false)
                      && bypassExpiry > System.currentTimeMillis()) return;
                  param.setResult(null);
                }
              }
            });
        XposedBridge.log("Knot: Successfully hooked manager class: " + managerCls.getName());

        XposedBridge.hookAllMethods(
            managerCls,
            cfg.readReceipt.methodExecuteReadReceiptAsync,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (config.preventMarkAsRead.enabled
                    && SettingsStore.get("prevent_read_state", true)
                    && SettingsStore.get("send_mark_state", false)) {
                  Class<?>[] params = ((java.lang.reflect.Method) param.method).getParameterTypes();
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
                if (config.preventMarkAsRead.enabled
                    && SettingsStore.get("prevent_read_state", true)
                    && SettingsStore.get("send_mark_state", false)) {
                  if (((java.lang.reflect.Method) param.method).getParameterCount() == 0)
                    bypassExpiry = System.currentTimeMillis() + 100;
                }
              }
            });
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Manager hook error: " + t);
    }

    try {
      Class<?> talkClient = lpparam.classLoader.loadClass(cfg.thrift.talkServiceClientImplClass);
      XC_MethodHook sendHook =
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {}

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              if (!config.preventMarkAsRead.enabled
                  || !SettingsStore.get("prevent_read_state", true)
                  || !SettingsStore.get("send_mark_state", false)) return;
              try {
                Object md = param.args[1];
                if (md == null) return;
                String chatId = (String) XposedHelpers.getObjectField(md, "b");
                if (chatId == null || chatId.isEmpty()) return;
                Object inst = cachedAt2eInstance;
                if (inst == null) return;

                bypassExpiry = System.currentTimeMillis() + 100;
                XposedHelpers.callMethod(
                    inst, cfg.readReceipt.methodSendReadReceipt, 0L, chatId, true);
              } catch (Throwable t) {
                XposedBridge.log("Knot: sendMark error: " + t);
              }
            }
          };
      XposedBridge.hookAllMethods(talkClient, cfg.thrift.sendMessage, sendHook);
    } catch (Throwable ignored) {
    }

    if (cfg.readReceipt.badgeClearClass != null && !cfg.readReceipt.badgeClearClass.isEmpty()) {
      try {
        Class<?> dcCls =
            XposedHelpers.findClass(cfg.readReceipt.badgeClearClass, lpparam.classLoader);
        XposedBridge.hookAllMethods(
            dcCls,
            "e",
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (!config.preventMarkAsRead.enabled
                    || !SettingsStore.get("prevent_read_state", true)) return;
                Class<?>[] params = ((java.lang.reflect.Method) param.method).getParameterTypes();
                if (params.length == 1 && params[0] == String.class) {
                  try {
                    if (SettingsStore.get("send_mark_state", false)
                        && bypassExpiry > System.currentTimeMillis()) return;

                    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                    boolean isLocalRead = false;
                    for (StackTraceElement element : stack) {
                      String className = element.getClassName();
                      if (className.contains("ChatHistoryActivity")
                          || className.contains("MessageList")
                          || className.contains("ChatList")) {
                        isLocalRead = true;
                        break;
                      }
                    }
                    if (!isLocalRead) return;
                  } catch (Throwable ignored) {
                  }
                  param.setResult(null);
                }
              }
            });
      } catch (Throwable t) {
        XposedBridge.log("Knot: Badge clear hook error: " + t);
      }
    }
  }

  private static boolean containsServiceReference(Class<?> c) {
    LineVersion.Config cfg = LineVersion.get();
    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
      if (cfg.thrift.talkServiceClientInterface.equals(f.getType().getName())) return true;
    }
    return false;
  }

  private static boolean containsMarkAsReadLogic(Class<?> c) {
    LineVersion.Config cfg = LineVersion.get();
    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
      if (!cfg.readReceipt.methodSendReadReceipt.equals(m.getName())) continue;
      Class<?>[] p = m.getParameterTypes();
      if (p.length == 3 && p[0] == long.class && p[1] == String.class && p[2] == boolean.class)
        return true;
    }
    return false;
  }

  private static void saveReadEvents(
      String chatId,
      String readerMid,
      java.util.List<app.zipper.knot.utils.LineDBUtils.MessageRecord> records,
      long readTime,
      JSONObject historyJson) {
    if (records == null || records.isEmpty()) return;

    try {
      JSONObject chats = historyJson.optJSONObject("c");
      if (chats == null) {
        chats = new JSONObject();
        historyJson.put("c", chats);
      }

      JSONObject chat = chats.optJSONObject(chatId);
      if (chat == null) {
        chat = new JSONObject();
        chats.put(chatId, chat);
      }

      JSONObject messages = chat.optJSONObject("m");
      if (messages == null) {
        messages = new JSONObject();
        chat.put("m", messages);
      }

      String readerName = app.zipper.knot.utils.LineDBUtils.resolveMemberName(readerMid);
      java.text.SimpleDateFormat sdf =
          new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
      String formattedReadTime = sdf.format(new java.util.Date(readTime));

      boolean isDirty = false;
      for (app.zipper.knot.utils.LineDBUtils.MessageRecord record : records) {
        if (!chat.has("n")) {
          chat.put("n", record.chatName);
        }

        JSONObject msgEntry = messages.optJSONObject(record.id);
        if (msgEntry == null) {
          msgEntry = new JSONObject();
          msgEntry.put("c", record.text);
          msgEntry.put("sn", record.senderName);
          msgEntry.put("sm", record.senderMid);
          msgEntry.put("ct", record.timestamp);
          msgEntry.put("r", new JSONObject());
          messages.put(record.id, msgEntry);
          isDirty = true;
        }

        JSONObject readers = msgEntry.optJSONObject("r");
        if (readers != null && !readers.has(readerMid)) {
          JSONObject rInfo = new JSONObject();
          rInfo.put("n", readerName != null ? readerName : "Unknown");
          rInfo.put("t", formattedReadTime);
          readers.put(readerMid, rInfo);
          isDirty = true;
        }
      }

      if (isDirty) {
        SettingsStore.saveReadHistory(historyJson);
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to save read events: " + t.getMessage());
    }
  }
}
