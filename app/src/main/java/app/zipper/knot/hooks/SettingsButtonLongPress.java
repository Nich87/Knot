package app.zipper.knot.hooks;

import android.app.Activity;
import android.view.View;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SettingsButtonLongPress implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    try {
      XposedHelpers.findAndHookMethod(
          cfg.main.headerButton, lpparam.classLoader,
          "setButtonOnClickListener", View.OnClickListener.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
                throws Throwable {
              attachInteractionHandler((View)param.thisObject);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          View.class, "setOnClickListener", View.OnClickListener.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
                throws Throwable {
              View target = (View)param.thisObject;
              if (target == null)
                return;
              int id = target.getId();
              if (id != View.NO_ID) {
                LineVersion.Config c = LineVersion.get();
                String entry = "";
                try {
                  entry = target.getResources().getResourceEntryName(id);
                } catch (Throwable ignored) {
                }
                if (c.res.resSettingsHeaderBtn.equals(entry) ||
                    c.res.resSettingsBtn.equals(entry)) {
                  attachInteractionHandler(target);
                }
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void attachInteractionHandler(View root) {
    if (root == null)
      return;
    LineVersion.Config cfg = LineVersion.get();
    if (root.getClass().getName().contains("HeaderButton")) {
      try {
        View inner = (View)XposedHelpers.getObjectField(
            root, cfg.main.headerButtonInnerField);
        if (inner != null) {
          inner.setOnLongClickListener(interactionListener);
          return;
        }
      } catch (Throwable ignored) {
      }
    }
    root.setOnLongClickListener(interactionListener);
  }

  private final View.OnLongClickListener interactionListener = v -> {
    try {
      Activity host = findHostActivity(v.getContext());
      if (host != null) {
        HomeSettingsTooltip.markShown();
        SettingsUIInjector.openSettings(host);
        return true;
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Interaction error: " + t);
    }
    return false;
  };

  private Activity findHostActivity(android.content.Context ctx) {
    while (ctx instanceof android.content.ContextWrapper) {
      if (ctx instanceof Activity)
        return (Activity)ctx;
      ctx = ((android.content.ContextWrapper)ctx).getBaseContext();
    }
    return null;
  }
}
