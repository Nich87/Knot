package app.zipper.knot.hooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class UnsendProtector implements BaseHook {

  private static final Map<String, String> unsendEvents =
      new ConcurrentHashMap<>();
  private static final Map<String, TextView> timestampViews =
      new ConcurrentHashMap<>();
  private static volatile Bitmap indicatorIcon;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    if (!config.preventUnsendMessage.enabled)
      return;
    initializeUnsendCache();

    LineVersion.Config cfg = LineVersion.get();

    try {
      XposedBridge.hookAllMethods(
          XposedHelpers.findClass(cfg.unsend.talkServiceHookClass,
                                  lpparam.classLoader),
          cfg.unsend.methodReadBuffer, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              if (!Main.options.preventUnsendMessage.enabled)
                return;
              try {
                handleIncomingOperation(param);
              } catch (Exception e) {
                XposedBridge.log("Knot: Unsend error: " + e);
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Unsend op hook failed: " + t);
    }

    if (cfg.unsend.unsendDestroyHandlerClass != null &&
        !cfg.unsend.unsendDestroyHandlerClass.isEmpty()) {
      try {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(cfg.unsend.unsendDestroyHandlerClass,
                                    lpparam.classLoader),
            "b", new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param)
                  throws Throwable {
                if (!Main.options.preventUnsendMessage.enabled)
                  return;
                // Signature: b(hf8.q0, fh8.ae, Continuation)
                java.lang.reflect.Method m =
                    (java.lang.reflect.Method)param.method;
                Class<?>[] types = m.getParameterTypes();
                if (types.length != 3 || !types[1].getName().equals("fh8.ae"))
                  return;

                Object aeVar = param.args[1];
                if (aeVar == null)
                  return;

                String msgId = (String)XposedHelpers.getObjectField(aeVar, "h");
                if (msgId == null || msgId.isEmpty())
                  return;

                if (!unsendEvents.containsKey(msgId)) {
                  XposedBridge.log("Knot: Blocked unsend (handler), id=" +
                                   msgId);
                  persistUnsendEvent(msgId, getFormattedTime());
                }

                XposedHelpers.setObjectField(aeVar, "h", "");

                TextView tsView = timestampViews.get(msgId);
                if (tsView != null)
                  applyUnsendIndicator(tsView, tsView.getContext(), msgId);
              }
            });
      } catch (Throwable t) {
        XposedBridge.log("Knot: Destroy handler hook failed: " + t);
      }
    }

    try {
      XposedBridge.hookAllMethods(
          XposedHelpers.findClass(cfg.unsend.chatMessageViewHolderClass,
                                  lpparam.classLoader),
          cfg.unsend.methodBind, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              if (!Main.options.preventUnsendMessage.enabled)
                return;
              try {
                handleViewHolderBinding(param);
              } catch (Exception e) {
                XposedBridge.log("Knot: Bind error: " + e);
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Bind hook failed: " + t);
    }
  }

  private static void initializeUnsendCache() {
    try {
      JSONObject json = SettingsStore.loadUnsendHistory();
      Iterator<String> keys = json.keys();
      while (keys.hasNext()) {
        String id = keys.next();
        unsendEvents.put(id, json.getString(id));
      }
    } catch (Exception e) {
      XposedBridge.log("Knot: Unsend history load error: " + e);
    }
  }

  private static synchronized void persistUnsendEvent(String msgId,
                                                      String timestamp) {
    unsendEvents.put(msgId, timestamp);
    try {
      JSONObject json = new JSONObject();
      for (Map.Entry<String, String> entry : unsendEvents.entrySet())
        json.put(entry.getKey(), entry.getValue());
      SettingsStore.saveUnsendHistory(json);
    } catch (Exception e) {
      XposedBridge.log("Knot: Unsend history save error: " + e);
    }
  }

  private static String getFormattedTime() {
    return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        .format(new Date());
  }

  private static void
  handleIncomingOperation(XC_MethodHook.MethodHookParam param)
      throws Exception {
    LineVersion.Config cfg = LineVersion.get();
    Object operation = param.args[1];
    if (operation == null || operation instanceof String)
      return;
    if (operation.getClass().getName().startsWith("java."))
      return;

    Object type =
        XposedHelpers.getObjectField(operation, cfg.unsend.operationTypeField);
    if (type == null)
      return;

    String typeStr = type.toString();
    if (!cfg.unsend.operationNotifiedUnsendName.equals(typeStr) &&
        !cfg.unsend.operationUnsendName.equals(typeStr))
      return;

    String msgId = (String)XposedHelpers.getObjectField(
        operation, cfg.unsend.operationParam2Field);
    if (msgId == null || msgId.isEmpty())
      return;

    if (!unsendEvents.containsKey(msgId)) {
      String time = getFormattedTime();
      XposedBridge.log("Knot: Blocked unsend, id=" + msgId);
      persistUnsendEvent(msgId, time);
    }

    Object harmlessType = XposedHelpers.callStaticMethod(
        type.getClass(), cfg.unsend.methodOperationTypeValueOf,
        cfg.unsend.operationTypeDummy);
    XposedHelpers.setObjectField(operation, cfg.unsend.operationTypeField,
                                 harmlessType);

    TextView tsView = timestampViews.get(msgId);
    if (tsView != null)
      applyUnsendIndicator(tsView, tsView.getContext(), msgId);
  }

  private static void
  handleViewHolderBinding(XC_MethodHook.MethodHookParam param)
      throws Exception {
    LineVersion.Config cfg = LineVersion.get();
    Object viewData = param.args[cfg.unsend.methodBindIndex];
    if (viewData == null)
      return;
    Object commonData =
        XposedHelpers.callMethod(viewData, cfg.unsend.methodGetCommonData);
    if (commonData == null)
      return;

    String msgId = (String)XposedHelpers.getObjectField(
        commonData, cfg.unsend.chatMessageIdField);
    View root = (View)XposedHelpers.callMethod(param.thisObject,
                                               cfg.unsend.methodGetItemView);
    if (root == null)
      return;

    TextView tsView = (TextView)root.findViewById(cfg.res.idTimestamp);
    if (tsView == null)
      return;

    resetViewProperties(tsView);
    if (msgId != null) {
      timestampViews.put(msgId, tsView);
      if (unsendEvents.containsKey(msgId))
        applyUnsendIndicator(tsView, root.getContext(), msgId);
    }
  }

  private static void applyUnsendIndicator(final TextView tsView,
                                           final Context context,
                                           final String msgId) {
    Bitmap rawIcon = resolveIndicatorIcon(context);
    if (rawIcon == null)
      return;
    float dens = tsView.getResources().getDisplayMetrics().density;
    final int targetPx = (int)(14 * dens);
    int padPx = (int)(3 * dens);

    Bitmap scaled =
        Bitmap.createScaledBitmap(rawIcon, targetPx, targetPx, true);
    Bitmap colored = applyTint(scaled, Color.RED);
    final BitmapDrawable draw =
        new BitmapDrawable(tsView.getResources(), colored);
    draw.setBounds(0, 0, targetPx, targetPx);

    tsView.post(() -> {
      tsView.setCompoundDrawables(null, null, draw, null);
      tsView.setCompoundDrawablePadding(padPx);
      tsView.setOnClickListener(v -> {
        String time = unsendEvents.get(msgId);
        if (time != null)
          Toast
              .makeText(context,
                        app.zipper.knot.utils.ModuleStrings.UNSET_TIME_PREFIX +
                            time,
                        Toast.LENGTH_SHORT)
              .show();
      });
    });
  }

  private static void resetViewProperties(final TextView tsView) {
    tsView.post(() -> {
      tsView.setCompoundDrawables(null, null, null, null);
      tsView.setCompoundDrawablePadding(0);
      tsView.setOnClickListener(null);
      tsView.setClickable(false);
    });
  }

  private static Bitmap resolveIndicatorIcon(Context ctx) {
    if (indicatorIcon != null)
      return indicatorIcon;
    try {
      Context modCtx = ctx.createPackageContext(
          "app.zipper.knot", Context.CONTEXT_IGNORE_SECURITY);
      int resId = modCtx.getResources().getIdentifier("message_off", "drawable",
                                                      "app.zipper.knot");
      if (resId != 0) {
        indicatorIcon =
            BitmapFactory.decodeResource(modCtx.getResources(), resId);
      }
    } catch (Exception ignored) {
    }
    return indicatorIcon;
  }

  private static Bitmap applyTint(Bitmap src, int color) {
    Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                                     Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(src, 0, 0, p);
    return out;
  }
}
