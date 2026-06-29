package app.zipper.knot.hooks;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoveAds implements BaseHook {

  private static final Map<String, Boolean> adClassCache = new ConcurrentHashMap<>();

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    applyLadAdHook(config, lpparam, cfg);
    applySmartChannelHook(config, lpparam, cfg);
    applyGenericAddViewHook(config);
  }

  private void applyLadAdHook(KnotConfig config, LoadParam lpparam, LineVersion.Config cfg) {
    try {
      Class<?> ladCls = lpparam.classLoader.loadClass(cfg.ads.ladAdView);
      Knot.module
          .hook(Reflect.findMethodExact(ladCls, "onAttachedToWindow"))
          .intercept(
              chain -> {
                hideAdWrapper((View) chain.getThisObject());
                return chain.proceed();
              });

      Knot.hookAll(
          ladCls,
          "setVisibility",
          chain -> {
            Object[] args = chain.getArgs().toArray();
            if ((int) args[0] == View.VISIBLE) {
              args[0] = View.GONE;
              return chain.proceed(args);
            }
            return chain.proceed();
          });
    } catch (Throwable ignored) {
    }
  }

  private void applySmartChannelHook(KnotConfig config, LoadParam lpparam, LineVersion.Config cfg) {
    try {
      Class<?> smartCls = lpparam.classLoader.loadClass(cfg.ads.smartChannel);
      Knot.module
          .hook(Reflect.findMethodExact(smartCls, "dispatchDraw", Canvas.class))
          .intercept(
              chain -> {
                hideAdWrapper((View) chain.getThisObject());
                return chain.proceed();
              });
    } catch (Throwable ignored) {
    }
  }

  private void applyGenericAddViewHook(KnotConfig config) {
    Knot.module
        .hook(
            Reflect.findMethodExact(
                ViewGroup.class, "addView", View.class, ViewGroup.LayoutParams.class))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              View view = (View) chain.getArg(0);
              if (isAdComponent(view.getClass().getName())) {
                hideAdWrapper(view);
              }
              return result;
            });
  }

  private static void hideAdWrapper(View view) {
    if (view == null) return;
    try {
      View current = view;
      int depth = 0;
      while (current != null && depth < 5) {
        current.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = current.getLayoutParams();
        if (lp != null) {
          lp.height = 0;
          if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
          }
          current.setLayoutParams(lp);
        }
        current.setPadding(0, 0, 0, 0);

        View parent = (View) current.getParent();
        if (!(parent instanceof ViewGroup)) break;

        ViewGroup vg = (ViewGroup) parent;
        String pName = vg.getClass().getSimpleName();
        if (pName.contains("RecyclerView")
            || pName.contains("ListView")
            || pName.contains("ViewPager")) {
          break;
        }

        boolean safeToHideParent = true;
        for (int i = 0; i < vg.getChildCount(); i++) {
          View child = vg.getChildAt(i);
          if (child != current && child.getVisibility() != View.GONE) {
            safeToHideParent = false;
            break;
          }
        }
        if (!safeToHideParent) {
          break;
        }

        current = parent;
        depth++;
      }
    } catch (Throwable ignored) {
    }
  }

  private static boolean isAdComponent(String className) {
    Boolean cached = adClassCache.get(className);
    if (cached != null) return cached;

    boolean result = performAdCheck(className);
    adClassCache.put(className, result);
    return result;
  }

  private static boolean performAdCheck(String className) {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg != null) {
      if (className.startsWith(cfg.ads.classAdSdkBase)) return true;
      if (className.startsWith(cfg.ads.classAdMolinBase)) return true;
    }
    String simpleName = className.substring(className.lastIndexOf('.') + 1);
    String lower = simpleName.toLowerCase();
    return simpleName.contains("NativeAd")
        || simpleName.contains("AdCard")
        || simpleName.contains("AdCell")
        || simpleName.contains("AdItem")
        || simpleName.contains("AdUnit")
        || simpleName.contains("AdView")
        || simpleName.contains("AdBanner")
        || simpleName.contains("AdModule")
        || lower.contains("sponsored")
        || lower.contains("promoted");
  }
}
