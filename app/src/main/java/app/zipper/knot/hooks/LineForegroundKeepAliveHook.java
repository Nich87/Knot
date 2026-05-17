package app.zipper.knot.hooks;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LineForegroundKeepAliveHook implements BaseHook {

  private static final String KEEP_ALIVE_ACTION = "app.zipper.knot.action.LINE_KEEP_ALIVE";
  private static final String KEEP_ALIVE_CHANNEL_ID = "knot_line_keep_alive";
  private static final int KEEP_ALIVE_NOTIFICATION_ID = 0x4b4e5446;
  private static final String NOTIFICATION_TEXT = "この常設通知は通知設定から非表示にできます。";
  private static volatile Context appContext;
  private static volatile boolean keepAliveRequested;

  private static boolean isEnabled(KnotConfig config) {
    return config.lineForegroundKeepAlive.enabled;
  }

  private static void log(String message) {
    XposedBridge.log("Knot: " + message);
  }

  private static Notification buildNotification(Context context) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
      NotificationChannel channel =
          new NotificationChannel(
              KEEP_ALIVE_CHANNEL_ID, "LINE background", NotificationManager.IMPORTANCE_LOW);
      channel.setDescription(NOTIFICATION_TEXT);
      channel.setShowBadge(false);
      notificationManager.createNotificationChannel(channel);
    }

    int smallIcon = context.getApplicationInfo().icon;
    if (smallIcon == 0) {
      smallIcon = android.R.drawable.stat_notify_sync;
    }

    Notification.Builder builder =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(context, KEEP_ALIVE_CHANNEL_ID)
            : new Notification.Builder(context);
    builder
        .setSmallIcon(smallIcon)
        .setContentTitle("LINE")
        .setContentText(NOTIFICATION_TEXT)
        .setStyle(new Notification.BigTextStyle().bigText(NOTIFICATION_TEXT))
        .setOngoing(true)
        .setShowWhen(false)
        .setLocalOnly(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .addAction(android.R.drawable.ic_menu_manage, "通知設定", buildSettingsPendingIntent(context));

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      builder.setPriority(Notification.PRIORITY_LOW);
    }

    return builder.build();
  }

  private static PendingIntent buildSettingsPendingIntent(Context context) {
    Intent intent;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
      intent.putExtra(Settings.EXTRA_CHANNEL_ID, KEEP_ALIVE_CHANNEL_ID);
    } else {
      intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.setData(Uri.parse("package:" + context.getPackageName()));
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    return PendingIntent.getActivity(context, 0, intent, flags);
  }

  private static void startKeepAlive(Service service) {
    Notification notification = buildNotification(service);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      service.startForeground(
          KEEP_ALIVE_NOTIFICATION_ID,
          notification,
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
      return;
    }
    service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification);
  }

  private static void requestKeepAlive(
      Context context, LineVersion.Config.ForegroundKeepAlive keepAliveCfg) {
    if (context == null || keepAliveCfg == null || keepAliveCfg.serviceClass.isEmpty()) {
      return;
    }

    Context targetContext = context.getApplicationContext();
    if (targetContext == null) {
      targetContext = context;
    }

    try {
      Intent intent = new Intent(KEEP_ALIVE_ACTION);
      intent.setClassName(targetContext.getPackageName(), keepAliveCfg.serviceClass);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        targetContext.startForegroundService(intent);
      } else {
        targetContext.startService(intent);
      }
      log("requested LINE foreground keep-alive service");
    } catch (Throwable t) {
      XposedBridge.log("Knot: failed to start LINE foreground keep-alive service: " + t);
    }
  }

  private static void scheduleKeepAlive(
      Context context, LineVersion.Config.ForegroundKeepAlive keepAliveCfg) {
    if (keepAliveRequested
        || context == null
        || keepAliveCfg == null
        || keepAliveCfg.serviceClass.isEmpty()) {
      return;
    }

    keepAliveRequested = true;
    Handler handler = new Handler(Looper.getMainLooper());
    handler.postDelayed(() -> requestKeepAlive(context, keepAliveCfg), 1500L);
    handler.postDelayed(() -> requestKeepAlive(context, keepAliveCfg), 20000L);
  }

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!isEnabled(config)) {
      return;
    }

    LineVersion.Config versionConfig = LineVersion.get();
    if (versionConfig == null || versionConfig.foregroundKeepAlive == null) {
      return;
    }

    final LineVersion.Config.ForegroundKeepAlive keepAliveCfg = versionConfig.foregroundKeepAlive;
    if (keepAliveCfg.serviceClass.isEmpty()) {
      return;
    }

    XposedHelpers.findAndHookMethod(
        Application.class,
        "onCreate",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (!isEnabled(config)) return;
            if (!(param.thisObject instanceof Application)) return;
            Application application = (Application) param.thisObject;
            appContext = application.getApplicationContext();

            if (lpparam.processName != null && !lpparam.packageName.equals(lpparam.processName)) {
              return;
            }

            scheduleKeepAlive(application, keepAliveCfg);
          }
        });

    XposedHelpers.findAndHookMethod(
        keepAliveCfg.serviceClass,
        lpparam.classLoader,
        "onStartCommand",
        Intent.class,
        int.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (!isEnabled(config)) return;
            Intent intent = (Intent) param.args[0];
            if (intent == null || !KEEP_ALIVE_ACTION.equals(intent.getAction())) {
              return;
            }

            try {
              startKeepAlive((Service) param.thisObject);
              log("LINE foreground keep-alive service is active");
              param.setResult(Service.START_STICKY);
            } catch (Throwable t) {
              XposedBridge.log("Knot: failed to activate LINE foreground keep-alive service: " + t);
              param.setResult(Service.START_NOT_STICKY);
            }
          }
        });

    XposedHelpers.findAndHookMethod(
        keepAliveCfg.serviceClass,
        lpparam.classLoader,
        "onDestroy",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (!isEnabled(config)) return;
            if (appContext == null) return;
            log("LINE foreground keep-alive service destroyed; scheduling restart");
            new Handler(Looper.getMainLooper())
                .postDelayed(() -> requestKeepAlive(appContext, keepAliveCfg), 3000L);
          }
        });

    log("LineForegroundKeepAliveHook installed");
  }
}
