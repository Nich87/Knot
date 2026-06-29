package app.zipper.knot.hooks;

import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Main;
import app.zipper.knot.Reflect;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

public class ShowThemeOnSubDeviceHook implements BaseHook {

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    if (!config.showThemeOnSubDevice.enabled) return;

    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null
        || cfg.settings.settingsRowItemClass.isEmpty()
        || cfg.settings.fieldVisibilityFilter.isEmpty()
        || cfg.settings.themeSettingItemId.isEmpty()) {
      return;
    }

    Class<?> itemClass = Reflect.findClass(cfg.settings.settingsRowItemClass, lpparam.classLoader);
    String themeId = cfg.settings.themeSettingItemId;
    String filterField = cfg.settings.fieldVisibilityFilter;

    Knot.hookAllCtors(
        itemClass,
        chain -> {
          Object result = chain.proceed();
          Object[] args = chain.getArgs().toArray();
          if (Main.options.showThemeOnSubDevice.enabled
              && args.length > 0
              && themeId.equals(args[0])) {
            try {
              forceAlwaysVisible(chain.getThisObject(), filterField);
            } catch (Throwable t) {
              Knot.log("Knot: ShowThemeOnSubDeviceHook failed: " + t);
            }
          }
          return result;
        });
  }

  private static void forceAlwaysVisible(Object item, String filterField) throws Exception {
    Field f = field(item.getClass(), filterField);
    if (f == null) return;
    f.setAccessible(true);
    Object alwaysVisible =
        Proxy.newProxyInstance(
            item.getClass().getClassLoader(),
            new Class<?>[] {f.getType()},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "invoke":
                  return Boolean.TRUE;
                case "equals":
                  return proxy == args[0];
                case "hashCode":
                  return System.identityHashCode(proxy);
                default:
                  return "KnotAlwaysVisible";
              }
            });
    f.set(item, alwaysVisible);
  }

  private static Field field(Class<?> c, String name) {
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        c = c.getSuperclass();
      }
    }
    return null;
  }
}
