package app.zipper.knot.hooks;

import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.LineDBUtils;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

public class ReadReceiptHandler implements BaseHook {

  private static volatile boolean isBulkReading = false;
  private static final java.util.Set<String> pendingManualReads =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  @Override
  public void hook(final KnotConfig config, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    hookOperationForHistory(cfg, lpparam.classLoader);
    hookReadQueue(cfg, lpparam.classLoader);
    hookThriftReadReceipt(cfg, lpparam.classLoader, config);
    hookReadReceiptManager(cfg, lpparam.classLoader, config);
    hookBadgeClear(cfg, lpparam.classLoader, config);
  }

  private void hookOperationForHistory(LineVersion.Config cfg, ClassLoader cl) {
    try {
      Knot.hookAll(
          Reflect.findClass(cfg.unsend.talkServiceHookClass, cl),
          cfg.unsend.methodReadBuffer,
          chain -> {
            Object result = chain.proceed();
            try {
              if (SettingsStore.get("record_read_history", false)) {
                Object op = chain.getArg(1);
                if (op != null && !(op instanceof String)) {
                  Object type = Reflect.getObjectField(op, cfg.unsend.operationTypeField);
                  if (type != null
                      && cfg.readReceipt.operationNotifiedReadName.equals(type.toString())) {
                    long createdTime =
                        Reflect.getLongField(op, cfg.unsend.operationCreatedTimeField);
                    String chatId =
                        (String) Reflect.getObjectField(op, cfg.unsend.operationParam1Field);
                    String readerMid =
                        (String) Reflect.getObjectField(op, cfg.unsend.operationParam2Field);
                    String lastMsgId =
                        (String) Reflect.getObjectField(op, cfg.unsend.operationParam3Field);
                    recordReadEvent(chatId, readerMid, lastMsgId, createdTime);
                  }
                }
              }
            } catch (Throwable ignored) {
            }
            return result;
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookReadQueue(LineVersion.Config cfg, ClassLoader cl) {
    if (cfg.readReceipt.readReceiptQueueClass.isEmpty()) return;
    try {
      Knot.hookAll(
          Reflect.findClass(cfg.readReceipt.readReceiptQueueClass, cl),
          cfg.readReceipt.methodEnqueueReadReceipt,
          chain -> {
            Object result = chain.proceed();
            try {
              if (SettingsStore.get("record_read_history", false)) {
                Method m = (Method) chain.getExecutable();
                Class<?>[] types = m.getParameterTypes();
                if (types.length == 5 && types[0] == long.class && types[1] == String.class) {
                  long createdTime = (long) chain.getArg(0);
                  String chatId = (String) chain.getArg(1);
                  String readerMid = (String) chain.getArg(2);
                  String lastMsgId = String.valueOf((long) chain.getArg(3));
                  recordReadEvent(chatId, readerMid, lastMsgId, createdTime);
                }
              }
            } catch (Throwable ignored) {
            }
            return result;
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookThriftReadReceipt(LineVersion.Config cfg, ClassLoader cl, KnotConfig config) {
    try {
      Knot.hookAll(
          cl.loadClass(cfg.thrift.talkServiceClientImplClass),
          cfg.thrift.v1,
          chain -> {
            List<Object> args = chain.getArgs();
            if (args.isEmpty() || args.get(0) == null) return chain.proceed();
            for (Object a : args) {
              if (a instanceof String && pendingManualReads.contains(a)) return chain.proceed();
            }
            if (shouldBlockReadReceipt(config)) {
              if (args.get(0) instanceof String) {
                Object[] newArgs = args.toArray();
                newArgs[0] = "KNOT_NOP";
                return chain.proceed(newArgs);
              } else {
                return null;
              }
            }
            return chain.proceed();
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookReadReceiptManager(LineVersion.Config cfg, ClassLoader cl, KnotConfig config) {
    try {
      Class<?> managerCls = getManagerClass(cfg, cl);
      if (managerCls == null) return;

      Knot.hookAll(
          managerCls,
          cfg.readReceipt.methodSendReadReceipt,
          chain -> {
            Method m = (Method) chain.getExecutable();
            Class<?>[] params = m.getParameterTypes();
            boolean skip = false;
            if (params.length == 3 && params[0] == long.class) {
              Object cid = chain.getArgs().size() > 1 ? chain.getArg(1) : null;
              if (!(cid instanceof String && pendingManualReads.contains(cid))
                  && shouldBlockReadReceipt(config)) {
                skip = true;
              }
            }
            Object result = skip ? null : chain.proceed();
            if (params.length == 3
                && params[0] == long.class
                && chain.getArgs().size() > 1
                && chain.getArg(1) instanceof String) {
              pendingManualReads.remove(chain.getArg(1));
            }
            return result;
          });

      Knot.hookAll(
          managerCls,
          cfg.readReceipt.methodExecuteReadReceiptAsync,
          chain -> {
            if (isPreventActive(config)) {
              Class<?>[] params = ((Method) chain.getExecutable()).getParameterTypes();
              if (params.length == 1 && params[0] == String.class) {
                String manualClass = cfg.readReceipt.longPressReadClass;
                boolean manual =
                    manualClass != null && !manualClass.isEmpty() && isFromClass(manualClass);
                if (manual || SettingsStore.get("send_mark_state", false)) {
                  pendingManualReads.add((String) chain.getArg(0));
                }
              }
            }
            return chain.proceed();
          });

      Knot.hookAll(
          managerCls,
          cfg.readReceipt.methodReadAll,
          chain -> {
            boolean isNoArg = ((Method) chain.getExecutable()).getParameterCount() == 0;
            if (isPreventActive(config) && isNoArg) isBulkReading = true;
            try {
              return chain.proceed();
            } finally {
              if (isNoArg) isBulkReading = false;
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookBadgeClear(LineVersion.Config cfg, ClassLoader cl, KnotConfig config) {
    if (cfg.readReceipt.badgeClearClass.isEmpty()) return;
    try {
      Knot.hookAll(
          Reflect.findClass(cfg.readReceipt.badgeClearClass, cl),
          "e",
          chain -> {
            if (!isPreventActive(config) || isBulkReading) return chain.proceed();

            Class<?>[] params = ((Method) chain.getExecutable()).getParameterTypes();
            if (params.length == 1 && params[0] == String.class && isLocalReadContext()) {
              return null;
            }
            return chain.proceed();
          });
    } catch (Throwable ignored) {
    }
  }

  private boolean shouldBlockReadReceipt(KnotConfig config) {
    return isPreventActive(config) && !isBulkReading;
  }

  private boolean isPreventActive(KnotConfig config) {
    return config.preventMarkAsRead.enabled && SettingsStore.get("prevent_read_state", true);
  }

  private boolean isFromClass(String prefix) {
    for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
      String n = el.getClassName();
      if (n.equals(prefix) || n.startsWith(prefix + "$") || n.startsWith(prefix + ".")) return true;
    }
    return false;
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
    if (cfg.readReceipt.readReceiptManagerClass.isEmpty()) return null;
    try {
      return Reflect.findClass(cfg.readReceipt.readReceiptManagerClass, cl);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private void recordReadEvent(
      String chatId, String readerMid, String lastMsgIdStr, long readTime) {
    if (chatId == null || readerMid == null || lastMsgIdStr == null) return;
    long lastMsgId;
    try {
      lastMsgId = Long.parseLong(lastMsgIdStr);
    } catch (NumberFormatException e) {
      return;
    }
    String myMid = LineDBUtils.getMyMid();
    if (myMid == null) return;
    if (readerMid.equals(myMid)) return;

    try {
      JSONObject history = SettingsStore.loadReadHistory();
      JSONObject chat = ensureChat(history, chatId);

      long prevHwm = getReaderHwm(chat, readerMid);
      if (lastMsgId <= prevHwm) return;

      long lowerBound;
      if (prevHwm > 0) {
        lowerBound = prevHwm;
      } else {
        long inferred = inferReaderFloor(chat, readerMid);
        lowerBound = inferred > 0 ? inferred : lastMsgId - 1;
      }

      List<LineDBUtils.MessageRecord> records =
          LineDBUtils.fetchMyMessagesUpTo(chatId, lowerBound, lastMsgId, myMid);

      if (!records.isEmpty()) {
        addReaderToMessages(chat, readerMid, records, readTime);
      }
      setReaderHwm(chat, readerMid, lastMsgId);
      SettingsStore.saveReadHistory(history);
    } catch (Throwable ignored) {
    }
  }

  private JSONObject ensureChat(JSONObject history, String chatId) throws Exception {
    JSONObject chats = history.optJSONObject("c");
    if (chats == null) history.put("c", chats = new JSONObject());
    JSONObject chat = chats.optJSONObject(chatId);
    if (chat == null) chats.put(chatId, chat = new JSONObject());
    return chat;
  }

  private long getReaderHwm(JSONObject chat, String readerMid) {
    JSONObject hwm = chat.optJSONObject("rh");
    if (hwm == null) return 0L;
    String v = hwm.optString(readerMid, "");
    if (v.isEmpty()) return 0L;
    try {
      return Long.parseLong(v);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private void setReaderHwm(JSONObject chat, String readerMid, long msgId) throws Exception {
    JSONObject hwm = chat.optJSONObject("rh");
    if (hwm == null) chat.put("rh", hwm = new JSONObject());
    hwm.put(readerMid, String.valueOf(msgId));
  }

  private long inferReaderFloor(JSONObject chat, String readerMid) {
    JSONObject messages = chat.optJSONObject("m");
    if (messages == null) return 0L;
    long maxSeen = 0L;
    java.util.Iterator<String> it = messages.keys();
    while (it.hasNext()) {
      String mid = it.next();
      JSONObject msg = messages.optJSONObject(mid);
      if (msg == null) continue;
      JSONObject readers = msg.optJSONObject("r");
      if (readers == null || !readers.has(readerMid)) continue;
      try {
        long id = Long.parseLong(mid);
        if (id > maxSeen) maxSeen = id;
      } catch (NumberFormatException ignored) {
      }
    }
    return maxSeen;
  }

  private void addReaderToMessages(
      JSONObject chat, String readerMid, List<LineDBUtils.MessageRecord> records, long readTime)
      throws Exception {
    JSONObject messages = chat.optJSONObject("m");
    if (messages == null) chat.put("m", messages = new JSONObject());

    String name = LineDBUtils.resolveMemberName(readerMid);
    String timeStr =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(readTime));

    for (LineDBUtils.MessageRecord record : records) {
      if (!chat.has("n") && record.chatName != null) chat.put("n", record.chatName);

      JSONObject msg = messages.optJSONObject(record.id);
      if (msg == null) {
        msg = new JSONObject();
        msg.put("c", record.text);
        msg.put("sn", record.senderName);
        msg.put("ct", record.timestamp);
        msg.put("r", new JSONObject());
        messages.put(record.id, msg);
      }
      JSONObject readers = msg.optJSONObject("r");
      if (readers == null) {
        readers = new JSONObject();
        msg.put("r", readers);
      }
      if (!readers.has(readerMid)) {
        JSONObject info = new JSONObject();
        info.put("n", name != null ? name : "Unknown");
        info.put("t", timeStr);
        readers.put(readerMid, info);
      }
    }
  }
}
