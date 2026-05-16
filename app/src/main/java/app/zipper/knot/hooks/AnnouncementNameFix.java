package app.zipper.knot.hooks;

import android.text.SpannableStringBuilder;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AnnouncementNameFix implements BaseHook {

  @Override
  public void hook(KnotConfig options, XC_LoadPackage.LoadPackageParam lpparam) {
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
        XposedBridge.log("Knot: AnnouncementNameFix - mid field not found");
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
        XposedBridge.log("Knot: AnnouncementNameFix - format method not found");
        return;
      }

      XposedBridge.hookMethod(
          formatMethod,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              try {
                Object event = param.args[1];
                if (event == null || !announcementEventCls.isInstance(event)) return;

                Object original = param.getResult();
                if (original == null) return;

                Object mid = midField.get(event);
                Object name =
                    XposedHelpers.callMethod(
                        param.thisObject,
                        config.announcementFix.nameResolverMethod,
                        param.args[0],
                        mid,
                        param.args[2]);
                if (name == null || name.toString().isEmpty()) return;

                SpannableStringBuilder sb = new SpannableStringBuilder((CharSequence) original);
                int i = 0;
                while (i < sb.length() && Character.isWhitespace(sb.charAt(i))) {
                  i++;
                }
                if (i > 0) {
                  sb.delete(0, i);
                }
                sb.insert(0, name.toString());
                param.setResult(sb);
              } catch (Throwable t) {
                XposedBridge.log("Knot: AnnouncementNameFix post-process error: " + t);
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: AnnouncementNameFix hook error: " + t);
    }
  }
}
