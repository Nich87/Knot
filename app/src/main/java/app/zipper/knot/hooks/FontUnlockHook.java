package app.zipper.knot.hooks;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.EditText;
import android.widget.TextView;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;

public class FontUnlockHook implements BaseHook {
  private static Typeface customTypeface = null;
  private static boolean overrideActive = false;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cfg.font.fontConfigClass.isEmpty()) return;

    initTypeface();
    if (!overrideActive || customTypeface == null) return;

    XposedBridge.log("Knot: Initializing Font hooks");

    try {
      XposedHelpers.findAndHookMethod(
          TextView.class,
          "setIncludeFontPadding",
          boolean.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              param.args[0] = false;
            }
          });
    } catch (Throwable ignored) {
    }

    XC_MethodHook globalHook =
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            param.setResult(customTypeface);
          }
        };
    XposedHelpers.findAndHookMethod(Typeface.class, "create", String.class, int.class, globalHook);
    XposedHelpers.findAndHookMethod(
        Typeface.class, "create", Typeface.class, int.class, globalHook);
    XposedHelpers.findAndHookMethod(Typeface.class, "defaultFromStyle", int.class, globalHook);

    XC_MethodHook textViewHook =
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            if (!overrideActive || customTypeface == null) return;
            if (param.args.length > 0 && param.args[0] instanceof Typeface)
              param.args[0] = customTypeface;
            if (param.thisObject instanceof TextView) {
              TextView tv = (TextView) param.thisObject;
              tv.setIncludeFontPadding(false);
              if (tv instanceof EditText
                  || (cfg.res.idChatMessageText != 0 && tv.getId() == cfg.res.idChatMessageText)) {
                tv.setPadding(tv.getPaddingLeft(), 0, tv.getPaddingRight(), 0);
              }
            }
          }
        };

    try {
      XposedHelpers.findAndHookMethod(
          TextView.class, "setTypeface", Typeface.class, int.class, textViewHook);
      XposedHelpers.findAndHookMethod(TextView.class, "setTypeface", Typeface.class, textViewHook);
      XC_MethodHook constructHook =
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              if (overrideActive && customTypeface != null && param.thisObject instanceof TextView)
                ((TextView) param.thisObject).setIncludeFontPadding(false);
            }
          };
      XposedHelpers.findAndHookConstructor(
          TextView.class,
          android.content.Context.class,
          android.util.AttributeSet.class,
          constructHook);
      XposedHelpers.findAndHookConstructor(
          TextView.class,
          android.content.Context.class,
          android.util.AttributeSet.class,
          int.class,
          constructHook);
    } catch (Throwable ignored) {
    }

    XC_MethodHook metricsHook =
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (!overrideActive || customTypeface == null) return;
            Paint paint = (Paint) param.thisObject;
            float textSize = paint.getTextSize();
            if (textSize <= 0) return;
            Object m =
                (param.args.length > 0
                        && (param.args[param.args.length - 1] instanceof Paint.FontMetricsInt
                            || param.args[param.args.length - 1] instanceof Paint.FontMetrics))
                    ? param.args[param.args.length - 1]
                    : param.getResult();
            if (m instanceof Paint.FontMetricsInt) {
              Paint.FontMetricsInt fmi = (Paint.FontMetricsInt) m;
              fmi.ascent = Math.round(-textSize * 0.95f);
              fmi.descent = Math.round(textSize * 0.20f);
              fmi.top = fmi.ascent;
              fmi.bottom = fmi.descent;
              fmi.leading = 0;
            } else if (m instanceof Paint.FontMetrics) {
              Paint.FontMetrics fm = (Paint.FontMetrics) m;
              fm.ascent = -textSize * 0.95f;
              fm.descent = textSize * 0.20f;
              fm.top = fm.ascent;
              fm.bottom = fm.descent;
              fm.leading = 0;
            } else if ("getFontSpacing".equals(param.method.getName()))
              param.setResult(textSize * 1.15f);
          }
        };

    try {
      XposedHelpers.findAndHookMethod(
          Paint.class, "getFontMetricsInt", Paint.FontMetricsInt.class, metricsHook);
      XposedHelpers.findAndHookMethod(Paint.class, "getFontMetricsInt", metricsHook);
      XposedHelpers.findAndHookMethod(
          Paint.class, "getFontMetrics", Paint.FontMetrics.class, metricsHook);
      XposedHelpers.findAndHookMethod(Paint.class, "getFontMetrics", metricsHook);
      XposedHelpers.findAndHookMethod(Paint.class, "getFontSpacing", metricsHook);
      try {
        XposedHelpers.findAndHookMethod(
            Paint.class,
            "getFontMetricsInt",
            CharSequence.class,
            int.class,
            int.class,
            int.class,
            int.class,
            boolean.class,
            Paint.FontMetricsInt.class,
            metricsHook);
      } catch (Throwable ignored) {
      }
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          TextView.class,
          "onMeasure",
          int.class,
          int.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (overrideActive && customTypeface != null) {
                TextView tv = (TextView) param.thisObject;
                tv.setIncludeFontPadding(false);
                if (tv instanceof EditText)
                  tv.setPadding(tv.getPaddingLeft(), 0, tv.getPaddingRight(), 0);
                try {
                  XposedHelpers.callMethod(tv, "setFallbackLineSpacing", false);
                } catch (Throwable ignored) {
                }
              }
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          Paint.class,
          "setElegantTextHeight",
          boolean.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (overrideActive && customTypeface != null) param.args[0] = false;
            }
          });
    } catch (Throwable ignored) {
    }

    XposedHelpers.findAndHookMethod(
        cfg.font.fontConfigClass,
        lpparam.classLoader,
        cfg.font.methodGetFontConfig,
        android.content.Context.class,
        java.util.List.class,
        int.class,
        boolean.class,
        int.class,
        android.os.Handler.class,
        cfg.font.fontCallbackClass,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            Object callback = param.args[6];
            if (callback != null)
              try {
                XposedHelpers.callMethod(callback, cfg.font.methodOnFontChanged, customTypeface);
              } catch (Throwable ignored) {
              }
            param.setResult(customTypeface);
          }
        });

    try {
      XposedHelpers.findAndHookMethod(
          cfg.font.fontCallbackClass,
          lpparam.classLoader,
          cfg.font.methodOnFontChanged,
          Typeface.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              param.args[0] = customTypeface;
            }
          });
    } catch (Throwable ignored) {
    }

    XposedHelpers.findAndHookMethod(
        cfg.font.fontManagerClass,
        lpparam.classLoader,
        "c",
        android.content.Context.class,
        java.util.List.class,
        int.class,
        "k6.p",
        "k6.c",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            param.setResult(customTypeface);
          }
        });

    XposedHelpers.findAndHookMethod(
        cfg.font.fontManagerClass,
        lpparam.classLoader,
        cfg.font.methodInitializeFont,
        String.class,
        android.content.Context.class,
        java.util.List.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            Object res = param.getResult();
            if (res != null)
              XposedHelpers.setObjectField(res, cfg.font.fieldTypeface, customTypeface);
          }
        });

    try {
      XposedHelpers.findAndHookMethod(
          cfg.font.fontSettingsClass,
          lpparam.classLoader,
          cfg.font.methodGetFontSettings,
          int.class,
          cfg.font.fontInjectedClass,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              param.setResult(customTypeface);
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void initTypeface() {
    if (!SettingsStore.get("use_custom_font", false)) {
      overrideActive = false;
      return;
    }
    String path = SettingsStore.getString("custom_font_path", "");
    if (!path.isEmpty()) {
      File f = new File(path);
      if (f.exists()) {
        try {
          customTypeface = Typeface.createFromFile(f);
          if (customTypeface != null) {
            overrideActive = true;
            XposedBridge.log("Knot: Custom font loaded: " + path);
            return;
          }
        } catch (Throwable t) {
          XposedBridge.log("Knot: Failed to load font: " + t.getMessage());
        }
      }
    }
    overrideActive = false;
  }
}
