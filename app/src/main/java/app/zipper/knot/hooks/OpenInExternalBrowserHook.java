package app.zipper.knot.hooks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Reflect;

public class OpenInExternalBrowserHook implements BaseHook {

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    if (config == null || !config.openUrlInDefaultBrowser.enabled) return;

    try {
      Knot.module
          .hook(
              Reflect.findMethodExact(
                  "jp.naver.line.android.activity.iab.InAppBrowserActivity",
                  lpparam.classLoader,
                  "onCreate",
                  Bundle.class))
          .intercept(
              chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                Intent intent = activity.getIntent();
                if (intent == null) return result;

                Uri uri = intent.getData();
                if (uri == null) return result;

                String url = uri.toString();

                // URLs handled by IAB for functionality
                if (url.startsWith("https://account-center.lylink.yahoo.co.jp")
                    || url.startsWith("https://access.line.me")
                    || url.startsWith(
                        "https://id.lylink.yahoo.co.jp/federation/ly/normal/callback/first")) {
                  return result;
                }

                Intent externalIntent = new Intent(Intent.ACTION_VIEW, uri);
                externalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(externalIntent);

                activity.finish();
                return result;
              });
    } catch (Throwable t) {
      Knot.log("Knot: Failed to hook InAppBrowserActivity: " + t.getMessage());
    }
  }
}
