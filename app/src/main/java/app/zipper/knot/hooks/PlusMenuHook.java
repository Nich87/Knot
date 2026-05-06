package app.zipper.knot.hooks;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class PlusMenuHook implements BaseHook {

  private static volatile boolean isMenuDisplayed = false;
  private static volatile Object menuContextScope = null;
  private static volatile boolean injectionActive = false;

  private static volatile boolean currentReadState = true;
  private static volatile boolean currentSendMarkState = false;

  private static final int ID_READ_ON = 0x64000001;
  private static final int ID_READ_OFF = 0x64000002;
  private static final int ID_MARK_ON = 0x64000003;
  private static final int ID_MARK_OFF = 0x64000004;

  private static volatile int targetDrawableId = 0;
  private static final Map<Integer, Bitmap> iconStorage = new HashMap<>();

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    final Class<?> pCls;
    final Class<?> composerCls;
    final Class<?> composerImplCls;
    final Class<?> callbackCls;
    final Class<?> modifierCls;
    final Class<?> clickItemCls;

    try {
      pCls = XposedHelpers.findClass(cfg.plusMenu.plusMenuComponentClass,
                                     lpparam.classLoader);
      composerCls = XposedHelpers.findClass(cfg.plusMenu.plusMenuComposerClass,
                                            lpparam.classLoader);
      composerImplCls = XposedHelpers.findClass(
          cfg.plusMenu.plusMenuComposerImplClass, lpparam.classLoader);
      callbackCls = XposedHelpers.findClass(cfg.plusMenu.plusMenuCallbackClass,
                                            lpparam.classLoader);
      modifierCls = XposedHelpers.findClass(cfg.plusMenu.plusMenuModifierClass,
                                            lpparam.classLoader);
      clickItemCls = XposedHelpers.findClass(
          cfg.plusMenu.plusMenuOnClickItemClass, lpparam.classLoader);
    } catch (Throwable t) {
      return;
    }

    final Method mainEntry = XposedHelpers.findMethodExact(
        pCls, cfg.plusMenu.methodAddMenuItem, int.class, modifierCls,
        composerCls, clickItemCls, boolean.class);

    final Method itemEntry = XposedHelpers.findMethodExact(
        pCls, cfg.plusMenu.methodCreateMenu, int.class, int.class, modifierCls,
        String.class, composerCls, callbackCls);

    final Object readToggleCallback = generateToggleHandler(
        lpparam.classLoader, callbackCls, "prevent_read_state",
        ModuleStrings.LABEL_PREVENT_READ, true, null);
    final Object markToggleCallback = generateToggleHandler(
        lpparam.classLoader, callbackCls, "send_mark_state",
        ModuleStrings.LABEL_SEND_MARK_READ, false, "prevent_read_state");

    XposedBridge.hookMethod(mainEntry, new XC_MethodHook() {
      @Override
      protected void beforeHookedMethod(MethodHookParam param) {
        isMenuDisplayed = true;
        currentReadState = SettingsStore.get("prevent_read_state", true);
        currentSendMarkState = SettingsStore.get("send_mark_state", false);
      }
      @Override
      protected void afterHookedMethod(MethodHookParam param) {
        isMenuDisplayed = false;
      }
    });

    XposedHelpers.findAndHookMethod(
        composerImplCls, cfg.plusMenu.methodExecuteAction, new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (isMenuDisplayed && param.getResult() != null) {
              menuContextScope = param.getResult();
            }
          }
        });

    XposedBridge.hookMethod(itemEntry, new XC_MethodHook() {
      @Override
      protected void afterHookedMethod(MethodHookParam param) {
        if (!isMenuDisplayed || injectionActive)
          return;
        LineVersion.Config c = LineVersion.get();

        if (targetDrawableId == 0) {
          try {
            Context ctx = fetchApplicationContext();
            if (ctx != null) {
              targetDrawableId = ctx.getResources().getIdentifier(
                  c.plusMenu.editChatDrawable, "drawable",
                  c.plusMenu.targetPkg);
            }
          } catch (Throwable ignored) {
          }
        }
        if (targetDrawableId == 0)
          return;

        int iconId = (int)param.args[0];
        if (iconId != targetDrawableId)
          return;

        Object composer = param.args[4];
        injectionActive = true;
        try {
          if (Main.options.preventMarkAsRead.enabled) {
            boolean readOn = currentReadState;
            String labelR = ModuleStrings.LABEL_PREVENT_READ + ": " +
                            (readOn ? "ON" : "OFF");

            itemEntry.invoke(null, readOn ? ID_READ_ON : ID_READ_OFF, 0, null,
                             labelR, composer, readToggleCallback);

            if (readOn) {
              boolean markOn = currentSendMarkState;
              String labelM = ModuleStrings.LABEL_SEND_MARK_READ + ": " +
                              (markOn ? "ON" : "OFF");

              itemEntry.invoke(null, markOn ? ID_MARK_ON : ID_MARK_OFF, 0, null,
                               labelM, composer, markToggleCallback);
            }
          }
        } catch (Throwable t) {
          XposedBridge.log("Knot: PlusMenu error: " + t);
        } finally {
          injectionActive = false;
        }
      }
    });

    XposedHelpers.findAndHookMethod(
        Resources.class, "getValue", int.class, TypedValue.class, boolean.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam p) {
            int id = (int)p.args[0];
            if ((id >>> 24) != 0x64)
              return;
            ((TypedValue)p.args[1]).string =
                "knot_res_" + Integer.toHexString(id) + ".png";
            ((TypedValue)p.args[1]).type = TypedValue.TYPE_STRING;
            p.setResult(null);
          }
        });
    XposedHelpers.findAndHookMethod(
        Resources.class, "getDrawable", int.class, Resources.Theme.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam p) {
            int id = (int)p.args[0];
            if ((id >>> 24) != 0x64)
              return;
            try {
              Bitmap b = retrieveModuleIcon(id, cfg);
              if (b != null)
                p.setResult(new BitmapDrawable((Resources)p.thisObject, b));
            } catch (Throwable ignored) {
            }
          }
        });
  }

  private static Object generateToggleHandler(ClassLoader cl,
                                              Class<?> callbackCls, String key,
                                              String label, boolean def,
                                              String depKey) {
    return Proxy.newProxyInstance(
        cl, new Class[] {callbackCls}, (proxy, method, args) -> {
          if ("invoke".equals(method.getName())) {
            if (depKey != null && !currentReadState)
              return null;
            boolean nextValue;
            if (key.equals("prevent_read_state")) {
              nextValue = !currentReadState;
              currentReadState = nextValue;
            } else {
              nextValue = !currentSendMarkState;
              currentSendMarkState = nextValue;
            }
            SettingsStore.save(key, nextValue);
            new Handler(Looper.getMainLooper()).post(() -> {
              if (menuContextScope != null) {
                try {
                  XposedHelpers.callMethod(menuContextScope, "invalidate");
                } catch (Throwable ignored) {
                }
              }
            });
            return null;
          }
          if ("hashCode".equals(method.getName()))
            return System.identityHashCode(proxy);
          return null;
        });
  }

  private static Bitmap retrieveModuleIcon(int id, LineVersion.Config cfg) {
    Bitmap stored = iconStorage.get(id);
    if (stored != null)
      return stored;
    String name;
    if (id == ID_READ_ON)
      name = "ic_prevent_read_on";
    else if (id == ID_READ_OFF)
      name = "ic_prevent_read_off";
    else if (id == ID_MARK_ON)
      name = "ic_send_mark_read_on";
    else if (id == ID_MARK_OFF)
      name = "ic_send_mark_read_off";
    else
      return null;

    try {
      Context appCtx = fetchApplicationContext();
      if (appCtx == null)
        return null;
      Context modCtx = appCtx.createPackageContext(
          cfg.plusMenu.moduleId, Context.CONTEXT_IGNORE_SECURITY);
      int resId = modCtx.getResources().getIdentifier(name, "drawable",
                                                      cfg.plusMenu.moduleId);
      Drawable d = modCtx.getResources().getDrawable(resId, null);
      Bitmap bmp = ((BitmapDrawable)d).getBitmap();
      iconStorage.put(id, bmp);
      return bmp;
    } catch (Throwable t) {
      return null;
    }
  }

  private static Context fetchApplicationContext() {
    try {
      return (Context)Class.forName("android.app.ActivityThread")
          .getMethod("currentApplication")
          .invoke(null);
    } catch (Throwable t) {
      return null;
    }
  }
}
