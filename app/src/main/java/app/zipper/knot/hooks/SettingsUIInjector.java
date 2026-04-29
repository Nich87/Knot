package app.zipper.knot.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.ModuleStrings;
import app.zipper.knot.utils.ThemeUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class SettingsUIInjector implements BaseHook {

  public static volatile Runnable openSettingsAction = null;
  private static volatile SettingsUIInjector instance = null;
  private static volatile Object cachedToggle = null;
  private static volatile Object cachedSuccess = null;

  private static final String BRAND_TAG = "Knot";

  public static void openSettings(android.app.Activity activity) {
    SettingsUIInjector ui = instance;
    if (ui != null)
      ui.displaySettingsDialog(activity);
  }

  private volatile Runnable onSettingsReloadRequest = null;
  private static final int PICK_DIRECTORY_CODE = 0x4C58;
  private static final int PICK_FONT_CODE = 0x4C59;
  private static final int PICK_RESTORE_DB_CODE = 0x4C5A;

  private volatile Object targetAdapter = null;
  private volatile Object targetFragment = null;

  private volatile Dialog settingsDialog = null;
  private volatile boolean pendingRestart = false;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    instance = this;
    LineVersion.Config cfg = LineVersion.get();

    Class<?> fragmentClass = XposedHelpers.findClass(
        cfg.settings.mainSettingsFragmentClass, lpparam.classLoader);
    XposedHelpers.findAndHookMethod(
        fragmentClass, "onViewCreated", View.class, Bundle.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            try {
              LineVersion.Config c = LineVersion.get();
              targetFragment = param.thisObject;
              View listView =
                  ((View)param.args[0]).findViewById(c.res.idSettingList);
              if (listView != null)
                targetAdapter =
                    XposedHelpers.callMethod(listView, "getAdapter");
              openSettingsAction = ()
                  -> displaySettingsDialog((Context)XposedHelpers.callMethod(
                      targetFragment, "requireContext"));
            } catch (Throwable ignored) {
            }
          }
        });

    Class<?> proxyInterface = XposedHelpers.findClass(
        cfg.settings.settingsItemClass, lpparam.classLoader);
    XposedHelpers.findAndHookMethod(
        cfg.settings.settingsAdapterClass, lpparam.classLoader,
        cfg.settings.methodSetItems, Collection.class, new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            if (param.thisObject != targetAdapter)
              return;
            LineVersion.Config c = LineVersion.get();
            List<Object> items = new ArrayList<>((Collection<?>)param.args[0]);
            int insertPos = items.size();
          findPosition:
            for (int i = 0; i < items.size(); i++) {
              try {
                Object model = XposedHelpers.getObjectField(
                    items.get(i), cfg.settings.fieldItemModel);
                if (model == null)
                  continue;
                for (java.lang.reflect.Field f :
                     model.getClass().getDeclaredFields()) {
                  if (f.getType() == int.class) {
                    f.setAccessible(true);
                    if (f.getInt(model) == c.res.idPersonalInfo) {
                      insertPos = i;
                      break findPosition;
                    }
                  }
                }
              } catch (Throwable ignored) {
              }
            }
            Object section = createAdapterItemProxy(
                proxyInterface, lpparam.classLoader, c.res.typeSection);
            Object row = createAdapterItemProxy(
                proxyInterface, lpparam.classLoader, c.res.typeRow);

            if (c.settings.settingsAdapterWrapperClass != null &&
                !c.settings.settingsAdapterWrapperClass.isEmpty()) {
              try {
                Class<?> wrapperCls = XposedHelpers.findClass(
                    c.settings.settingsAdapterWrapperClass,
                    lpparam.classLoader);
                Class<?> headerCls = XposedHelpers.findClass(
                    c.settings.settingsHeaderItemClass, lpparam.classLoader);
                Class<?> itemCls = XposedHelpers.findClass(
                    c.settings.settingsRowItemClass, lpparam.classLoader);

                Class<?> unsafeCls = XposedHelpers.findClass("sun.misc.Unsafe",
                                                             (ClassLoader)null);
                Object unsafe =
                    XposedHelpers.getStaticObjectField(unsafeCls, "theUnsafe");

                Object dummyHeader = XposedHelpers.callMethod(
                    unsafe, "allocateInstance", headerCls);
                Object dummyRow = XposedHelpers.callMethod(
                    unsafe, "allocateInstance", itemCls);

                XposedHelpers.setIntField(
                    dummyHeader, cfg.settings.fieldLayoutId, c.res.typeSection);
                XposedHelpers.setIntField(dummyRow, cfg.settings.fieldLayoutId,
                                          c.res.typeRow);

                section = XposedHelpers.newInstance(wrapperCls, dummyHeader);
                row = XposedHelpers.newInstance(wrapperCls, dummyRow);

                XposedHelpers.setObjectField(
                    dummyHeader, cfg.settings.fieldModelTag, BRAND_TAG);
                XposedHelpers.setObjectField(
                    dummyRow, cfg.settings.fieldModelTag, BRAND_TAG);

                XposedHelpers.setBooleanField(
                    dummyHeader, cfg.settings.fieldIsVisible, true);

                Class<?> bc = XposedHelpers.findClass(
                    c.settings.settingsHandlerBaseClass, lpparam.classLoader);
                Object dummyHandler = XposedHelpers.getStaticObjectField(
                    bc, cfg.settings.fieldDefaultHandler);

                String[] handlerFields = {cfg.settings.fieldActionHandler,
                                          cfg.settings.fieldIconProvider,
                                          cfg.settings.fieldDescriptionProvider,
                                          cfg.settings.fieldSubActionHandler,
                                          cfg.settings.fieldVisibilityFilter};
                for (String f : handlerFields) {
                  try {
                    XposedHelpers.setObjectField(dummyRow, f, dummyHandler);
                    XposedHelpers.setObjectField(dummyHeader, f, dummyHandler);
                  } catch (Throwable ignored) {
                  }
                }

                XposedHelpers.setObjectField(
                    dummyRow, cfg.settings.fieldVisibilityFilter,
                    XposedHelpers.getStaticObjectField(
                        bc, cfg.settings.fieldCommonHandler));
                XposedHelpers.setObjectField(
                    dummyHeader, cfg.settings.fieldVisibilityFilter,
                    XposedHelpers.getStaticObjectField(
                        bc, cfg.settings.fieldCommonHandler));
              } catch (Throwable e) {
                XposedBridge.log("Knot: Adapter wrapper failed: " + e);
              }
            }

            items.add(insertPos, section);
            items.add(insertPos + 1, row);
            param.args[0] = items;
          }
        });

    Class<?> itemBindingClass = XposedHelpers.findClass(
        cfg.settings.settingsBaseAdapterClass, lpparam.classLoader);
    XposedHelpers.findAndHookMethod(
        cfg.settings.settingsSearchHelperClass, lpparam.classLoader,
        cfg.settings.methodBindViewHolder, itemBindingClass, int.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (param.thisObject != targetAdapter)
              return;
            LineVersion.Config c = LineVersion.get();
            int currentPos = (int)param.args[1];
            try {
              Object currentItem = XposedHelpers.callMethod(
                  param.thisObject, c.settings.methodGetItem, currentPos);
              if (currentItem == null)
                return;
              if (currentItem != null &&
                  currentItem.getClass().getName().equals(
                      c.settings.settingsAdapterWrapperClass)) {
                currentItem = XposedHelpers.getObjectField(
                    currentItem, c.settings.fieldItemModel);
              }
              if (currentItem == null)
                return;

              String sourceTag = (String)XposedHelpers.getObjectField(
                  currentItem, c.settings.fieldModelTag);
              if (!BRAND_TAG.equals(sourceTag))
                return;

              int entryType = XposedHelpers.getIntField(
                  currentItem, cfg.settings.fieldLayoutId);
              View itemView = (View)XposedHelpers.getObjectField(
                  param.args[0], c.settings.fieldViewHolderView);
              param.setResult(null);
              if (entryType == c.res.typeSection) {
                if (itemView instanceof TextView)
                  ((TextView)itemView).setText(BRAND_TAG);
              } else if (entryType == c.res.typeRow) {
                applyVisibility(itemView, c.res.idIcon, View.VISIBLE);
                applyVisibility(itemView, c.res.idDesc, View.GONE);
                applyVisibility(itemView, c.res.idMark, View.GONE);
                applyVisibility(itemView, c.res.idSeparator, View.GONE);
                applyVisibility(itemView, c.res.idNewMark, View.GONE);
                applyVisibility(itemView, c.res.idNoticeDot, View.GONE);
                applyVisibility(itemView, c.res.idArrow, View.VISIBLE);

                android.widget.ImageView iconView =
                    itemView.findViewById(c.res.idIcon);
                if (iconView != null) {
                  try {
                    Context modCtx = itemView.getContext().createPackageContext(
                        "app.zipper.knot", Context.CONTEXT_IGNORE_SECURITY);
                    int resId = modCtx.getResources().getIdentifier(
                        "ic_knot", "drawable", "app.zipper.knot");

                    if (resId != 0) {
                      iconView.setImageDrawable(modCtx.getDrawable(resId));
                      iconView.setVisibility(android.view.View.VISIBLE);

                      float density = itemView.getContext()
                                          .getResources()
                                          .getDisplayMetrics()
                                          .density;
                      int size = (int)(24 * density);
                      android.view.ViewGroup.LayoutParams lp =
                          iconView.getLayoutParams();
                      if (lp != null) {
                        lp.width = size;
                        lp.height = size;
                        iconView.setLayoutParams(lp);
                      }
                      iconView.setScaleType(
                          android.widget.ImageView.ScaleType.FIT_CENTER);
                    }
                  } catch (Throwable ignored) {
                  }
                }
                TextView title = itemView.findViewById(c.res.idTitle);
                if (title != null)
                  title.setText(ModuleStrings.SETTINGS_TITLE);
                itemView.setOnClickListener(
                    v -> displaySettingsDialog(v.getContext()));
              }
            } catch (Throwable ignored) {
            }
          }
        });

    XposedHelpers.findAndHookMethod(
        android.app.Activity.class, "onActivityResult", int.class, int.class,
        Intent.class, new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            int requestCode = (int)param.args[0];
            if (requestCode == PICK_DIRECTORY_CODE) {
              param.setResult(null);
              if ((int)param.args[1] != Activity.RESULT_OK ||
                  param.args[2] == null)
                return;
              Uri treeUri = ((Intent)param.args[2]).getData();
              if (treeUri == null)
                return;
              try {
                ((Activity)param.thisObject)
                    .getContentResolver()
                    .takePersistableUriPermission(
                        treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
              } catch (Throwable ignored) {
              }
              SettingsStore.setSettingsDir(treeUri.toString());
              SettingsStore.load(Main.options);
              pendingRestart = true;
              if (onSettingsReloadRequest != null)
                onSettingsReloadRequest.run();
            } else if (requestCode == PICK_FONT_CODE) {
              param.setResult(null);
              if ((int)param.args[1] != Activity.RESULT_OK ||
                  param.args[2] == null)
                return;
              Uri fontUri = ((Intent)param.args[2]).getData();
              if (fontUri == null)
                return;

              try {
                Context ctx = (Context)param.thisObject;
                java.io.InputStream is =
                    ctx.getContentResolver().openInputStream(fontUri);
                File out = new File(ctx.getFilesDir(), "knot_custom_font.ttf");
                java.io.FileOutputStream os = new java.io.FileOutputStream(out);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1)
                  os.write(buffer, 0, len);
                os.close();
                is.close();

                String localPath = out.getAbsolutePath();
                SettingsStore.save("custom_font_path", localPath);
                for (KnotConfig.Item itm : Main.options.items) {
                  if (itm.key.equals("custom_font_path")) {
                    itm.value = localPath;
                    break;
                  }
                }
                pendingRestart = true;
                if (onSettingsReloadRequest != null)
                  onSettingsReloadRequest.run();
              } catch (Throwable t) {
                XposedBridge.log("Knot: Failed to copy font file: " +
                                 t.getMessage());
              }
            } else if (requestCode == PICK_RESTORE_DB_CODE) {
              param.setResult(null);
              if ((int)param.args[1] != Activity.RESULT_OK ||
                  param.args[2] == null)
                return;
              Uri dbUri = ((Intent)param.args[2]).getData();
              if (dbUri == null)
                return;

              Context ctx = (Context)param.thisObject;
              new Thread(() -> {
                File tempFile = null;
                try {
                  tempFile = File.createTempFile("knot_restore_", ".db",
                                                 ctx.getCacheDir());
                  try (java.io.InputStream is =
                           ctx.getContentResolver().openInputStream(dbUri);
                       java.io.FileOutputStream os =
                           new java.io.FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1)
                      os.write(buffer, 0, len);
                  }

                  final File finalFile = tempFile;
                  new Handler(Looper.getMainLooper()).post(() -> {
                    int themeId =
                        ThemeUtils.isContextDarkTheme(ctx)
                            ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                            : android.app.AlertDialog
                                  .THEME_DEVICE_DEFAULT_LIGHT;
                    new AlertDialog.Builder(ctx, themeId)
                        .setTitle(ModuleStrings.RESTORE_CONFIRM_TITLE)
                        .setMessage(ModuleStrings.RESTORE_CONFIRM_MSG)
                        .setPositiveButton(ModuleStrings.SETTINGS_YES,
                                           (d, w) -> {
                                             BackupRestoreHook.runRestore(
                                                 ctx, finalFile);
                                           })
                        .setNegativeButton(ModuleStrings.SETTINGS_CANCEL,
                                           (d, w) -> finalFile.delete())
                        .show();
                  });
                } catch (Throwable t) {
                  XposedBridge.log("Knot: Failed to prepare restore DB: " +
                                   t.getMessage());
                  if (tempFile != null)
                    tempFile.delete();
                }
              }).start();
            }
          }
        });
  }

  private void displaySettingsDialog(Context ctx) {
    if (settingsDialog != null && settingsDialog.isShowing())
      return;
    try {
      Activity host = resolveActivity(ctx);
      if (host == null)
        return;
      cacheUiConstants(host);
      SettingsStore.init(host);
      SettingsStore.load(Main.options);
      pendingRestart = false;

      Dialog dialog =
          new Dialog(host, android.R.style.Theme_DeviceDefault_NoActionBar) {
            @Override
            public void onBackPressed() {
              initiateDialogClosure();
            }
          };
      settingsDialog = dialog;
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

      View content = createSettingsView(host, cachedToggle, cachedSuccess,
                                        dialog.getWindow());

      dialog.setContentView(content);

      Window win = dialog.getWindow();
      if (win != null) {
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.setDimAmount(0);
        win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                      WindowManager.LayoutParams.MATCH_PARENT);
        win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        win.addFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        win.getDecorView().setPadding(0, 0, 0, 0);
        win.getDecorView().requestApplyInsets();
      }

      content.setTranslationX(
          host.getResources().getDisplayMetrics().widthPixels);
      dialog.show();
      content.animate()
          .translationX(0)
          .setDuration(300)
          .setInterpolator(new DecelerateInterpolator())
          .start();
    } catch (Throwable e) {
      XposedBridge.log("Knot: Dialog display failed: " + e.getMessage());
    }
  }

  private void initiateDialogClosure() {
    if (settingsDialog == null || !settingsDialog.isShowing())
      return;

    if (pendingRestart) {
      int themeId = ThemeUtils.isContextDarkTheme(settingsDialog.getContext())
                        ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                        : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
      new AlertDialog.Builder(settingsDialog.getContext(), themeId)
          .setTitle(ModuleStrings.RESTART_TITLE)
          .setMessage(ModuleStrings.RESTART_MESSAGE)
          .setPositiveButton(ModuleStrings.RESTART_OK, (d, w) -> System.exit(0))
          .setNegativeButton(ModuleStrings.RESTART_LATER,
                             (d, w) -> {
                               pendingRestart = false;
                               initiateDialogClosure();
                             })
          .show();
      return;
    }

    LineVersion.Config currentCfg = LineVersion.get();
    View topHeader = settingsDialog.findViewById(currentCfg.res.idHeader);
    if (topHeader == null) {
      settingsDialog.dismiss();
      settingsDialog = null;
      return;
    }
    View rootPane = topHeader.getRootView();
    rootPane.animate()
        .translationX(rootPane.getWidth())
        .setDuration(250)
        .setInterpolator(new DecelerateInterpolator())
        .withEndAction(() -> {
          settingsDialog.dismiss();
          settingsDialog = null;
        })
        .start();
  }

  private View createSettingsView(Activity host, Object toggleType,
                                  Object statusEnum, Window win) {
    try {
      LineVersion.Config currentCfg = LineVersion.get();
      boolean isDark = ThemeUtils.isContextDarkTheme(host);
      LayoutInflater infl = LayoutInflater.from(host);
      ViewGroup hostContainer =
          (ViewGroup)infl.inflate(currentCfg.res.layoutSettingsMain, null);
      hostContainer.setClickable(true);
      hostContainer.setFocusable(true);
      hostContainer.setPadding(0, 0, 0, 0);

      View navHeader = hostContainer.findViewById(currentCfg.res.idHeader);
      if (navHeader != null) {
        try {
          XposedHelpers.callMethod(navHeader,
                                   currentCfg.main.methodRefreshNavHeader, win);
        } catch (Throwable t) {
          if (currentCfg.res.idStatusBarGuide != 0) {
            View guide =
                navHeader.findViewById(currentCfg.res.idStatusBarGuide);
            if (guide != null) {
              int statusBarHeight = 0;
              int resId = host.getResources().getIdentifier("status_bar_height",
                                                            "dimen", "android");
              if (resId > 0)
                statusBarHeight =
                    host.getResources().getDimensionPixelSize(resId);
              if (statusBarHeight > 0) {
                ViewGroup.LayoutParams lp = guide.getLayoutParams();
                lp.height = statusBarHeight;
                guide.setLayoutParams(lp);
              }
            }
          }
        }
        XposedHelpers.callMethod(navHeader,
                                 currentCfg.main.methodHeaderSetTitle,
                                 ModuleStrings.SETTINGS_TITLE);
        try {
          XposedHelpers.callMethod(
              navHeader, currentCfg.main.methodHeaderSetButtonVisibility, true);
        } catch (Throwable ignored) {
        }
        XposedHelpers.callMethod(
            navHeader, currentCfg.main.methodHeaderSetButtonListener,
            (View.OnClickListener)v -> initiateDialogClosure());

        if (isDark) {
          navHeader.setBackgroundColor(Color.parseColor("#111111"));
          ThemeUtils.applyDarkThemeToView(navHeader);
        }
      }

      View itemListView =
          hostContainer.findViewById(currentCfg.res.idSettingList);
      if (itemListView != null) {
        ViewGroup viewParent = (ViewGroup)itemListView.getParent();
        int viewIndex = viewParent.indexOfChild(itemListView);
        ViewGroup.LayoutParams viewLp = itemListView.getLayoutParams();
        viewParent.removeView(itemListView);

        LinearLayout settingsRoot = new LinearLayout(host);
        settingsRoot.setOrientation(LinearLayout.VERTICAL);
        settingsRoot.setLayoutParams(viewLp);

        final FrameLayout itemHost = new FrameLayout(host);
        final ScrollView scroller = new ScrollView(host);

        itemHost.addView(renderSettingsItems(host, toggleType, statusEnum));
        scroller.addView(itemHost);

        setupSearchBox(host, isDark, settingsRoot, itemHost, toggleType,
                       statusEnum);

        settingsRoot.addView(scroller, new LinearLayout.LayoutParams(-1, -1));
        viewParent.addView(settingsRoot, viewIndex, viewLp);

        int bgColor = isDark ? Color.parseColor("#111111") : Color.WHITE;
        hostContainer.setBackgroundColor(bgColor);
        scroller.setBackgroundColor(bgColor);
      }
      return hostContainer;
    } catch (Throwable t) {
      TextView errorLabel = new TextView(host);
      errorLabel.setText("Error: " + t.getMessage());
      return errorLabel;
    }
  }

  private View renderSettingsItems(Context ctx, Object toggleType,
                                   Object statusEnum) {
    LineVersion.Config currentCfg = LineVersion.get();
    LayoutInflater layoutInfl = LayoutInflater.from(ctx);
    LinearLayout mainList = new LinearLayout(ctx);
    mainList.setOrientation(LinearLayout.VERTICAL);
    int bottomOffset =
        (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64,
                                       ctx.getResources().getDisplayMetrics());
    mainList.setPadding(0, 0, 0, bottomOffset);

    boolean isDark = ThemeUtils.isContextDarkTheme(ctx);
    mainList.setBackgroundColor(isDark ? Color.parseColor("#111111")
                                       : Color.WHITE);

    injectSectionHeader(layoutInfl, mainList, ModuleStrings.CAT_STORAGE);
    injectPathSelectorRow(layoutInfl, mainList, ctx,
                          ModuleStrings.DESC_PATH_ROW);
    mainList.getChildAt(mainList.getChildCount() - 1)
        .setTag((ModuleStrings.CAT_STORAGE + " " + ModuleStrings.DESC_PATH_ROW)
                    .toLowerCase());

    KnotConfig activeConfig = Main.options;
    int lastCategoryIndex = -1;
    for (KnotConfig.Item i : activeConfig.items) {
      int currentCatIdx = i.category.ordinal();
      if (currentCatIdx != lastCategoryIndex) {
        lastCategoryIndex = currentCatIdx;
        injectSectionHeader(layoutInfl, mainList,
                            resolveCategoryLabelText(currentCatIdx));
      }
      try {
        final String settingKey = i.key;

        // Temporarily hide this option until a solution is found for
        // notification issues when app is frozen by freezer.
        if (settingKey.equals("fix_notifications"))
          continue;

        View settingRow;

        if (settingKey.equals("custom_font_path")) {
          settingRow =
              layoutInfl.inflate(currentCfg.res.typeRow, mainList, false);
          applyVisibility(settingRow, currentCfg.res.idIcon, View.GONE);
          applyVisibility(settingRow, currentCfg.res.idArrow, View.VISIBLE);

          TextView title = settingRow.findViewById(currentCfg.res.idTitle);
          title.setText(i.label);
          TextView desc = settingRow.findViewById(currentCfg.res.idDesc);
          desc.setText(i.description);
          desc.setVisibility(View.VISIBLE);

          settingRow.setOnClickListener(v -> {
            Activity host = resolveActivity(ctx);
            if (host == null)
              return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] mimeTypes = {"font/ttf", "font/otf",
                                  "application/x-font-ttf",
                                  "application/x-font-otf"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            host.startActivityForResult(intent, PICK_FONT_CODE);
          });
        } else {
          settingRow = layoutInfl.inflate(currentCfg.res.layoutCheckbox,
                                          mainList, false);
          boolean isCurrentlyEnabled = SettingsStore.get(i.key, i.enabled);
          XposedHelpers.callMethod(
              settingRow, currentCfg.settings.methodSetTitleText, i.label);
          XposedHelpers.callMethod(settingRow,
                                   currentCfg.settings.methodSetDescription,
                                   i.description, null, null);
          if (toggleType != null)
            XposedHelpers.callMethod(
                settingRow, currentCfg.settings.methodSetItemType, toggleType);
          if (statusEnum != null)
            XposedHelpers.callMethod(settingRow,
                                     currentCfg.settings.methodSetSyncStatus,
                                     statusEnum);
          XposedHelpers.callMethod(settingRow,
                                   currentCfg.settings.methodSetChecked,
                                   isCurrentlyEnabled);
          XposedHelpers.callMethod(
              settingRow, currentCfg.settings.methodSetDividerVisible, true);

          settingRow.setOnClickListener(v -> {
            boolean newState = !SettingsStore.get(settingKey, i.enabled);
            XposedHelpers.callMethod(v, currentCfg.settings.methodSetChecked,
                                     newState);
            for (KnotConfig.Item itm : Main.options.items)
              if (itm.key.equals(settingKey)) {
                itm.enabled = newState;
                break;
              }
            SettingsStore.save(settingKey, newState);
            pendingRestart = true;
          });
        }
        settingRow.setTag((i.label + " " + i.description).toLowerCase());
        mainList.addView(settingRow);
      } catch (Throwable ignored) {
      }
    }

    injectSectionHeader(layoutInfl, mainList, ModuleStrings.CAT_BACKUP);
    injectBackupRow(layoutInfl, mainList, ctx);
    mainList.getChildAt(mainList.getChildCount() - 1)
        .setTag((ModuleStrings.OPT_BACKUP_LABEL + " " +
                 ModuleStrings.OPT_BACKUP_DESC)
                    .toLowerCase());
    injectRestoreRow(layoutInfl, mainList, ctx);
    mainList.getChildAt(mainList.getChildCount() - 1)
        .setTag((ModuleStrings.OPT_RESTORE_LABEL + " " +
                 ModuleStrings.OPT_RESTORE_DESC)
                    .toLowerCase());

    injectSectionHeader(layoutInfl, mainList, ModuleStrings.CAT_OTHER);
    injectResetRow(layoutInfl, mainList, ctx, activeConfig,
                   ModuleStrings.DESC_RESET_ROW);
    mainList.getChildAt(mainList.getChildCount() - 1)
        .setTag(
            (ModuleStrings.SETTINGS_RESET + " " + ModuleStrings.DESC_RESET_ROW)
                .toLowerCase());
    return mainList;
  }

  private static String resolveCategoryLabelText(int index) {
    try {
      return KnotConfig.Category.values()[index].label;
    } catch (Throwable t) {
      return ModuleStrings.CAT_OTHER;
    }
  }

  private void injectSectionHeader(LayoutInflater infl, LinearLayout parent,
                                   String text) {
    try {
      LineVersion.Config currentCfg = LineVersion.get();
      View hView =
          infl.inflate(currentCfg.res.layoutSectionHeader, parent, false);
      if (hView instanceof TextView)
        ((TextView)hView).setText(text);
      hView.setTag("section_header");
      parent.addView(hView);
    } catch (Throwable ignored) {
    }
  }

  private void filterSettings(View settingsList, String query) {
    if (!(settingsList instanceof ViewGroup))
      return;
    ViewGroup list = (ViewGroup)settingsList;
    boolean isSearching = query.length() > 0;

    int childCount = list.getChildCount();
    View lastHeader = null;
    int itemsInCurrentSection = 0;

    for (int i = 0; i < childCount; i++) {
      View child = list.getChildAt(i);
      Object tag = child.getTag();

      if (tag instanceof String && ((String)tag).equals("section_header")) {
        if (lastHeader != null) {
          lastHeader.setVisibility(itemsInCurrentSection > 0 || !isSearching
                                       ? View.VISIBLE
                                       : View.GONE);
        }
        lastHeader = child;
        itemsInCurrentSection = 0;
        continue;
      }

      if (!isSearching) {
        child.setVisibility(View.VISIBLE);
        continue;
      }

      if (tag instanceof String) {
        String searchable = (String)tag;
        if (searchable.contains(query)) {
          child.setVisibility(View.VISIBLE);
          itemsInCurrentSection++;
        } else {
          child.setVisibility(View.GONE);
        }
      }
    }

    if (lastHeader != null) {
      lastHeader.setVisibility(
          itemsInCurrentSection > 0 || !isSearching ? View.VISIBLE : View.GONE);
    }
  }

  private void injectPathSelectorRow(LayoutInflater infl, LinearLayout parent,
                                     Context ctx, String description) {
    try {
      LineVersion.Config currentCfg = LineVersion.get();
      View pRow = infl.inflate(currentCfg.res.typeRow, parent, false);
      applyVisibility(pRow, currentCfg.res.idIcon, View.GONE);
      applyVisibility(pRow, currentCfg.res.idMark, View.GONE);
      applyVisibility(pRow, currentCfg.res.idSeparator, View.GONE);
      applyVisibility(pRow, currentCfg.res.idNewMark, View.GONE);
      applyVisibility(pRow, currentCfg.res.idNoticeDot, View.GONE);
      applyVisibility(pRow, currentCfg.res.idArrow, View.VISIBLE);

      TextView titleLabel = pRow.findViewById(currentCfg.res.idTitle);
      updateDisplayPathLabel(titleLabel);
      TextView descLabel = pRow.findViewById(currentCfg.res.idDesc);
      if (descLabel != null) {
        descLabel.setText(description);
        descLabel.setVisibility(View.VISIBLE);
      }
      pRow.setOnClickListener(v -> openSystemFolderPicker(ctx));
      parent.addView(pRow);
    } catch (Throwable ignored) {
    }
  }

  private void injectResetRow(LayoutInflater infl, LinearLayout parent,
                              Context ctx, KnotConfig config,
                              String description) {
    try {
      LineVersion.Config currentCfg = LineVersion.get();
      View rRow = infl.inflate(currentCfg.res.typeRow, parent, false);
      applyVisibility(rRow, currentCfg.res.idIcon, View.GONE);
      applyVisibility(rRow, currentCfg.res.idMark, View.GONE);
      applyVisibility(rRow, currentCfg.res.idSeparator, View.GONE);
      applyVisibility(rRow, currentCfg.res.idNewMark, View.GONE);
      applyVisibility(rRow, currentCfg.res.idNoticeDot, View.GONE);
      applyVisibility(rRow, currentCfg.res.idArrow, View.GONE);

      TextView titleLabel = rRow.findViewById(currentCfg.res.idTitle);
      if (titleLabel != null) {
        titleLabel.setText(ModuleStrings.SETTINGS_RESET);
        titleLabel.setTextColor(Color.RED);
      }
      TextView descLabel = rRow.findViewById(currentCfg.res.idDesc);
      if (descLabel != null) {
        descLabel.setText(description);
        descLabel.setVisibility(View.VISIBLE);
      }
      rRow.setOnClickListener(v -> {
        Context activeCtx =
            settingsDialog != null ? settingsDialog.getContext() : ctx;
        int themeId = ThemeUtils.isContextDarkTheme(activeCtx)
                          ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                          : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
        new AlertDialog.Builder(activeCtx, themeId)
            .setTitle(ModuleStrings.SETTINGS_RESET)
            .setMessage(ModuleStrings.SETTINGS_RESET_CONFIRM)
            .setPositiveButton(ModuleStrings.SETTINGS_RESET_OK,
                               (d, w) -> {
                                 SettingsStore.reset();
                                 SettingsStore.load(Main.options);
                                 pendingRestart = true;
                                 if (onSettingsReloadRequest != null)
                                   onSettingsReloadRequest.run();
                               })
            .setNegativeButton(ModuleStrings.SETTINGS_CANCEL, null)
            .show();
      });
      parent.addView(rRow);
    } catch (Throwable ignored) {
    }
  }

  private void injectBackupRow(LayoutInflater infl, LinearLayout parent,
                               Context ctx) {
    try {
      LineVersion.Config currentCfg = LineVersion.get();
      View bRow = infl.inflate(currentCfg.res.typeRow, parent, false);
      applyVisibility(bRow, currentCfg.res.idIcon, View.GONE);
      applyVisibility(bRow, currentCfg.res.idArrow, View.VISIBLE);

      TextView titleLabel = bRow.findViewById(currentCfg.res.idTitle);
      titleLabel.setText(ModuleStrings.OPT_BACKUP_LABEL);
      TextView descLabel = bRow.findViewById(currentCfg.res.idDesc);
      descLabel.setText(ModuleStrings.OPT_BACKUP_DESC);
      descLabel.setVisibility(View.VISIBLE);

      bRow.setOnClickListener(v -> BackupRestoreHook.runBackup(ctx));
      parent.addView(bRow);
    } catch (Throwable ignored) {
    }
  }

  private void injectRestoreRow(LayoutInflater infl, LinearLayout parent,
                                Context ctx) {
    try {
      LineVersion.Config currentCfg = LineVersion.get();
      View rRow = infl.inflate(currentCfg.res.typeRow, parent, false);
      applyVisibility(rRow, currentCfg.res.idIcon, View.GONE);
      applyVisibility(rRow, currentCfg.res.idArrow, View.VISIBLE);

      TextView titleLabel = rRow.findViewById(currentCfg.res.idTitle);
      titleLabel.setText(ModuleStrings.OPT_RESTORE_LABEL);
      TextView descLabel = rRow.findViewById(currentCfg.res.idDesc);
      descLabel.setText(ModuleStrings.OPT_RESTORE_DESC);
      descLabel.setVisibility(View.VISIBLE);

      rRow.setOnClickListener(v -> {
        Activity host = resolveActivity(ctx);
        if (host == null)
          return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "Knot_*.db");

        try {
          String dirUriStr = SettingsStore.getSettingsDirUri();
          if (dirUriStr != null) {
            Uri treeUri = Uri.parse(dirUriStr);
            String treeId =
                android.provider.DocumentsContract.getTreeDocumentId(treeUri);

            androidx.documentfile.provider.DocumentFile root =
                androidx.documentfile.provider.DocumentFile.fromTreeUri(
                    ctx, treeUri);
            androidx.documentfile.provider.DocumentFile backupDir =
                root.findFile("KnotBackup");

            String targetId = treeId;
            if (backupDir != null && backupDir.isDirectory()) {
              targetId =
                  treeId + (treeId.endsWith(":") ? "" : "/") + "KnotBackup";
            }

            Uri initialUri =
                android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, targetId);
            intent.putExtra(
                android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                initialUri);
          }
        } catch (Throwable ignored) {
        }

        host.startActivityForResult(intent, PICK_RESTORE_DB_CODE);
      });
      parent.addView(rRow);
    } catch (Throwable ignored) {
    }
  }

  private static void applyVisibility(View root, int viewId, int state) {
    View v = root.findViewById(viewId);
    if (v != null)
      v.setVisibility(state);
  }

  private void updateDisplayPathLabel(TextView label) {
    if (label == null)
      return;
    String activePath = SettingsStore.getSettingsDir();
    if (activePath == null) {
      label.setText(ModuleStrings.SETTINGS_PATH_PICKER_HINT);
      label.setTextColor(Color.RED);
    } else {
      label.setText(activePath);
      label.setTextColor(Color.parseColor("#1a7a1a"));
    }
  }

  private void openSystemFolderPicker(Context ctx) {
    Activity host = resolveActivity(ctx);
    if (host == null)
      return;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    intent.addFlags(3);
    host.startActivityForResult(intent, PICK_DIRECTORY_CODE);
  }

  private Activity resolveActivity(Context ctx) {
    if (ctx instanceof Activity)
      return (Activity)ctx;
    if (ctx instanceof ContextWrapper)
      return resolveActivity(((ContextWrapper)ctx).getBaseContext());
    return null;
  }

  private static void cacheUiConstants(Context ctx) {
    if (cachedToggle != null && cachedSuccess != null)
      return;
    try {
      LineVersion.Config currentCfg = LineVersion.get();
      LayoutInflater infl = LayoutInflater.from(ctx);
      View view = infl.inflate(currentCfg.res.layoutCheckbox, null, false);
      for (java.lang.reflect.Method m : view.getClass().getMethods()) {
        if (m.getParameterCount() != 1)
          continue;
        Class<?> p = m.getParameterTypes()[0];
        if (!p.isEnum())
          continue;
        if ("setItemType".equals(m.getName())) {
          for (Object c : p.getEnumConstants())
            if ("TOGGLE".equals(c.toString()))
              cachedToggle = c;
        } else if ("setSyncStatus".equals(m.getName())) {
          for (Object c : p.getEnumConstants())
            if ("SUCCESS".equals(c.toString()))
              cachedSuccess = c;
        }
      }
    } catch (Throwable ignored) {
    }
  }

  private static Object createAdapterItemProxy(Class<?> itf, ClassLoader cl,
                                               int type) {
    LineVersion.Config currentCfg = LineVersion.get();
    return Proxy.newProxyInstance(
        cl, new Class[] {itf},
        (proxy, method, args)
            -> currentCfg.settings.methodProxyGetItemType.equals(
                   method.getName())
                   ? type
                   : null);
  }

  private void setupSearchBox(Context ctx, boolean isDark, LinearLayout root,
                              FrameLayout itemHost, Object toggleType,
                              Object statusEnum) {
    EditText searchBox = new EditText(ctx);
    searchBox.setHint(ModuleStrings.SETTINGS_SEARCH_HINT);
    searchBox.setSingleLine(true);
    searchBox.setTextSize(14);

    float density = ctx.getResources().getDisplayMetrics().density;
    int pHorizontal = (int)(16 * density);
    int pVertical = (int)(8 * density);
    searchBox.setPadding(pHorizontal, pVertical, pHorizontal, pVertical);

    GradientDrawable searchBg = new GradientDrawable();
    searchBg.setColor(isDark ? Color.parseColor("#222222")
                             : Color.parseColor("#F5F5F5"));
    searchBg.setCornerRadius(20 * density);
    searchBox.setBackground(searchBg);

    searchBox.setTextColor(isDark ? Color.WHITE : Color.BLACK);
    searchBox.setHintTextColor(isDark ? Color.parseColor("#888888")
                                      : Color.GRAY);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    int margin = (int)(12 * density);
    lp.setMargins(margin, margin / 2, margin, margin / 2);
    searchBox.setLayoutParams(lp);

    root.addView(searchBox, 0);

    searchBox.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
                                    int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before,
                                int count) {
        filterSettings(itemHost.getChildAt(0), s.toString().toLowerCase());
      }
      @Override
      public void afterTextChanged(Editable s) {}
    });

    onSettingsReloadRequest = () -> {
      Activity a = resolveActivity(ctx);
      if (a != null)
        a.runOnUiThread(() -> {
          itemHost.removeAllViews();
          View newList = renderSettingsItems(ctx, toggleType, statusEnum);
          itemHost.addView(newList);
          filterSettings(newList, searchBox.getText().toString().toLowerCase());
        });
    };
  }
}
