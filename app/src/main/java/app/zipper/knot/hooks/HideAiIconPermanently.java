package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HideAiIconPermanently implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg.aiIcon.repoClass.isEmpty()) return;

    XposedHelpers.findAndHookMethod(
        cfg.aiIcon.repoClass,
        lpparam.classLoader,
        cfg.aiIcon.methodGetShownAfterMillis,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (Main.options.hideAiIconPermanently.enabled) {
              param.setResult(Long.MAX_VALUE);
            }
          }
        });
  }
}
