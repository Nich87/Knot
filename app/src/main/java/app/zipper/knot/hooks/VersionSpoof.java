package app.zipper.knot.hooks;

import android.content.Context;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;
import io.github.libxposed.api.XposedInterface;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class VersionSpoof implements BaseHook {

  private static final String TARGET_VERSION = "15.12.2";
  private static final ThreadLocal<Boolean> unsendProcessingFlag =
      ThreadLocal.withInitial(() -> false);
  private static final Set<String> targetUnsendMethods = new HashSet<>();

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
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
      applyVersionPatch(config, cfg, lpparam);
    }
  }

  private void applyUiLimitPatch(LineVersion.Config cfg, LoadParam lpparam) {
    if (cfg.unsend.chatServiceConfigClass.isEmpty() || cfg.unsend.methodUnsendLimit.isEmpty())
      return;
    try {
      Class<?> configCls =
          Reflect.findClass(cfg.unsend.chatServiceConfigClass, lpparam.classLoader);
      XposedInterface.Hooker limitPatch = chain -> 86400000;
      Knot.module
          .hook(Reflect.findMethodExact(configCls, cfg.unsend.methodUnsendLimit))
          .intercept(limitPatch);
      Knot.module
          .hook(Reflect.findMethodExact(configCls, cfg.unsend.methodUnsendPremiumLimit))
          .intercept(limitPatch);
    } catch (Throwable t) {
      Knot.log("Knot: UI limit patch failed: " + t.getMessage());
    }
  }

  private void applyVersionPatch(KnotConfig config, LineVersion.Config cfg, LoadParam lpparam) {
    if (cfg.unsend.appInfoProviderClass.isEmpty()) return;

    initializeThriftInterception(lpparam.classLoader);

    try {
      Class<?> infoCls = Reflect.findClass(cfg.unsend.appInfoProviderClass, lpparam.classLoader);

      XposedInterface.Hooker stringPatchHook =
          chain -> {
            Object result = chain.proceed();
            if (!(result instanceof String)) return result;
            String raw = (String) result;

            if (config.spoofVersion.enabled) {
              String patched = patchVersionString(raw);
              Knot.log(
                  "Knot: Global patch "
                      + raw.replace("\t", " ")
                      + " -> "
                      + patched.replace("\t", " "));
              return patched;
            }

            if (config.spoofVersionUnsendOnly.enabled && isUnsendActionActive()) {
              String patched = patchVersionString(raw);
              Knot.log(
                  "Knot: Contextual patch "
                      + raw.replace("\t", " ")
                      + " -> "
                      + patched.replace("\t", " "));
              return patched;
            }
            return result;
          };

      Knot.module
          .hook(Reflect.findMethodExact(infoCls, cfg.unsend.methodGetFullUserAgent))
          .intercept(stringPatchHook);
      Knot.module
          .hook(Reflect.findMethodExact(infoCls, cfg.unsend.methodGetSimpleUserAgent))
          .intercept(stringPatchHook);
      Knot.module
          .hook(
              Reflect.findMethodExact(
                  infoCls, cfg.unsend.methodGetFullUserAgentWithContext, Context.class))
          .intercept(stringPatchHook);
      Knot.module
          .hook(
              Reflect.findMethodExact(
                  infoCls, cfg.unsend.methodGetSimpleUserAgentWithContext, Context.class))
          .intercept(stringPatchHook);

    } catch (Throwable t) {
      Knot.log("Knot: Version patch failed: " + t.getMessage());
    }
  }

  private void initializeThriftInterception(ClassLoader cl) {
    LineVersion.Config cfg = LineVersion.get();
    try {
      Class<?> protocolCls = Reflect.findClass(cfg.thrift.protocolClass, cl);
      Knot.module
          .hook(
              Reflect.findMethodExact(
                  protocolCls,
                  cfg.thrift.methodWriteMessageBegin,
                  String.class,
                  cfg.thrift.messageClass))
          .intercept(
              chain -> {
                String method = (String) chain.getArg(0);
                if (targetUnsendMethods.contains(method)) {
                  unsendProcessingFlag.set(true);
                }
                try {
                  return chain.proceed();
                } finally {
                  unsendProcessingFlag.set(false);
                }
              });
    } catch (Throwable t) {
      Knot.log("Knot: Thrift interception failed: " + t.getMessage());
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
