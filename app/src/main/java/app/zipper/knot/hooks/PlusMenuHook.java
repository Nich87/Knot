package app.zipper.knot.hooks;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Main;
import app.zipper.knot.Reflect;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.ModuleStrings;
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
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    final Class<?> pCls;
    final Class<?> composerCls;
    final Class<?> composerImplCls;
    final Class<?> callbackCls;
    final Class<?> modifierCls;
    final Class<?> clickItemCls;

    try {
      pCls = Reflect.findClass(cfg.plusMenu.plusMenuComponentClass, lpparam.classLoader);
      composerCls = Reflect.findClass(cfg.plusMenu.plusMenuComposerClass, lpparam.classLoader);
      composerImplCls =
          Reflect.findClass(cfg.plusMenu.plusMenuComposerImplClass, lpparam.classLoader);
      callbackCls = Reflect.findClass(cfg.plusMenu.plusMenuCallbackClass, lpparam.classLoader);
      modifierCls = Reflect.findClass(cfg.plusMenu.plusMenuModifierClass, lpparam.classLoader);
      clickItemCls = Reflect.findClass(cfg.plusMenu.plusMenuOnClickItemClass, lpparam.classLoader);
    } catch (Throwable t) {
      return;
    }

    final Method mainEntry =
        findMethodByParamSet(
            pCls,
            cfg.plusMenu.methodAddMenuItem,
            int.class,
            modifierCls,
            composerCls,
            clickItemCls,
            boolean.class);

    final Method itemEntry =
        findMethodByParamSet(
            pCls,
            cfg.plusMenu.methodCreateMenu,
            int.class,
            int.class,
            modifierCls,
            String.class,
            composerCls,
            callbackCls);

    if (mainEntry == null || itemEntry == null) return;

    final MenuItemFactory menu = new MenuItemFactory(itemEntry, composerCls, callbackCls);
    if (!menu.valid()) return;

    final Object readToggleCallback =
        generateToggleHandler(
            lpparam.classLoader,
            callbackCls,
            "prevent_read_state",
            ModuleStrings.LABEL_PREVENT_READ,
            true,
            null);
    final Object markToggleCallback =
        generateToggleHandler(
            lpparam.classLoader,
            callbackCls,
            "send_mark_state",
            ModuleStrings.LABEL_SEND_MARK_READ,
            false,
            "prevent_read_state");

    Knot.module
        .hook(mainEntry)
        .intercept(
            chain -> {
              isMenuDisplayed = true;
              currentReadState = SettingsStore.get("prevent_read_state", true);
              currentSendMarkState = SettingsStore.get("send_mark_state", false);
              try {
                return chain.proceed();
              } finally {
                isMenuDisplayed = false;
              }
            });

    Knot.module
        .hook(Reflect.findMethodExact(composerImplCls, cfg.plusMenu.methodExecuteAction))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (isMenuDisplayed && result != null) {
                menuContextScope = result;
              }
              return result;
            });

    Knot.module
        .hook(itemEntry)
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (!isMenuDisplayed || injectionActive) return result;

              if (targetDrawableId == 0) {
                try {
                  Context ctx = fetchApplicationContext();
                  if (ctx != null) {
                    targetDrawableId =
                        ctx.getResources()
                            .getIdentifier(
                                cfg.plusMenu.editChatDrawable, "drawable", cfg.plusMenu.targetPkg);
                  }
                } catch (Throwable ignored) {
                }
              }
              if (targetDrawableId == 0) return result;

              int iconId = (int) chain.getArg(0);
              if (iconId != targetDrawableId) return result;

              Object composer = chain.getArg(menu.composerIdx);
              injectionActive = true;
              try {
                if (Main.options.preventMarkAsRead.enabled) {
                  boolean readOn = currentReadState;
                  String labelR = ModuleStrings.LABEL_PREVENT_READ + ": " + (readOn ? "ON" : "OFF");
                  menu.add(readOn ? ID_READ_ON : ID_READ_OFF, labelR, composer, readToggleCallback);

                  if (readOn) {
                    boolean markOn = currentSendMarkState;
                    String labelM =
                        ModuleStrings.LABEL_SEND_MARK_READ + ": " + (markOn ? "ON" : "OFF");
                    menu.add(
                        markOn ? ID_MARK_ON : ID_MARK_OFF, labelM, composer, markToggleCallback);
                  }
                }
              } catch (Throwable t) {
                Knot.log("Knot: PlusMenu error: " + t);
              } finally {
                injectionActive = false;
              }
              return result;
            });

    Knot.module
        .hook(
            Reflect.findMethodExact(
                Resources.class, "getValue", int.class, TypedValue.class, boolean.class))
        .intercept(
            chain -> {
              int id = (int) chain.getArg(0);
              if ((id >>> 24) != 0x64) return chain.proceed();
              TypedValue tv = (TypedValue) chain.getArg(1);
              tv.string = "knot_res_" + Integer.toHexString(id) + ".png";
              tv.type = TypedValue.TYPE_STRING;
              return null;
            });

    Knot.module
        .hook(
            Reflect.findMethodExact(
                Resources.class, "getDrawable", int.class, Resources.Theme.class))
        .intercept(
            chain -> {
              int id = (int) chain.getArg(0);
              if ((id >>> 24) != 0x64) return chain.proceed();
              try {
                Bitmap b = retrieveModuleIcon(id, cfg);
                if (b != null) return new BitmapDrawable((Resources) chain.getThisObject(), b);
              } catch (Throwable ignored) {
              }
              return chain.proceed();
            });
  }

  private static Method findMethodByParamSet(Class<?> cls, String name, Class<?>... types) {
    for (Method m : cls.getDeclaredMethods()) {
      if (m.getName().equals(name) && sameTypeMultiset(m.getParameterTypes(), types)) return m;
    }
    return null;
  }

  private static boolean sameTypeMultiset(Class<?>[] a, Class<?>[] b) {
    if (a.length != b.length) return false;
    boolean[] used = new boolean[b.length];
    for (Class<?> t : a) {
      boolean found = false;
      for (int i = 0; i < b.length; i++) {
        if (!used[i] && b[i] == t) {
          used[i] = true;
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }

  private static int indexOfType(Class<?>[] params, Class<?> type) {
    for (int i = 0; i < params.length; i++) if (params[i] == type) return i;
    return -1;
  }

  private static final class MenuItemFactory {
    private final Method method;
    private final int paramCount;
    final int composerIdx;
    private final int callbackIdx;

    MenuItemFactory(Method method, Class<?> composerCls, Class<?> callbackCls) {
      this.method = method;
      Class<?>[] params = method.getParameterTypes();
      this.paramCount = params.length;
      this.composerIdx = indexOfType(params, composerCls);
      this.callbackIdx = indexOfType(params, callbackCls);
    }

    boolean valid() {
      return composerIdx >= 0 && callbackIdx >= 0;
    }

    void add(int id, String label, Object composer, Object callback) throws Exception {
      Object[] args = new Object[paramCount];
      args[0] = id;
      args[1] = 0;
      args[2] = null;
      args[3] = label;
      args[composerIdx] = composer;
      args[callbackIdx] = callback;
      method.invoke(null, args);
    }
  }

  private static Object generateToggleHandler(
      ClassLoader cl, Class<?> callbackCls, String key, String label, boolean def, String depKey) {
    return Proxy.newProxyInstance(
        cl,
        new Class[] {callbackCls},
        (proxy, method, args) -> {
          if ("invoke".equals(method.getName())) {
            if (depKey != null && !currentReadState) return null;
            boolean nextValue;
            if (key.equals("prevent_read_state")) {
              nextValue = !currentReadState;
              currentReadState = nextValue;
            } else {
              nextValue = !currentSendMarkState;
              currentSendMarkState = nextValue;
            }
            SettingsStore.save(key, nextValue);
            new Handler(Looper.getMainLooper())
                .post(
                    () -> {
                      if (menuContextScope != null) {
                        try {
                          Reflect.callMethod(menuContextScope, "invalidate");
                        } catch (Throwable ignored) {
                        }
                      }
                    });
            return null;
          }
          if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
          return null;
        });
  }

  private static Bitmap retrieveModuleIcon(int id, LineVersion.Config cfg) {
    Bitmap stored = iconStorage.get(id);
    if (stored != null) return stored;
    String name;
    if (id == ID_READ_ON) name = "ic_prevent_read_on";
    else if (id == ID_READ_OFF) name = "ic_prevent_read_off";
    else if (id == ID_MARK_ON) name = "ic_send_mark_read_on";
    else if (id == ID_MARK_OFF) name = "ic_send_mark_read_off";
    else return null;

    try {
      Context appCtx = fetchApplicationContext();
      if (appCtx == null) return null;
      Context modCtx =
          appCtx.createPackageContext(cfg.plusMenu.moduleId, Context.CONTEXT_IGNORE_SECURITY);
      int resId = modCtx.getResources().getIdentifier(name, "drawable", cfg.plusMenu.moduleId);
      Drawable d = modCtx.getResources().getDrawable(resId, null);
      Bitmap bmp = ((BitmapDrawable) d).getBitmap();
      iconStorage.put(id, bmp);
      return bmp;
    } catch (Throwable t) {
      return null;
    }
  }

  private static Context fetchApplicationContext() {
    return Knot.currentApplication();
  }
}
