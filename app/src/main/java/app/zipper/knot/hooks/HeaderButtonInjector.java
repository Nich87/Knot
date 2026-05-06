package app.zipper.knot.hooks;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HeaderButtonInjector implements BaseHook {

  @Override
  public void hook(KnotConfig options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config config = LineVersion.get();
    if (config == null || config.chat.headerController.isEmpty()) return;

    try {
      Class<?> headerControllerClass =
          XposedHelpers.findClass(config.chat.headerController, lpparam.classLoader);
      Class<?> headerHelperClass =
          XposedHelpers.findClass(config.chat.headerHelper, lpparam.classLoader);
      Class<?> headerButtonTypeEnum =
          XposedHelpers.findClass(config.main.headerButtonTypeClass, lpparam.classLoader);
      final Object slotFarLeft =
          XposedHelpers.getStaticObjectField(headerButtonTypeEnum, config.main.slotFarLeft);

      XposedHelpers.findAndHookConstructor(
          headerControllerClass,
          config.chatHeader.chatHistoryActivity,
          config.chatHeader.chatHistoryActivity,
          Window.class,
          View.class,
          config.chatHeader.fieldChatConfigChatId,
          config.chatHeader.fieldChatConfigIsMuted,
          config.chatHeader.fieldChatConfigCategory,
          config.chatHeader.fieldChatConfigType,
          headerHelperClass,
          config.chatHeader.fieldAppInfoVersion,
          config.chatHeader.fieldAppInfoPkg,
          config.chatHeader.fieldAppInfoId,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
              if (app.zipper.knot.SettingsStore.get("record_read_history", false)) {
                injectButton(param.thisObject, slotFarLeft, config);
              }
            }
          });

      XposedHelpers.findAndHookMethod(
          headerControllerClass,
          config.main.methodSetHeaderButton,
          headerButtonTypeEnum,
          config.main.headerInterfaceA,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
              if (slotFarLeft.equals(param.args[0])) {
                if (app.zipper.knot.SettingsStore.get("record_read_history", false)) {
                  param.setResult(null);
                }
              }
            }
          });

    } catch (Throwable t) {
      XposedBridge.log("Knot: init error: " + t.getMessage());
    }
  }

  private void injectButton(Object controller, Object slot, LineVersion.Config config) {
    try {
      Object headerHelper = XposedHelpers.getObjectField(controller, config.main.fieldHeaderHelper);
      if (headerHelper == null) return;

      final Context context =
          (Context) XposedHelpers.getObjectField(controller, config.main.fieldChatActivity);
      if (context == null) return;

      Drawable icon = null;
      try {
        Context modCtx =
            context.createPackageContext("app.zipper.knot", Context.CONTEXT_IGNORE_SECURITY);
        int iconId =
            modCtx.getResources().getIdentifier("ic_book", "drawable", modCtx.getPackageName());
        if (iconId != 0) {
          icon = modCtx.getDrawable(iconId);
          if (icon != null) {

            int size = (int) (24 * context.getResources().getDisplayMetrics().density);
            android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            icon.setBounds(0, 0, size, size);
            icon.draw(canvas);
            icon = new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
          }
        }
      } catch (Throwable t) {
        XposedBridge.log("Knot: icon load error: " + t.getMessage());
      }

      if (icon == null) return;

      // Use stable setButtonImageViewDrawable API to avoid per-version config
      Object headerButton =
          XposedHelpers.callMethod(headerHelper, config.main.methodGetHeaderButtonView, slot);
      if (headerButton == null) return;
      XposedHelpers.callMethod(headerButton, "setButtonImageViewDrawable", icon);

      XposedHelpers.callMethod(
          headerHelper, config.main.methodSetHeaderLabel,
          slot, ModuleStrings.READ_RECEIPT_VIEWER);

      XposedHelpers.callMethod(headerHelper, config.main.methodSetHeaderButtonVisibility, slot, 0);

      try {
        if (headerButton instanceof LinearLayout) {
          LinearLayout layout = (LinearLayout) headerButton;
          layout.setGravity(android.view.Gravity.CENTER);
          int padding = (int) (7 * context.getResources().getDisplayMetrics().density);
          layout.setPadding(padding, 0, padding, 0);

          android.view.ViewGroup.LayoutParams lp = layout.getLayoutParams();
          if (lp != null) {
            lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            layout.setLayoutParams(lp);
          }
        }
      } catch (Throwable t) {
        XposedBridge.log("Knot: layout error: " + t.getMessage());
      }

      XposedHelpers.callMethod(
          headerHelper,
          config.main.methodSetHeaderOnClickListener,
          slot,
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              try {
                XposedBridge.log("Knot: Clicked!");
                Activity activity =
                    (Activity)
                        XposedHelpers.getObjectField(controller, config.main.fieldChatActivity);
                if (activity == null) return;

                String chatId = activity.getIntent().getStringExtra("chatId");
                if (chatId == null || chatId.isEmpty()) {
                  chatId = activity.getIntent().getStringExtra("chat_id");
                }

                if (chatId == null || chatId.isEmpty()) {
                  Object request = XposedHelpers.getObjectField(activity, config.chat.chatIdField);
                  if (request != null) {
                    chatId =
                        (String) XposedHelpers.callMethod(request, config.chat.methodGetChatId);
                  }
                }

                if (chatId != null && !chatId.isEmpty()) {
                  app.zipper.knot.ui.ReadHistoryViewer.show(activity, chatId);
                } else {
                  android.widget.Toast.makeText(
                          activity, "ChatId not found", android.widget.Toast.LENGTH_SHORT)
                      .show();
                }
              } catch (Throwable t) {
                XposedBridge.log("Knot: click error: " + t.toString());
              }
            }
          });

    } catch (Throwable t) {
      XposedBridge.log("Knot: injection error: " + t.getMessage());
    }
  }
}
