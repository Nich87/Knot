package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public interface BaseHook {
  void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
