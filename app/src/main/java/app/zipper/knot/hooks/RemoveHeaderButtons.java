package app.zipper.knot.hooks;

import android.content.Context;
import android.view.View;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RemoveHeaderButtons implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null) return;

    if (!config.removeAiFriendsButton.enabled
        && !config.removeSearchBarAgentIButton.enabled
        && !config.removeOpenChatButton.enabled) return;

    if (config.removeSearchBarAgentIButton.enabled) {
      hookHomeSearchBarAiButton(cfg, lpparam.classLoader);
    }

    if (cfg.talkTabHeader.chatTabHeaderStateClass.isEmpty()) return;

    Class<?> cls =
        XposedHelpers.findClass(cfg.talkTabHeader.chatTabHeaderStateClass, lpparam.classLoader);
    Class<?> iconTypeCls =
        XposedHelpers.findClass(cfg.talkTabHeader.iconTypeClass, lpparam.classLoader);

    final Object aiFriend = firstAvailableValueOf(iconTypeCls, "AI_FRIEND", "AI_FRIENDS");
    Object album = safeValueOf(iconTypeCls, "ALBUM");
    Object openChat = safeValueOf(iconTypeCls, "OPEN_CHAT");

    if (config.removeSearchBarAgentIButton.enabled) {
      hookSearchBarAiButton(cfg, cls);
    }

    XposedBridge.hookAllConstructors(
        cls,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (!Main.options.removeAiFriendsButton.enabled
                && !Main.options.removeOpenChatButton.enabled) return;
            try {
              patchState(param.thisObject, cfg, aiFriend, album, openChat);
            } catch (Exception e) {
              XposedBridge.log("Knot: RemoveHeaderButtons constructor error: " + e);
            }
          }
        });

    XposedBridge.hookAllMethods(
        cls,
        "createEndButtons",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (!Main.options.removeAiFriendsButton.enabled
                && !Main.options.removeOpenChatButton.enabled) return;
            List<?> result = (List<?>) param.getResult();
            if (result == null || result.isEmpty()) return;
            try {
              param.setResult(filterButtons(result, cfg, aiFriend, album, openChat));
            } catch (Exception e) {
              XposedBridge.log("Knot: RemoveHeaderButtons createEndButtons error: " + e);
            }
          }
        });
  }

  private static void patchState(
      Object instance, LineVersion.Config cfg, Object aiFriend, Object album, Object openChat)
      throws Exception {
    Object iconState = XposedHelpers.getObjectField(instance, cfg.talkTabHeader.iconListStateField);
    List<?> icons = (List<?>) XposedHelpers.callMethod(iconState, "getValue");
    if (icons != null)
      XposedHelpers.callMethod(
          iconState, "setValue", filterIcons(icons, aiFriend, album, openChat));

    Object btnState =
        XposedHelpers.getObjectField(instance, cfg.talkTabHeader.buttonListStateField);
    List<?> buttons = (List<?>) XposedHelpers.callMethod(btnState, "getValue");
    if (buttons != null)
      XposedHelpers.callMethod(
          btnState, "setValue", filterButtons(buttons, cfg, aiFriend, album, openChat));
  }

  private static List<Object> filterIcons(
      List<?> icons, Object aiFriend, Object album, Object openChat) {
    boolean removeAi = Main.options.removeAiFriendsButton.enabled;
    boolean removeOc = Main.options.removeOpenChatButton.enabled;
    List<Object> out = new ArrayList<>();
    for (Object icon : icons) {
      if (removeAi && (icon == aiFriend || icon == album)) continue;
      if (removeOc && icon == openChat) continue;
      out.add(icon);
    }
    return out;
  }

  private static List<Object> filterButtons(
      List<?> buttons, LineVersion.Config cfg, Object aiFriend, Object album, Object openChat)
      throws Exception {
    boolean removeAi = Main.options.removeAiFriendsButton.enabled;
    boolean removeOc = Main.options.removeOpenChatButton.enabled;
    List<Object> out = new ArrayList<>();
    for (Object btn : buttons) {
      Object type = XposedHelpers.getObjectField(btn, cfg.talkTabHeader.iconTypeFieldInButton);
      if (removeAi && (type == aiFriend || type == album)) continue;
      if (removeOc && type == openChat) continue;
      out.add(btn);
    }
    return out;
  }

  private static Object safeValueOf(Class<?> cls, String name) {
    try {
      return XposedHelpers.callStaticMethod(cls, "valueOf", name);
    } catch (Exception e) {
      return null;
    }
  }

  private static Object firstAvailableValueOf(Class<?> cls, String... names) {
    for (String name : names) {
      Object value = safeValueOf(cls, name);
      if (value != null) return value;
    }
    return null;
  }

  private static void hookSearchBarAiButton(LineVersion.Config cfg, Class<?> cls) {
    Method searchBarAiVisible =
        findZeroArgMethod(cls, cfg.searchBarAgentI.talkVisibleMethod, boolean.class);
    Method searchBarAiClick =
        findZeroArgMethod(cls, cfg.searchBarAgentI.talkClickMethod, void.class);

    if (searchBarAiVisible != null) {
      XposedBridge.hookMethod(
          searchBarAiVisible,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              if (Main.options.removeSearchBarAgentIButton.enabled) param.setResult(false);
            }
          });
    } else {
      XposedBridge.log("Knot: RemoveHeaderButtons could not find search bar AI visibility method.");
    }

    if (searchBarAiClick != null) {
      XposedBridge.hookMethod(
          searchBarAiClick,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              if (Main.options.removeSearchBarAgentIButton.enabled) param.setResult(null);
            }
          });
    }

    if (searchBarAiVisible != null || searchBarAiClick != null) {
      XposedBridge.log("Knot: RemoveHeaderButtons hooked Talk search bar Agent i button.");
    }
  }

  private static Method findZeroArgMethod(Class<?> cls, String methodName, Class<?> returnType) {
    if (methodName == null || methodName.isEmpty()) return null;
    try {
      Method method = cls.getDeclaredMethod(methodName);
      if (method.getReturnType() == returnType) return method;
    } catch (NoSuchMethodException e) {
    }
    return null;
  }

  private static void hookHomeSearchBarAiButton(LineVersion.Config cfg, ClassLoader classLoader) {
    if (cfg.searchBarAgentI.homeSearchBarClass.isEmpty()
        || cfg.searchBarAgentI.homeRefreshMethod.isEmpty()) return;

    Class<?> cls;
    try {
      cls = XposedHelpers.findClass(cfg.searchBarAgentI.homeSearchBarClass, classLoader);
    } catch (Throwable t) {
      XposedBridge.log("Knot: RemoveHeaderButtons could not find Home search bar class.");
      return;
    }

    XC_MethodHook patchHook =
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (!Main.options.removeSearchBarAgentIButton.enabled) return;
            try {
              patchHomeSearchBarAiButton(cfg, param.thisObject);
            } catch (Exception e) {
              XposedBridge.log("Knot: RemoveHeaderButtons Home search bar error: " + e);
            }
          }
        };

    XposedBridge.hookAllConstructors(cls, patchHook);
    XposedBridge.hookAllMethods(cls, cfg.searchBarAgentI.homeRefreshMethod, patchHook);
    XposedBridge.log("Knot: RemoveHeaderButtons hooked Home search bar Agent i button.");
  }

  private static void patchHomeSearchBarAiButton(LineVersion.Config cfg, Object instance) {
    if (!isHomeSearchBar(cfg, instance)) return;

    View rootView =
        (View) XposedHelpers.getObjectField(instance, cfg.searchBarAgentI.homeRootViewField);
    if (rootView == null) return;

    Context context = rootView.getContext();
    int aiContainerId = cfg.searchBarAgentI.homeAiContainerId;
    if (aiContainerId == 0) return;

    View aiContainer = rootView.findViewById(aiContainerId);
    if (aiContainer == null) return;

    aiContainer.setOnClickListener(null);
    aiContainer.setClickable(false);
    aiContainer.setVisibility(View.GONE);

    int guidelineId = cfg.searchBarAgentI.homeGuidelineId;
    View guidelineView = guidelineId != 0 ? rootView.findViewById(guidelineId) : null;
    if (guidelineView != null && cfg.searchBarAgentI.homeGuidelineEndDp > 0) {
      if (cfg.searchBarAgentI.homeGuidelineClass.equals(guidelineView.getClass().getName())) {
        try {
          XposedHelpers.callMethod(
              guidelineView,
              "setGuidelineEnd",
              dpToPx(context, cfg.searchBarAgentI.homeGuidelineEndDp));
        } catch (Throwable t) {
          XposedBridge.log("Knot: RemoveHeaderButtons guideline error: " + t);
        }
      }
    }
  }

  private static boolean isHomeSearchBar(LineVersion.Config cfg, Object instance) {
    if (cfg.searchBarAgentI.homeTabTypeField.isEmpty()) return false;

    Object tabType = XposedHelpers.getObjectField(instance, cfg.searchBarAgentI.homeTabTypeField);
    if (!(tabType instanceof Enum<?>)) return false;
    String name = ((Enum<?>) tabType).name();
    return name.equals(cfg.searchBarAgentI.homeTabName)
        || name.equals(cfg.searchBarAgentI.homeTabV2Name);
  }

  private static int dpToPx(Context context, int dp) {
    return Math.round(dp * context.getResources().getDisplayMetrics().density);
  }
}
