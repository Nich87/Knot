package app.zipper.knot.hooks;

import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Main;
import app.zipper.knot.Reflect;
import java.lang.reflect.Method;

public class HideAiIconPermanently implements BaseHook {

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg.aiIcon.repoClass.isEmpty()) return;

    Method target =
        Reflect.findMethodExact(
            cfg.aiIcon.repoClass, lpparam.classLoader, cfg.aiIcon.methodGetShownAfterMillis);

    Knot.module
        .hook(target)
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (Main.options.hideAiIconPermanently.enabled) {
                return Long.MAX_VALUE;
              }
              return result;
            });
  }
}
