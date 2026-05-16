package app.zipper.knot;

import android.content.Context;
import app.zipper.knot.hooks.*;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main implements IXposedHookLoadPackage, IXposedHookInitPackageResources {

  public static final String TAG = "Knot";
  public static final KnotConfig options = new KnotConfig();

  @Override
  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!lpparam.packageName.equals("jp.naver.line.android")) return;

    XposedHelpers.findAndHookMethod(
        "android.content.ContextWrapper",
        lpparam.classLoader,
        "attachBaseContext",
        Context.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Context context = (Context) param.args[0];
            if (context == null) return;

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

  private void initializeModule(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
    synchronized (Main.class) {
      if (SettingsStore.isLoaded()) return;

      SettingsStore.setContext(context);
      SettingsStore.load(options);
      SettingsStore.setLoaded(true);

      XposedBridge.log("Knot: Initializing Knot hooks...");

      applyHook(new SettingsUIInjector(), lpparam);
      applyHook(new SettingsButtonLongPress(), lpparam);
      applyHook(new ShowConfigWarning(), lpparam);
      applyHook(new HomeSettingsTooltip(), lpparam);
      applyHook(new SafeResourceFix(), lpparam);

      if (options.recordReadHistory.enabled || options.preventMarkAsRead.enabled) {
        applyHook(new ReadReceiptHandler(), lpparam);
        applyHook(new PlusMenuHook(), lpparam);
      }
      if (options.recordReadHistory.enabled) {
        applyHook(new HeaderButtonInjector(), lpparam);
      }
      if (options.preventUnsendMessage.enabled) {
        applyHook(new UnsendProtector(), lpparam);
      }
      if (options.hideAiIconPermanently.enabled) {
        applyHook(new RemoveTalkRoomAgentIToggle(), lpparam);
        applyHook(new HideAiIconPermanently(), lpparam);
      }
      if (options.openUrlInDefaultBrowser.enabled) {
        applyHook(new OpenInExternalBrowserHook(), lpparam);
      }
      if (options.highQualityPhoto.enabled) {
        applyHook(new ImageQuality(), lpparam);
      }
      if (options.longVideo.enabled) {
        applyHook(new LongVideoHook(), lpparam);
      }
      if (options.searchByMember.enabled) {
        applyHook(new SearchByMemberHook(), lpparam);
      }
      if (options.searchMin1Char.enabled) {
        applyHook(new SearchMin1CharHook(), lpparam);
      }
      if (options.fixAnnouncementName.enabled) {
        applyHook(new AnnouncementNameFix(), lpparam);
      }

      if (options.removeAds.enabled) {
        applyHook(new RemoveAds(), lpparam);
      }
      if (options.removeHomeRecommendations.enabled
          || options.removeHomeServices.enabled
          || options.removeHomeAccordion.enabled) {
        applyHook(new RemoveHomeContents(), lpparam);
      }
      if (options.removeTabVoom.enabled
          || options.removeTabNews.enabled
          || options.removeTabMini.enabled
          || options.hideTabText.enabled
          || options.extendTabClickArea.enabled) {
        applyHook(new RemoveTabs(), lpparam);
      }
      if (options.removeAiFriendsButton.enabled || options.removeOpenChatButton.enabled) {
        applyHook(new RemoveHeaderButtons(), lpparam);
      }
      if (options.useCustomFont.enabled) {
        applyHook(new FontUnlockHook(), lpparam);
      }

      if (options.reactionNotification.enabled) {
        applyHook(new ReactionNotification(), lpparam);
      }
      if (options.removeNotificationMuteButton.enabled) {
        applyHook(new NotificationHook(), lpparam);
      }
      if (options.experimentalFcmFix.enabled) {
        applyHook(new FcmFixHook(), lpparam);
      }
      if (options.spoofVersion.enabled || options.spoofVersionUnsendOnly.enabled) {
        applyHook(new VersionSpoof(), lpparam);
      }
    }
  }

  private void applyHook(BaseHook hook, XC_LoadPackage.LoadPackageParam lpparam) {
    try {
      hook.hook(options, lpparam);
    } catch (Throwable t) {
      XposedBridge.log("Knot: Hook failed for " + hook.getClass().getSimpleName() + ": " + t);
    }
  }

  private void handleUnsupportedVersion(XC_LoadPackage.LoadPackageParam lpparam, Context context) {
    final String supported = LineVersion.getSupportedVersions();
    final String msg = ModuleStrings.UNSUPPORTED_VERSION_MSG + " (Supported: " + supported + ")";

    XposedHelpers.findAndHookMethod(
        "jp.naver.line.android.activity.main.MainActivity",
        lpparam.classLoader,
        "onCreate",
        android.os.Bundle.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            android.app.Activity activity = (android.app.Activity) param.thisObject;
            new android.app.AlertDialog.Builder(
                    activity, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(ModuleStrings.UNSUPPORTED_VERSION_TITLE)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
          }
        });
  }

  @Override
  public void handleInitPackageResources(
      de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam resParam)
      throws Throwable {}
}
