package app.zipper.knot.hooks;

import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Main;
import app.zipper.knot.Reflect;

public class UseDefaultCameraHook implements BaseHook {

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    if (!config.useDefaultCamera.enabled) return;
    LineVersion.Config version = LineVersion.get();
    if (version == null || version.camera.cameraModuleClass.isEmpty()) return;

    Knot.module
        .hook(
            Reflect.findMethodExact(
                version.camera.cameraModuleClass,
                lpparam.classLoader,
                version.camera.methodUseExternalCamera))
        .intercept(chain -> Main.options.useDefaultCamera.enabled ? Boolean.TRUE : chain.proceed());
  }
}
