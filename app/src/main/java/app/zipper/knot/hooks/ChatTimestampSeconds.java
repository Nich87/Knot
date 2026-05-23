package app.zipper.knot.hooks;

import android.view.View;
import android.widget.TextView;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Main;
import app.zipper.knot.Reflect;
import io.github.libxposed.api.XposedInterface;
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
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    if (!config.showSecondsInChatTime.enabled) return;

    final LineVersion.Config cfg = LineVersion.get();
    final int timestampId = cfg.res.idTimestamp;
    tagKey = timestampId;
    final Class<?> displayTimeIface =
        Reflect.findClass(cfg.chatTimestamp.displayTimeInterface, lpparam.classLoader);

    Knot.hookAll(
        Reflect.findClass(cfg.unsend.chatMessageViewHolderClass, lpparam.classLoader),
        cfg.unsend.methodBind,
        chain -> {
          if (Main.options.showSecondsInChatTime.enabled) {
            try {
              stashCreatedMillis(chain, cfg, displayTimeIface, timestampId);
            } catch (Throwable t) {
              Knot.log("Knot: ChatTimestampSeconds stash error: " + t);
            }
          }
          return chain.proceed();
        });

    Knot.module
        .hook(
            Reflect.findMethodExact(
                TextView.class, "setText", CharSequence.class, TextView.BufferType.class))
        .intercept(
            chain -> {
              if (!Main.options.showSecondsInChatTime.enabled) return chain.proceed();
              if (!(chain.getArg(0) instanceof CharSequence)) return chain.proceed();
              try {
                TextView view = (TextView) chain.getThisObject();
                Object stamp = view.getTag(tagKey);
                if (!(stamp instanceof Long)) return chain.proceed();
                String injected = injectSeconds(chain.getArg(0).toString(), (Long) stamp);
                if (injected != null) {
                  return chain.proceed(new Object[] {injected, chain.getArg(1)});
                }
                return chain.proceed();
              } catch (Throwable t) {
                Knot.log("Knot: ChatTimestampSeconds setText error: " + t);
                return chain.proceed();
              }
            });
  }

  private static void stashCreatedMillis(
      XposedInterface.Chain chain,
      LineVersion.Config cfg,
      Class<?> displayTimeIface,
      int timestampId)
      throws Throwable {
    Object viewData = chain.getArg(cfg.unsend.methodBindIndex);
    if (viewData == null) return;

    Object commonData = Reflect.callMethod(viewData, cfg.unsend.methodGetCommonData);
    if (commonData == null) return;

    Object displayTime = resolveDisplayTime(commonData, displayTimeIface);
    if (displayTime == null) return;

    Object millisObj = Reflect.callMethod(displayTime, cfg.chatTimestamp.methodCreatedMillis);
    if (!(millisObj instanceof Number)) return;
    long createdMillis = ((Number) millisObj).longValue();
    if (createdMillis <= 0) return;

    View root = (View) Reflect.callMethod(chain.getThisObject(), cfg.unsend.methodGetItemView);
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
