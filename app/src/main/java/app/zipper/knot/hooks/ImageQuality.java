package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ImageQuality implements BaseHook {

  @Override
  public void hook(KnotConfig options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!options.highQualityPhoto.enabled) return;

    LineVersion.Config v = LineVersion.get();
    if (v == null) return;

    hookQualityProfiles(lpparam.classLoader, v);
    hookImageUtil(lpparam.classLoader, v);
  }

  private void hookQualityProfiles(ClassLoader cl, LineVersion.Config v) {
    if (v.imageQuality.qualityProfileHighClass.isEmpty()) return;

    try {
      XC_MethodHook qualityHook =
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              if (v.imageQuality.methodGetMaxDimension.equals(param.method.getName())) {
                param.setResult(99999);
              } else if (v.imageQuality.methodGetQuality.equals(param.method.getName())) {
                param.setResult(100);
              }
            }
          };

      Class<?> highClass = XposedHelpers.findClass(v.imageQuality.qualityProfileHighClass, cl);
      XposedHelpers.findAndHookMethod(highClass, v.imageQuality.methodGetMaxDimension, qualityHook);
      XposedHelpers.findAndHookMethod(highClass, v.imageQuality.methodGetQuality, qualityHook);

      if (!v.imageQuality.qualityProfileMediumClass.isEmpty()) {
        Class<?> mediumClass =
            XposedHelpers.findClass(v.imageQuality.qualityProfileMediumClass, cl);
        XposedHelpers.findAndHookMethod(
            mediumClass, v.imageQuality.methodGetMaxDimension, qualityHook);
        XposedHelpers.findAndHookMethod(mediumClass, v.imageQuality.methodGetQuality, qualityHook);
      }

      XposedBridge.log("Knot: Image quality profiles hooked");
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook image quality profiles: " + t.getMessage());
    }
  }

  private void hookImageUtil(ClassLoader cl, LineVersion.Config v) {
    if (v.imageQuality.imageUtilClass.isEmpty()) return;

    try {
      XposedHelpers.findAndHookMethod(
          android.graphics.Bitmap.class,
          "compress",
          android.graphics.Bitmap.CompressFormat.class,
          int.class,
          java.io.OutputStream.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              android.graphics.Bitmap.CompressFormat format =
                  (android.graphics.Bitmap.CompressFormat) param.args[0];
              int quality = (int) param.args[1];

              if (format == android.graphics.Bitmap.CompressFormat.JPEG && quality < 100) {
                param.args[1] = 100;
              }
            }
          });

      XposedBridge.log("Knot: Bitmap.compress hooked");
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook Bitmap.compress: " + t.getMessage());
    }
  }
}
