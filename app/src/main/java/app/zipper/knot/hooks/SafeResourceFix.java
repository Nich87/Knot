package app.zipper.knot.hooks;

import android.content.res.Resources;
import android.text.SpannedString;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;

public class SafeResourceFix implements BaseHook {
  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    if (!config.safeSettingsResources.enabled) return;

    try {
      Knot.module
          .hook(Reflect.findMethodExact(Resources.class, "getText", int.class))
          .intercept(
              chain -> {
                Object result = chain.proceed();
                if (result instanceof String) {
                  return new SpannedString((String) result);
                }
                return result;
              });
    } catch (Throwable t) {
      Knot.debug("Knot: Failed to hook SafeResourceFix: " + t.getMessage());
    }
  }
}
