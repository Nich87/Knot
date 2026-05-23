package app.zipper.knot.hooks;

import android.app.Notification;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;

public class NotificationHook implements BaseHook {

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    Knot.module
        .hook(
            Reflect.findMethodExact(
                Notification.Builder.class, "addAction", Notification.Action.class))
        .intercept(
            chain -> {
              if (!config.removeNotificationMuteButton.enabled) return chain.proceed();

              Notification.Action action = (Notification.Action) chain.getArg(0);
              if (action == null || action.title == null) return chain.proceed();

              android.app.Application app = Knot.currentApplication();
              if (app == null) return chain.proceed();

              int resId =
                  app.getResources()
                      .getIdentifier("notification_button_mute", "string", app.getPackageName());
              if (resId == 0) return chain.proceed();

              String muteLabel = app.getString(resId);
              if (muteLabel.equals(action.title.toString())) {
                return chain.getThisObject();
              }
              return chain.proceed();
            });

    Knot.module
        .hook(
            Reflect.findMethodExact(
                Notification.Builder.class,
                "addAction",
                int.class,
                CharSequence.class,
                android.app.PendingIntent.class))
        .intercept(
            chain -> {
              if (!config.removeNotificationMuteButton.enabled) return chain.proceed();

              CharSequence titleCs = (CharSequence) chain.getArg(1);
              if (titleCs == null) return chain.proceed();

              android.app.Application app = Knot.currentApplication();
              if (app == null) return chain.proceed();

              int resId =
                  app.getResources()
                      .getIdentifier("notification_button_mute", "string", app.getPackageName());
              if (resId == 0) return chain.proceed();

              String muteLabel = app.getString(resId);
              if (muteLabel.equals(titleCs.toString())) {
                return chain.getThisObject();
              }
              return chain.proceed();
            });
  }
}
