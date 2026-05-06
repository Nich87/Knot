package app.zipper.knot.hooks;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RemoveTabs implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    XposedHelpers.findAndHookMethod(
        cfg.main.mainActivity,
        lpparam.classLoader,
        "onResume",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            Activity host = (Activity) param.thisObject;
            LineVersion.Config c = LineVersion.get();

            if (SettingsStore.get(config.removeTabVoom.key, config.removeTabVoom.enabled))
              deactivateTab(host, c.tabs.resVoom);
            if (SettingsStore.get(config.removeTabNews.key, config.removeTabNews.enabled))
              deactivateTab(host, c.tabs.resNews);
            if (SettingsStore.get(config.removeTabMini.key, config.removeTabMini.enabled))
              deactivateTab(host, c.tabs.resMini);
            if (SettingsStore.get(config.extendTabClickArea.key, config.extendTabClickArea.enabled))
              expandInteractionArea(host);
            if (SettingsStore.get(config.hideTabText.key, config.hideTabText.enabled))
              applyCompactLayout(host);
          }
        });

    try {
      Class<?> bnbLabelCls =
          XposedHelpers.findClass(cfg.tabs.bottomNavigationBarTextViewClass, lpparam.classLoader);
      XposedBridge.hookAllConstructors(
          bnbLabelCls,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              if (SettingsStore.get(config.hideTabText.key, config.hideTabText.enabled)) {
                ((View) param.thisObject).setVisibility(View.GONE);
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private static void expandInteractionArea(Activity host) {
    LineVersion.Config c = LineVersion.get();
    int rootId = host.getResources().getIdentifier(c.tabs.resContainer, "id", c.linePkg);
    if (rootId == 0) return;
    ViewGroup root = host.findViewById(rootId);
    if (root == null) return;
    for (int i = 2; i < root.getChildCount(); i += 2) {
      View child = root.getChildAt(i);
      if (!(child instanceof ViewGroup) || child.getVisibility() == View.GONE) continue;
      ViewGroup tab = (ViewGroup) child;
      ViewGroup.LayoutParams lp = tab.getLayoutParams();
      lp.width = 0;
      tab.setLayoutParams(lp);
      View clickable = tab.getChildAt(tab.getChildCount() - 1);
      if (clickable != null) {
        ViewGroup.LayoutParams clp = clickable.getLayoutParams();
        clp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        clickable.setLayoutParams(clp);
      }
    }
  }

  private static void deactivateTab(Activity host, String resName) {
    LineVersion.Config c = LineVersion.get();
    int id = host.getResources().getIdentifier(resName, "id", c.linePkg);
    if (id != 0) {
      View tab = host.findViewById(id);
      if (tab != null) tab.setVisibility(View.GONE);
    }
    int spacerId = host.getResources().getIdentifier(resName + "_spacer", "id", c.linePkg);
    if (spacerId != 0) {
      View spacer = host.findViewById(spacerId);
      if (spacer != null) spacer.setVisibility(View.GONE);
    }
  }

  private static void applyCompactLayout(Activity host) {
    LineVersion.Config c = LineVersion.get();
    int rootId = host.getResources().getIdentifier(c.tabs.resContainer, "id", c.linePkg);
    if (rootId == 0) return;
    ViewGroup root = host.findViewById(rootId);
    if (root == null) return;
    for (int i = 0; i < root.getChildCount(); i++) {
      View child = root.getChildAt(i);
      if (child instanceof ViewGroup) adjustTabDimensions(host, (ViewGroup) child);
    }
  }

  private static void adjustTabDimensions(Activity host, ViewGroup container) {
    LineVersion.Config c = LineVersion.get();
    int imgId = host.getResources().getIdentifier(c.tabs.resBtnImg, "id", c.linePkg);
    int animImgId = host.getResources().getIdentifier(c.tabs.resBtnAnimImg, "id", c.linePkg);
    int textId = host.getResources().getIdentifier(c.tabs.resBtnText, "id", c.linePkg);

    View label = container.findViewById(textId);
    if (label != null) label.setVisibility(View.GONE);

    View[] icons = {container.findViewById(imgId), container.findViewById(animImgId)};
    float scale = host.getResources().getDisplayMetrics().density;
    int w = (int) (80 * scale);
    int h = (int) (106 * scale);

    for (View v : icons) {
      if (v == null) continue;
      ViewGroup.LayoutParams lp = v.getLayoutParams();
      lp.width = w;
      lp.height = h;
      v.setLayoutParams(lp);
      if (v instanceof ImageView) {
        ImageView iv = (ImageView) v;
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setAdjustViewBounds(true);
      }
      v.setTranslationY(21 * scale);
    }
    for (int i = 0; i < container.getChildCount(); i++) {
      View child = container.getChildAt(i);
      if (child instanceof ViewGroup && child.getId() != imgId && child.getId() != animImgId)
        adjustTabDimensions(host, (ViewGroup) child);
    }
  }
}
