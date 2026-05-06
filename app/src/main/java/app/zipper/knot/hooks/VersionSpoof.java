package app.zipper.knot.hooks;

import android.content.Context;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class VersionSpoof implements BaseHook {

  private static final String TARGET_VERSION = "15.12.2";
  private static final ThreadLocal<Boolean> unsendProcessingFlag =
      ThreadLocal.withInitial(() -> false);
  private static final Set<String> targetUnsendMethods = new HashSet<>();

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null) return;

    if (targetUnsendMethods.isEmpty()) {
      targetUnsendMethods.addAll(
          Arrays.asList(
              cfg.unsend.methodUnsendThrift,
              cfg.unsend.methodUnsendThriftSilent,
              cfg.unsend.methodUnsendAnnouncement,
              cfg.thrift.methodDestroyMessage,
              cfg.thrift.methodDestroyMessages));
    }

    if (config.spoofVersion.enabled || config.spoofVersionUnsendOnly.enabled) {
      applyUiLimitPatch(cfg, lpparam);
    }

    if (config.spoofVersion.enabled || config.spoofVersionUnsendOnly.enabled) {
      applyVersionPatch(config, cfg, lpparam);
    }
  }

  private void applyUiLimitPatch(LineVersion.Config cfg, XC_LoadPackage.LoadPackageParam lpparam) {
    if (cfg.unsend.chatServiceConfigClass.isEmpty() || cfg.unsend.methodUnsendLimit.isEmpty())
      return;
    try {
      Class<?> configCls =
          XposedHelpers.findClass(cfg.unsend.chatServiceConfigClass, lpparam.classLoader);
      XC_MethodReplacement patch =
          new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
              return 86400000;
            }
          };
      XposedHelpers.findAndHookMethod(configCls, cfg.unsend.methodUnsendLimit, patch);
      XposedHelpers.findAndHookMethod(configCls, cfg.unsend.methodUnsendPremiumLimit, patch);
    } catch (Throwable t) {
      XposedBridge.log("Knot: UI limit patch failed: " + t.getMessage());
    }
  }

  private void applyVersionPatch(
      KnotConfig config, LineVersion.Config cfg, XC_LoadPackage.LoadPackageParam lpparam) {
    if (cfg.unsend.appInfoProviderClass.isEmpty()) return;

    initializeThriftInterception(lpparam.classLoader);

    try {
      Class<?> infoCls =
          XposedHelpers.findClass(cfg.unsend.appInfoProviderClass, lpparam.classLoader);

      XC_MethodHook stringPatchHook =
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              if (!(param.getResult() instanceof String)) return;
              String raw = (String) param.getResult();

              if (config.spoofVersion.enabled) {
                String patched = patchVersionString(raw);
                XposedBridge.log(
                    "Knot: Global patch "
                        + raw.replace("\t", " ")
                        + " -> "
                        + patched.replace("\t", " "));
                param.setResult(patched);
                return;
              }

              if (config.spoofVersionUnsendOnly.enabled && isUnsendActionActive()) {
                String patched = patchVersionString(raw);
                XposedBridge.log(
                    "Knot: Contextual patch "
                        + raw.replace("\t", " ")
                        + " -> "
                        + patched.replace("\t", " "));
                param.setResult(patched);
              }
            }
          };

      XposedHelpers.findAndHookMethod(infoCls, cfg.unsend.methodGetFullUserAgent, stringPatchHook);
      XposedHelpers.findAndHookMethod(
          infoCls, cfg.unsend.methodGetSimpleUserAgent, stringPatchHook);
      XposedHelpers.findAndHookMethod(
          infoCls, cfg.unsend.methodGetFullUserAgentWithContext, Context.class, stringPatchHook);
      XposedHelpers.findAndHookMethod(
          infoCls, cfg.unsend.methodGetSimpleUserAgentWithContext, Context.class, stringPatchHook);

    } catch (Throwable t) {
      XposedBridge.log("Knot: Version patch failed: " + t.getMessage());
    }
  }

  private void initializeThriftInterception(ClassLoader cl) {
    LineVersion.Config cfg = LineVersion.get();
    try {
      Class<?> protocolCls = XposedHelpers.findClass(cfg.thrift.protocolClass, cl);
      XposedHelpers.findAndHookMethod(
          protocolCls,
          cfg.thrift.methodWriteMessageBegin,
          String.class,
          cfg.thrift.messageClass,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              String method = (String) param.args[0];
              if (targetUnsendMethods.contains(method)) {
                unsendProcessingFlag.set(true);
              }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              unsendProcessingFlag.set(false);
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Thrift interception failed: " + t.getMessage());
    }
  }

  private String patchVersionString(String input) {
    if (input == null || input.isEmpty()) return input;
    return input.replaceAll("(\\d+\\.\\d+\\.\\d+)", TARGET_VERSION);
  }

  private boolean isUnsendActionActive() {
    return unsendProcessingFlag.get();
  }
}
