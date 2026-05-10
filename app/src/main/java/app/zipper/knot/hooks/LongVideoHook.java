package app.zipper.knot.hooks;

import android.net.Uri;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Map;

public class LongVideoHook implements BaseHook {

  @Override
  public void hook(KnotConfig options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!options.longVideo.enabled) return;

    LineVersion.Config v = LineVersion.get();
    if (v == null) return;

    hookDurationCheck(lpparam.classLoader, v);
    hookMediaPickerParams(lpparam.classLoader, v);
    hookGalleryController(lpparam.classLoader, v);
    hookProfileTrimmer(lpparam.classLoader, v);
    hookSmartDurationBypass(lpparam.classLoader, v);
    hookDroppedMediaPreprocessor(lpparam.classLoader, v);
    hookPickerSelectionValidator(lpparam.classLoader, v);
  }

  private void hookDurationCheck(ClassLoader cl, LineVersion.Config v) {
    if (v.media.videoDurationCheckClass.isEmpty()) return;
    try {
      Class<?> checkClass = XposedHelpers.findClass(v.media.videoDurationCheckClass, cl);
      Class<?> resultClass = XposedHelpers.findClass(v.media.videoDurationSuccessClass, cl);
      Object success =
          XposedHelpers.getStaticObjectField(resultClass, v.media.fieldVideoDurationSuccess);

      XC_MethodHook bypassHook =
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              param.setResult(success);
            }
          };

      XposedHelpers.findAndHookMethod(
          checkClass,
          v.media.videoDurationCheckMethod,
          Uri.class,
          Map.class,
          boolean.class,
          bypassHook);
      XposedHelpers.findAndHookMethod(checkClass, "a", Uri.class, bypassHook);
      XposedHelpers.findAndHookMethod(checkClass, "b", Uri.class, bypassHook);
    } catch (Throwable ignored) {
    }
  }

  private void hookMediaPickerParams(ClassLoader cl, LineVersion.Config v) {
    if (v.media.mediaPickerParamsClass.isEmpty()) return;
    try {
      Class<?> paramsClass = XposedHelpers.findClass(v.media.mediaPickerParamsClass, cl);
      XposedHelpers.findAndHookConstructor(
          paramsClass,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              XposedHelpers.setLongField(
                  param.thisObject,
                  v.media.fieldMediaPickerMaxVideoDuration,
                  (long) Integer.MAX_VALUE);
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookGalleryController(ClassLoader cl, LineVersion.Config v) {
    if (v.media.galleryViewClass.isEmpty()) return;
    try {
      Class<?> galleryViewClass = XposedHelpers.findClass(v.media.galleryViewClass, cl);
      XposedHelpers.setStaticLongField(
          galleryViewClass, v.media.fieldGalleryDurationLimit, (long) Integer.MAX_VALUE);
    } catch (Throwable ignored) {
    }
  }

  private void hookSmartDurationBypass(ClassLoader cl, LineVersion.Config v) {
    try {
      XposedHelpers.findAndHookMethod(
          android.media.MediaPlayer.class,
          "getDuration",
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              applySpoof(param, v);
            }
          });

      XposedHelpers.findAndHookMethod(
          android.media.MediaMetadataRetriever.class,
          "extractMetadata",
          int.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              if ((int) param.args[0] == 9) {
                applySpoof(param, v);
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void applySpoof(XC_MethodHook.MethodHookParam param, LineVersion.Config v) {
    Object result = param.getResult();
    long duration = 0;
    if (result instanceof Integer) {
      duration = (int) result;
    } else if (result instanceof String) {
      try {
        duration = Long.parseLong((String) result);
      } catch (Exception e) {
        return;
      }
    } else {
      return;
    }

    if (duration > 300000 && shouldSpoof(v)) {
      if (result instanceof Integer) {
        param.setResult(1000);
      } else {
        param.setResult("1000");
      }
    }
  }

  private boolean shouldSpoof(LineVersion.Config v) {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    for (StackTraceElement element : stackTrace) {
      String className = element.getClassName();

      if (className.startsWith(v.media.videoDurationCheckClass)
          || className.startsWith(v.media.droppedMediaPreprocessorClass)
          || className.contains("VideoValidator")
          || className.contains("MediaLimitCheck")) {
        return true;
      }

      if (className.startsWith("kc1.")
          || className.startsWith("lc1.")
          || className.startsWith("com.linecorp.line.media.picker")
          || className.contains("ViewHolder")
          || className.contains("ViewData")
          || className.contains("Adapter")
          || className.contains("Formatter")
          || className.contains("Thumbnail")) {
        return false;
      }
    }
    for (StackTraceElement element : stackTrace) {
      if (element.getClassName().startsWith("jp.naver.line.android")
          || element.getClassName().startsWith("com.linecorp.line")) {
        return true;
      }
    }
    return false;
  }

  private void hookDroppedMediaPreprocessor(ClassLoader cl, LineVersion.Config v) {
    if (v.media.droppedMediaPreprocessorClass.isEmpty()) return;
    try {
      XposedHelpers.findAndHookMethod(
          v.media.droppedMediaPreprocessorClass,
          cl,
          "invokeSuspend",
          Object.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              Object result = param.getResult();
              if (result != null && result.toString().equals("EXCEEDS_LIMIT")) {
                param.setResult(null);
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookPickerSelectionValidator(ClassLoader cl, LineVersion.Config v) {
    if (v.media.selectionValidatorClass.isEmpty()) return;
    try {
      XposedHelpers.findAndHookMethod(
          v.media.selectionValidatorClass,
          cl,
          v.media.selectionValidatorMethod,
          v.media.selectionValidatorParamClass,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              param.setResult(false);
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookProfileTrimmer(ClassLoader cl, LineVersion.Config v) {
    if (v.media.videoProfileTrimmerActivityClass.isEmpty()) return;
    try {
      XposedHelpers.findAndHookMethod(
          v.media.videoProfileTrimmerActivityClass,
          cl,
          "onCreate",
          android.os.Bundle.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              XposedHelpers.setIntField(
                  param.thisObject, v.media.fieldVideoProfileTrimmerLimit, Integer.MAX_VALUE);
            }
          });
    } catch (Throwable ignored) {
    }
  }
}
