package app.zipper.knot.hooks;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RemoveAds implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    applyLadAdHook(config, lpparam, cfg);
    applySmartChannelHook(config, lpparam, cfg);
    applyGenericAddViewHook(config);
  }

  private void applyLadAdHook(
      KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam, LineVersion.Config cfg) {
    try {
      Class<?> ladCls = lpparam.classLoader.loadClass(cfg.ads.ladAdView);
      XposedHelpers.findAndHookMethod(
          ladCls,
          "onAttachedToWindow",
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (!config.removeAds.enabled) return;
              try {
                View target = (View) param.thisObject;
                View root = (View) target.getParent().getParent();
                ViewGroup.LayoutParams lp = root.getLayoutParams();
                if (lp != null) {
                  lp.height = 0;
                  root.setLayoutParams(lp);
                }
                root.setVisibility(View.GONE);
              } catch (Throwable e) {
                try {
                  View target = (View) param.thisObject;
                  View root = (View) target.getParent();
                  ViewGroup.LayoutParams lp = root.getLayoutParams();
                  if (lp != null) {
                    lp.height = 0;
                    root.setLayoutParams(lp);
                  }
                  root.setVisibility(View.GONE);
                } catch (Throwable ignored) {
                }
              }
            }
          });

      XposedBridge.hookAllMethods(
          ladCls,
          "setVisibility",
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (!config.removeAds.enabled) return;
              if ((int) param.args[0] == View.VISIBLE) param.args[0] = View.GONE;
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void applySmartChannelHook(
      KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam, LineVersion.Config cfg) {
    try {
      Class<?> smartCls = lpparam.classLoader.loadClass(cfg.ads.smartChannel);
      XposedHelpers.findAndHookMethod(
          smartCls,
          "dispatchDraw",
          Canvas.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (!config.removeAds.enabled) return;
              try {
                View container = (View) ((View) param.thisObject).getParent();
                container.setVisibility(View.GONE);
              } catch (Throwable ignored) {
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void applyGenericAddViewHook(KnotConfig config) {
    XposedHelpers.findAndHookMethod(
        ViewGroup.class,
        "addView",
        View.class,
        ViewGroup.LayoutParams.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (!config.removeAds.enabled) return;
            View view = (View) param.args[0];
            if (isAdComponent(view.getClass().getName())) view.setVisibility(View.GONE);
          }
        });
  }

  private static boolean isAdComponent(String className) {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg != null) {
      if (className.startsWith(cfg.ads.classAdSdkBase)) return true;
      if (className.startsWith(cfg.ads.classAdMolinBase)) return true;
    }
    String lower = className.toLowerCase();
    return lower.contains("nativead")
        || lower.contains("adcard")
        || lower.contains("adcell")
        || lower.contains("aditem")
        || lower.contains("adunit")
        || lower.contains("adview")
        || lower.contains("adbanner")
        || lower.contains("admodule")
        || lower.contains("sponsored")
        || lower.contains("promoted");
  }
}
