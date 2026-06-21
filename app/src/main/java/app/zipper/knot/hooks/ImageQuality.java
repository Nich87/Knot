package app.zipper.knot.hooks;

import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;
import io.github.libxposed.api.XposedInterface;

public class ImageQuality implements BaseHook {

  @Override
  public void hook(KnotConfig options, LoadParam lpparam) throws Throwable {
    if (!options.highQualityPhoto.enabled) return;

    LineVersion.Config v = LineVersion.get();
    if (v == null) return;

    hookQualityProfiles(lpparam.classLoader, v);
    hookImageUtil(lpparam.classLoader, v);
  }

  private void hookQualityProfiles(ClassLoader cl, LineVersion.Config v) {
    if (v.imageQuality.qualityProfileHighClass.isEmpty()) return;

    try {
      XposedInterface.Hooker maxDimensionHook = chain -> 99999;
      XposedInterface.Hooker qualityHook = chain -> 100;

      Class<?> highClass = Reflect.findClass(v.imageQuality.qualityProfileHighClass, cl);
      Knot.module
          .hook(Reflect.findMethodExact(highClass, v.imageQuality.methodGetMaxDimension))
          .intercept(maxDimensionHook);
      Knot.module
          .hook(Reflect.findMethodExact(highClass, v.imageQuality.methodGetQuality))
          .intercept(qualityHook);

      if (!v.imageQuality.qualityProfileMediumClass.isEmpty()) {
        Class<?> mediumClass = Reflect.findClass(v.imageQuality.qualityProfileMediumClass, cl);
        Knot.module
            .hook(Reflect.findMethodExact(mediumClass, v.imageQuality.methodGetMaxDimension))
            .intercept(maxDimensionHook);
        Knot.module
            .hook(Reflect.findMethodExact(mediumClass, v.imageQuality.methodGetQuality))
            .intercept(qualityHook);
      }

      Knot.log("Knot: Image quality profiles hooked");
    } catch (Throwable t) {
      Knot.debug("Knot: Failed to hook image quality profiles: " + t.getMessage());
    }
  }

  private void hookImageUtil(ClassLoader cl, LineVersion.Config v) {
    if (v.imageQuality.imageUtilClass.isEmpty()) return;

    try {
      Knot.module
          .hook(
              Reflect.findMethodExact(
                  android.graphics.Bitmap.class,
                  "compress",
                  android.graphics.Bitmap.CompressFormat.class,
                  int.class,
                  java.io.OutputStream.class))
          .intercept(
              chain -> {
                android.graphics.Bitmap.CompressFormat format =
                    (android.graphics.Bitmap.CompressFormat) chain.getArg(0);
                int quality = (int) chain.getArg(1);

                if (format == android.graphics.Bitmap.CompressFormat.JPEG && quality < 100) {
                  Object[] args = chain.getArgs().toArray();
                  args[1] = 100;
                  return chain.proceed(args);
                }
                return chain.proceed();
              });

      Knot.log("Knot: Bitmap.compress hooked");
    } catch (Throwable t) {
      Knot.debug("Knot: Failed to hook Bitmap.compress: " + t.getMessage());
    }
  }
}
