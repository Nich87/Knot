package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class RemoveTalkRoomAgentIToggle implements BaseHook {

  private static final String LEGACY_KEY = "remove_talkroom_agent_i_toggle";

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!isEnabled(config)) return;

    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cfg.agentIInChat.toggleComposableClass.isEmpty()) return;

    final Class<?> cls;
    try {
      cls = XposedHelpers.findClass(cfg.agentIInChat.toggleComposableClass, lpparam.classLoader);
    } catch (Throwable t) {
      return;
    }

    XC_MethodHook noOp =
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            if (!isEnabled(Main.options)) return;
            param.setResult(null);
          }
        };

    int hookCount = 0;
    for (Method method : cls.getDeclaredMethods()) {
      if (!isComposeRenderMethod(method)) continue;
      XposedBridge.hookMethod(method, noOp);
      hookCount++;
    }

    if (hookCount > 0) {
      XposedBridge.log(
          "Knot: RemoveTalkRoomAgentIToggle hooked " + hookCount + " compose methods.");
    }
  }

  private static boolean isEnabled(KnotConfig config) {
    return config.removeSearchBarAgentIButton.enabled || SettingsStore.get(LEGACY_KEY, false);
  }

  private static boolean isComposeRenderMethod(Method method) {
    if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != Void.TYPE) {
      return false;
    }

    boolean hasComposer = false;
    boolean hasFlags = false;
    for (Class<?> type : method.getParameterTypes()) {
      if ("t2.k".equals(type.getName())) {
        hasComposer = true;
      } else if (type == Integer.TYPE) {
        hasFlags = true;
      }
    }
    return hasComposer && hasFlags;
  }
}
