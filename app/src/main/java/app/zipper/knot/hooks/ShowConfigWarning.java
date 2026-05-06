package app.zipper.knot.hooks;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ShowConfigWarning implements BaseHook {

  private static final int WARNING_BANNER_TAG = 0x4C585F57;
  private static final int BROWSE_DIR_REQUEST = 0x4C5859;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    Class<?> activityCls =
        lpparam.classLoader.loadClass("jp.naver.line.android.activity.main.MainActivity");

    XposedHelpers.findAndHookMethod(
        activityCls,
        "onResume",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            Activity host = (Activity) param.thisObject;
            SettingsStore.init(host);
            for (KnotConfig.Item item : config.items) {
              item.enabled = SettingsStore.get(item.key, item.enabled);
            }

            ViewGroup layoutRoot = host.findViewById(android.R.id.content);
            if (layoutRoot == null) return;

            if (SettingsStore.isConfigured()) {
              dismissBanner(layoutRoot);
            } else {
              if (layoutRoot.findViewWithTag(WARNING_BANNER_TAG) == null) {
                layoutRoot.addView(constructWarningBanner(host));
              }
            }
          }
        });

    XposedHelpers.findAndHookMethod(
        activityCls,
        "onActivityResult",
        int.class,
        int.class,
        Intent.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            int code = (int) param.args[0];
            int result = (int) param.args[1];
            Intent data = (Intent) param.args[2];

            if (code != BROWSE_DIR_REQUEST) return;
            param.setResult(null);

            if (result != Activity.RESULT_OK || data == null) return;
            Uri treeUri = data.getData();
            if (treeUri == null) return;

            Activity host = (Activity) param.thisObject;
            try {
              host.getContentResolver()
                  .takePersistableUriPermission(
                      treeUri,
                      Intent.FLAG_GRANT_READ_URI_PERMISSION
                          | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
            SettingsStore.setSettingsDir(treeUri.toString());

            host.runOnUiThread(
                () -> {
                  ViewGroup root = host.findViewById(android.R.id.content);
                  if (root != null) dismissBanner(root);
                });
          }
        });
  }

  private static void dismissBanner(ViewGroup root) {
    View banner = root.findViewWithTag(WARNING_BANNER_TAG);
    if (banner != null) root.removeView(banner);
  }

  private View constructWarningBanner(Activity host) {
    TextView label = new TextView(host);
    label.setTag(WARNING_BANNER_TAG);
    label.setText(app.zipper.knot.utils.ModuleStrings.WARN_STORAGE_UNSET);
    label.setTextColor(Color.WHITE);
    label.setTextSize(12);
    label.setTypeface(null, Typeface.BOLD);
    label.setBackgroundColor(Color.parseColor("#CC333333"));
    label.setGravity(Gravity.CENTER);

    float scale = host.getResources().getDisplayMetrics().density;
    int padding = (int) (10 * scale);
    label.setPadding(padding, padding, padding, padding);
    label.setClickable(true);
    label.setOnClickListener(
        v -> {
          Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
          intent.addFlags(
              Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
          host.startActivityForResult(intent, BROWSE_DIR_REQUEST);
        });

    int statusH = 0, actionH = 0;
    try {
      int id = host.getResources().getIdentifier("status_bar_height", "dimen", "android");
      if (id > 0) statusH = host.getResources().getDimensionPixelSize(id);
    } catch (Throwable ignored) {
    }

    try {
      android.util.TypedValue val = new android.util.TypedValue();
      if (host.getTheme().resolveAttribute(android.R.attr.actionBarSize, val, true)) {
        actionH =
            android.util.TypedValue.complexToDimensionPixelSize(
                val.data, host.getResources().getDisplayMetrics());
      }
    } catch (Throwable ignored) {
    }

    FrameLayout.LayoutParams params =
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP);
    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
    params.topMargin = statusH + actionH;
    label.setLayoutParams(params);
    return label;
  }
}
