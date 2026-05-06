package app.zipper.knot.hooks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import app.zipper.knot.KnotConfig;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class OpenInExternalBrowserHook implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (config == null || !config.openUrlInDefaultBrowser.enabled) return;

    try {
      XposedHelpers.findAndHookMethod(
          "jp.naver.line.android.activity.iab.InAppBrowserActivity",
          lpparam.classLoader,
          "onCreate",
          Bundle.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              Activity activity = (Activity) param.thisObject;
              Intent intent = activity.getIntent();
              if (intent == null) return;

              Uri uri = intent.getData();
              if (uri == null) return;

              String url = uri.toString();

              // URLs handled by IAB for functionality
              if (url.startsWith("https://account-center.lylink.yahoo.co.jp")
                  || url.startsWith("https://access.line.me")
                  || url.startsWith(
                      "https://id.lylink.yahoo.co.jp/federation/ly/normal/callback/first")) {
                return;
              }

              Intent externalIntent = new Intent(Intent.ACTION_VIEW, uri);
              externalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              activity.startActivity(externalIntent);

              activity.finish();
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook InAppBrowserActivity: " + t.getMessage());
    }
  }
}
