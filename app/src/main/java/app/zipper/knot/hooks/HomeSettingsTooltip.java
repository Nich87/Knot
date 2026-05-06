package app.zipper.knot.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HomeSettingsTooltip implements BaseHook {

  private static final String SHOWN_KEY = "knot_settings_tooltip_shown";
  private static volatile PopupWindow activePopup = null;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null
        || cfg.res.resSettingsHeaderBtn.isEmpty()
        || cfg.searchBarAgentI.homeSearchBarClass.isEmpty()
        || cfg.searchBarAgentI.homeRefreshMethod.isEmpty()) return;

    XposedHelpers.findAndHookMethod(
        cfg.searchBarAgentI.homeSearchBarClass,
        lpparam.classLoader,
        cfg.searchBarAgentI.homeRefreshMethod,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (isHomeSearchBar(cfg, param.thisObject)) {
              View rootView =
                  (View)
                      XposedHelpers.getObjectField(
                          param.thisObject, cfg.searchBarAgentI.homeRootViewField);
              if (rootView != null) {
                onHomeTabEntered(rootView, cfg);
              }
            } else {
              dismissSilently();
            }
          }
        });
  }

  private static boolean isHomeSearchBar(LineVersion.Config cfg, Object instance) {
    if (cfg.searchBarAgentI.homeTabTypeField.isEmpty()) return false;
    Object tabType = XposedHelpers.getObjectField(instance, cfg.searchBarAgentI.homeTabTypeField);
    if (!(tabType instanceof Enum<?>)) return false;
    String name = ((Enum<?>) tabType).name();
    return name.equals(cfg.searchBarAgentI.homeTabName)
        || name.equals(cfg.searchBarAgentI.homeTabV2Name);
  }

  private static void onHomeTabEntered(View homeView, LineVersion.Config cfg) {
    if (SettingsStore.get(SHOWN_KEY, false)) return;

    android.graphics.Rect rect = new android.graphics.Rect();
    if (homeView.getGlobalVisibleRect(rect) && !rect.isEmpty()) {
      triggerShow(homeView, cfg);
    } else {
      homeView
          .getViewTreeObserver()
          .addOnGlobalLayoutListener(
              new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                  if (SettingsStore.get(SHOWN_KEY, false)) {
                    removeListener(homeView, this);
                    return;
                  }
                  android.graphics.Rect r = new android.graphics.Rect();
                  if (homeView.getGlobalVisibleRect(r) && !r.isEmpty()) {
                    triggerShow(homeView, cfg);
                    removeListener(homeView, this);
                  }
                }
              });
    }
  }

  private static void triggerShow(View homeView, LineVersion.Config cfg) {
    if (!homeView.isShown() || !SettingsStore.isConfigured()) return;

    Activity host = findActivity(homeView.getContext());
    if (host == null) return;

    host.runOnUiThread(
        () -> {
          if (activePopup != null && activePopup.isShowing()) return;
          View btn =
              findViewByEntryName(host.getWindow().getDecorView(), cfg.res.resSettingsHeaderBtn);
          if (btn == null) return;

          if (btn.getWidth() > 0) {
            showTooltip(host, btn, homeView);
          } else {
            btn.post(
                () -> {
                  if (btn.getWidth() > 0) showTooltip(host, btn, homeView);
                });
          }
        });
  }

  public static void markShown() {
    dismissSilently();
    SettingsStore.save(SHOWN_KEY, true);
  }

  private static void dismissSilently() {
    PopupWindow p = activePopup;
    activePopup = null;
    if (p != null) {
      try {
        p.dismiss();
      } catch (Throwable ignored) {
      }
    }
  }

  private static void removeListener(View v, ViewTreeObserver.OnGlobalLayoutListener l) {
    try {
      v.getViewTreeObserver().removeOnGlobalLayoutListener(l);
    } catch (Throwable ignored) {
    }
  }

  private static View findViewByEntryName(View root, String entryName) {
    if (root == null) return null;
    int id = root.getId();
    if (id != View.NO_ID) {
      try {
        if (entryName.equals(root.getResources().getResourceEntryName(id))) return root;
      } catch (Throwable ignored) {
      }
    }
    if (!(root instanceof ViewGroup)) return null;
    ViewGroup group = (ViewGroup) root;
    for (int i = 0; i < group.getChildCount(); i++) {
      View found = findViewByEntryName(group.getChildAt(i), entryName);
      if (found != null) return found;
    }
    return null;
  }

  private static Activity findActivity(Context ctx) {
    while (ctx instanceof ContextWrapper) {
      if (ctx instanceof Activity) return (Activity) ctx;
      ctx = ((ContextWrapper) ctx).getBaseContext();
    }
    return null;
  }

  private static void showTooltip(Activity host, View anchor, View homeView) {
    try {
      LineVersion.Config cfg = LineVersion.get();
      String pkg = cfg.linePkg;
      float dp = host.getResources().getDisplayMetrics().density;

      ImageView arrow = new ImageView(host);
      int arrowId = host.getResources().getIdentifier(cfg.res.resTooltipArrowUp, "drawable", pkg);
      if (arrowId != 0) arrow.setImageResource(arrowId);
      arrow.measure(
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      LinearLayout.LayoutParams arrowLp =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      arrowLp.gravity = Gravity.END;
      arrowLp.rightMargin = Math.max(0, anchor.getWidth() / 2 - arrow.getMeasuredWidth() / 2);

      TextView text = new TextView(host);
      text.setText(ModuleStrings.TOOLTIP_SETTINGS_LONG_PRESS);
      text.setTextColor(Color.WHITE);
      text.setTextSize(13f);
      int ph = (int) (12 * dp), pv = (int) (7 * dp);
      text.setPadding(ph, pv, ph, pv);
      int bgId = host.getResources().getIdentifier(cfg.res.resTooltipBackground, "drawable", pkg);
      if (bgId != 0) text.setBackgroundResource(bgId);

      LinearLayout container = new LinearLayout(host);
      container.setOrientation(LinearLayout.VERTICAL);
      container.addView(arrow, arrowLp);
      container.addView(
          text,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      container.measure(
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

      PopupWindow popup =
          new PopupWindow(
              container,
              LinearLayout.LayoutParams.WRAP_CONTENT,
              LinearLayout.LayoutParams.WRAP_CONTENT);
      popup.setOutsideTouchable(true);
      popup.setFocusable(false);
      popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

      container.setOnClickListener(v -> markShown());

      popup.showAsDropDown(anchor, anchor.getWidth() - container.getMeasuredWidth(), 0);
      activePopup = popup;

      homeView
          .getViewTreeObserver()
          .addOnScrollChangedListener(
              new ViewTreeObserver.OnScrollChangedListener() {
                @Override
                public void onScrollChanged() {
                  android.graphics.Rect r = new android.graphics.Rect();
                  if (!homeView.getGlobalVisibleRect(r) || r.isEmpty()) {
                    dismissSilently();
                    try {
                      homeView.getViewTreeObserver().removeOnScrollChangedListener(this);
                    } catch (Throwable ignored) {
                    }
                  }
                }
              });

    } catch (Throwable t) {
      XposedBridge.log("Knot: HomeSettingsTooltip error: " + t);
    }
  }
}
