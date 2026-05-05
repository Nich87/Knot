package app.zipper.knot.hooks;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RemoveHomeContents implements BaseHook {

  private static int recId = 0;
  private static int svcCarouselId = 0;
  private static int svcTitleId = 0;
  private static int noServicesId = 0;
  private static boolean isSetupDone = false;
  private static Object emptySectionInstance = null;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    XposedHelpers.findAndHookMethod(
        cfg.main.mainActivity, lpparam.classLoader, "onResume",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
            if (isSetupDone)
              return;
            android.app.Activity host = (android.app.Activity)param.thisObject;
            LineVersion.Config c = LineVersion.get();
            String pkg = c.linePkg;
            recId = host.getResources().getIdentifier(c.home.resRecommendation,
                                                      "id", pkg);
            svcCarouselId = host.getResources().getIdentifier(
                c.home.resServiceCarouselId, "id", pkg);
            svcTitleId = host.getResources().getIdentifier(
                c.home.resServiceTitleId, "id", pkg);
            noServicesId = host.getResources().getIdentifier(
                c.home.resNoServicesId, "id", pkg);
            isSetupDone = true;
          }
        });

    XposedHelpers.findAndHookMethod(
        View.class, "onAttachedToWindow", new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
            View target = (View)param.thisObject;
            int id = target.getId();
            if (id == View.NO_ID)
              return;

            if (id == recId && recId != 0) {
              if (SettingsStore.get(config.removeHomeRecommendations.key,
                                    config.removeHomeRecommendations.enabled))
                hideView(target);
              return;
            }

            if (id == svcCarouselId && svcCarouselId != 0) {
              if (SettingsStore.get(config.removeHomeServices.key,
                                    config.removeHomeServices.enabled))
                hideView(target);
              return;
            }

            if ((id == svcTitleId && svcTitleId != 0) ||
                (id == noServicesId && noServicesId != 0)) {
              if (SettingsStore.get(config.removeHomeServices.key,
                                    config.removeHomeServices.enabled)) {
                ViewParent parent = target.getParent();
                if (parent instanceof View)
                  hideView((View)parent);
              }
            }
          }
        });

    LineVersion.Config c = LineVersion.get();
    if (c == null || c.home.lypRecommendationControllerClass.isEmpty() ||
        c.home.lypRecommendationModuleArgClass.isEmpty() ||
        c.home.lypRecommendationContextClass.isEmpty() ||
        c.home.lypRecommendationComposerClass.isEmpty())
      return;

    XposedHelpers.findAndHookMethod(
        c.home.lypRecommendationControllerClass, lpparam.classLoader, "a",
        String.class, c.home.lypRecommendationModuleArgClass,
        c.home.lypRecommendationContextClass,
        c.home.lypRecommendationComposerClass, new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
            if (!SettingsStore.get(config.removeHomeAccordion.key,
                                   config.removeHomeAccordion.enabled))
              return;

            Object module = param.args[1];
            if (module == null ||
                !module.getClass()
                     .getName()
                     .equals(c.home.lypRecommendationModuleClass))
              return;

            param.setResult(getEmptySectionInstance(lpparam.classLoader));
          }
        });
  }

  private static void hideView(View target) {
    target.setVisibility(View.GONE);
    ViewGroup.LayoutParams params = target.getLayoutParams();
    if (params != null && params.height != 0) {
      params.height = 0;
      target.setLayoutParams(params);
    }
  }

  private static Object getEmptySectionInstance(ClassLoader classLoader) {
    if (emptySectionInstance != null)
      return emptySectionInstance;
    Class<?> sectionClass = XposedHelpers.findClass("l02.e", classLoader);
    emptySectionInstance =
        XposedHelpers.getStaticObjectField(sectionClass, "e");
    return emptySectionInstance;
  }
}
