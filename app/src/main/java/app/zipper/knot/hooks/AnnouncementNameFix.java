package app.zipper.knot.hooks;

import android.text.SpannableStringBuilder;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AnnouncementNameFix implements BaseHook {

  @Override
  public void hook(KnotConfig options, LoadParam lpparam) {
    if (!options.fixAnnouncementName.enabled) return;

    final LineVersion.Config config = LineVersion.get();
    if (config == null
        || config.announcementFix.formatterClass.isEmpty()
        || config.announcementFix.formatMethod.isEmpty()
        || config.announcementFix.nameResolverMethod.isEmpty()
        || config.announcementFix.announcementEventClass.isEmpty()) {
      return;
    }

    try {
      final Class<?> formatterCls =
          lpparam.classLoader.loadClass(config.announcementFix.formatterClass);
      final Class<?> announcementEventCls =
          lpparam.classLoader.loadClass(config.announcementFix.announcementEventClass);

      Field midFieldTmp = null;
      for (Field f : announcementEventCls.getDeclaredFields()) {
        if (f.getType() == String.class) {
          midFieldTmp = f;
          break;
        }
      }
      if (midFieldTmp == null) {
        Knot.log("Knot: AnnouncementNameFix - mid field not found");
        return;
      }
      final Field midField = midFieldTmp;
      midField.setAccessible(true);

      Method formatMethod = null;
      for (Method m : formatterCls.getDeclaredMethods()) {
        if (m.getName().equals(config.announcementFix.formatMethod)
            && m.getParameterTypes().length == 3) {
          formatMethod = m;
          break;
        }
      }
      if (formatMethod == null) {
        Knot.log("Knot: AnnouncementNameFix - format method not found");
        return;
      }

      Knot.module
          .hook(formatMethod)
          .intercept(
              chain -> {
                Object original = chain.proceed();
                try {
                  Object event = chain.getArg(1);
                  if (event == null || !announcementEventCls.isInstance(event)) return original;
                  if (original == null) return null;

                  Object mid = midField.get(event);
                  Object name =
                      Reflect.callMethod(
                          chain.getThisObject(),
                          config.announcementFix.nameResolverMethod,
                          chain.getArg(0),
                          mid,
                          chain.getArg(2));
                  if (name == null || name.toString().isEmpty()) return original;

                  SpannableStringBuilder sb = new SpannableStringBuilder((CharSequence) original);
                  int i = 0;
                  while (i < sb.length() && Character.isWhitespace(sb.charAt(i))) {
                    i++;
                  }
                  if (i > 0) {
                    sb.delete(0, i);
                  }
                  sb.insert(0, name.toString());
                  return sb;
                } catch (Throwable t) {
                  Knot.log("Knot: AnnouncementNameFix post-process error: " + t);
                  return original;
                }
              });
    } catch (Throwable t) {
      Knot.log("Knot: AnnouncementNameFix hook error: " + t);
    }
  }
}
