package app.zipper.knot.hooks;

import android.app.Notification;
import app.zipper.knot.KnotConfig;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotificationHook implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    XposedHelpers.findAndHookMethod(
        Notification.Builder.class,
        "addAction",
        Notification.Action.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (!config.removeNotificationMuteButton.enabled) return;

            Notification.Action action = (Notification.Action) param.args[0];
            if (action == null || action.title == null) return;

            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            if (app == null) return;

            int resId =
                app.getResources()
                    .getIdentifier("notification_button_mute", "string", app.getPackageName());
            if (resId == 0) return;

            String muteLabel = app.getString(resId);
            if (muteLabel.equals(action.title.toString())) {
              param.setResult(param.thisObject);
            }
          }
        });

    XposedHelpers.findAndHookMethod(
        Notification.Builder.class,
        "addAction",
        int.class,
        CharSequence.class,
        android.app.PendingIntent.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (!config.removeNotificationMuteButton.enabled) return;

            CharSequence titleCs = (CharSequence) param.args[1];
            if (titleCs == null) return;

            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            if (app == null) return;

            int resId =
                app.getResources()
                    .getIdentifier("notification_button_mute", "string", app.getPackageName());
            if (resId == 0) return;

            String muteLabel = app.getString(resId);
            if (muteLabel.equals(titleCs.toString())) {
              param.setResult(param.thisObject);
            }
          }
        });
  }
}
