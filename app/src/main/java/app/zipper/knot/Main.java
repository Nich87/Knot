package app.zipper.knot;

import android.content.Context;
import app.zipper.knot.hooks.*;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main
    implements IXposedHookLoadPackage, IXposedHookInitPackageResources {

  public static final String TAG = "Knot";
  public static final KnotConfig options = new KnotConfig();

  @Override
  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    if (!lpparam.packageName.equals("jp.naver.line.android"))
      return;

    XposedHelpers.findAndHookMethod(
        "android.content.ContextWrapper", lpparam.classLoader,
        "attachBaseContext", Context.class, new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
              throws Throwable {
            Context context = (Context)param.args[0];
            if (context == null)
              return;

            LineVersion.Config cfg = LineVersion.detectWithContext(context);
            if (cfg == null) {
              cfg = LineVersion.detect(lpparam.classLoader);
            }

            if (cfg == null) {
              handleUnsupportedVersion(lpparam, context);
              return;
            }

            initializeModule(context, lpparam);
          }
        });
  }

  private void initializeModule(Context context,
                                XC_LoadPackage.LoadPackageParam lpparam) {
    synchronized (Main.class) {
      if (SettingsStore.isLoaded())
        return;

      SettingsStore.setContext(context);
      SettingsStore.load(options);
      SettingsStore.setLoaded(true);

      XposedBridge.log("Knot: Initializing Knot hooks...");

      BaseHook[] hooks = {new SettingsUIInjector(),
                          new ReadReceiptHandler(),
                          new ReactionNotification(),
                          new UnsendProtector(),
                          new VersionSpoof(),
                          new RemoveAds(),
                          new RemoveHomeContents(),
                          new RemoveTabs(),
                          new PlusMenuHook(),
                          new SettingsButtonLongPress(),
                          new ShowConfigWarning(),
                          new HeaderButtonInjector(),
                          new SafeResourceFix(),
                          new NotificationHook(),
                          new OpenInExternalBrowserHook(),
                          new FontUnlockHook(),
                          new BackupRestoreHook(),
                          new FixNotificationHook(),
                          new ThemeFreeHook(),
                          new StickerTrialHook()};

      for (BaseHook h : hooks) {
        try {
          h.hook(options, lpparam);
        } catch (Throwable t) {
          XposedBridge.log("Knot: Hook failed for " +
                           h.getClass().getSimpleName() + ": " + t);
        }
      }
    }
  }

  private void handleUnsupportedVersion(XC_LoadPackage.LoadPackageParam lpparam,
                                        Context context) {
    final String supported = LineVersion.getSupportedVersions();
    final String msg = ModuleStrings.UNSUPPORTED_VERSION_MSG +
                       " (Supported: " + supported + ")";

    XposedHelpers.findAndHookMethod(
        "jp.naver.line.android.activity.main.MainActivity", lpparam.classLoader,
        "onCreate", android.os.Bundle.class, new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
              throws Throwable {
            android.app.Activity activity =
                (android.app.Activity)param.thisObject;
            new android.app.AlertDialog
                .Builder(activity,
                         android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(ModuleStrings.UNSUPPORTED_VERSION_TITLE)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
          }
        });
  }
  @Override
  public void handleInitPackageResources(
      de.robv.android.xposed.callbacks.XC_InitPackageResources
          .InitPackageResourcesParam resParam) throws Throwable {}
}
