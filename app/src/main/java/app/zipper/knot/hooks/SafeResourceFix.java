package app.zipper.knot.hooks;

import android.content.res.Resources;
import android.text.SpannedString;
import app.zipper.knot.KnotConfig;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SafeResourceFix implements BaseHook {
  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    if (!config.safeSettingsResources.enabled)
      return;

    try {
      XposedHelpers.findAndHookMethod(
          Resources.class, "getText", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
                throws Throwable {
              if (param.getResult() instanceof String) {
                param.setResult(new SpannedString((String)param.getResult()));
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook SafeResourceFix: " +
                       t.getMessage());
    }
  }
}
