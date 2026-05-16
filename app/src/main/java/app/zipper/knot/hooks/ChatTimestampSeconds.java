package app.zipper.knot.hooks;

import android.view.View;
import android.widget.TextView;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatTimestampSeconds implements BaseHook {

  private static final Pattern TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}");

  private static final Map<Class<?>, Field> timeFieldCache = new ConcurrentHashMap<>();

  private static int tagKey;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!config.showSecondsInChatTime.enabled) return;

    final LineVersion.Config cfg = LineVersion.get();
    final int timestampId = cfg.res.idTimestamp;
    tagKey = timestampId;
    final Class<?> displayTimeIface =
        XposedHelpers.findClass(cfg.chatTimestamp.displayTimeInterface, lpparam.classLoader);

    XposedBridge.hookAllMethods(
        XposedHelpers.findClass(cfg.unsend.chatMessageViewHolderClass, lpparam.classLoader),
        cfg.unsend.methodBind,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            if (!Main.options.showSecondsInChatTime.enabled) return;
            try {
              stashCreatedMillis(param, cfg, displayTimeIface, timestampId);
            } catch (Throwable t) {
              XposedBridge.log("Knot: ChatTimestampSeconds stash error: " + t);
            }
          }
        });

    XposedHelpers.findAndHookMethod(
        TextView.class,
        "setText",
        CharSequence.class,
        TextView.BufferType.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            if (!Main.options.showSecondsInChatTime.enabled) return;
            if (!(param.args[0] instanceof CharSequence)) return;
            try {
              TextView view = (TextView) param.thisObject;
              Object stamp = view.getTag(tagKey);
              if (!(stamp instanceof Long)) return;
              String injected = injectSeconds(param.args[0].toString(), (Long) stamp);
              if (injected != null) param.args[0] = injected;
            } catch (Throwable t) {
              XposedBridge.log("Knot: ChatTimestampSeconds setText error: " + t);
            }
          }
        });
  }

  private static void stashCreatedMillis(
      XC_MethodHook.MethodHookParam param,
      LineVersion.Config cfg,
      Class<?> displayTimeIface,
      int timestampId)
      throws Throwable {
    Object viewData = param.args[cfg.unsend.methodBindIndex];
    if (viewData == null) return;

    Object commonData = XposedHelpers.callMethod(viewData, cfg.unsend.methodGetCommonData);
    if (commonData == null) return;

    Object displayTime = resolveDisplayTime(commonData, displayTimeIface);
    if (displayTime == null) return;

    Object millisObj = XposedHelpers.callMethod(displayTime, cfg.chatTimestamp.methodCreatedMillis);
    if (!(millisObj instanceof Number)) return;
    long createdMillis = ((Number) millisObj).longValue();
    if (createdMillis <= 0) return;

    View root = (View) XposedHelpers.callMethod(param.thisObject, cfg.unsend.methodGetItemView);
    if (root == null) return;
    TextView tsView = (TextView) root.findViewById(timestampId);
    if (tsView == null) return;

    tsView.setTag(timestampId, createdMillis);
  }

  private static String injectSeconds(String base, long createdMillis) {
    if (base.isEmpty()) return null;
    Matcher m = TIME_PATTERN.matcher(base);
    if (!m.find()) return null;
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(createdMillis);
    String sec = String.format(Locale.US, "%02d", cal.get(Calendar.SECOND));
    return base.substring(0, m.end()) + ":" + sec + base.substring(m.end());
  }

  private static Object resolveDisplayTime(Object commonData, Class<?> displayTimeIface)
      throws IllegalAccessException {
    Class<?> cls = commonData.getClass();
    Field cached = timeFieldCache.get(cls);
    if (cached != null) return cached.get(commonData);

    for (Field f : cls.getDeclaredFields()) {
      if (!displayTimeIface.isAssignableFrom(f.getType())) continue;
      f.setAccessible(true);
      Object value = f.get(commonData);
      if (displayTimeIface.isInstance(value)) {
        timeFieldCache.put(cls, f);
        return value;
      }
    }
    return null;
  }
}
