package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SearchMin1CharHook implements BaseHook {

  private static boolean hasMeaningfulKeyword(String str) {
    for (int i = 0; i < str.length(); ) {
      int codePoint = str.codePointAt(i);
      i += Character.charCount(codePoint);

      if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) continue;
      if (Character.isISOControl(codePoint)) continue;
      if (Character.getType(codePoint) == Character.FORMAT) continue;
      return true;
    }
    return false;
  }

  @Override
  public void hook(KnotConfig options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (!options.searchMin1Char.enabled) return;

    LineVersion.Config config = LineVersion.get();
    if (config == null
        || config.chat.searchKeywordTypeClass.isEmpty()
        || config.chat.searchKeywordTypeMethod.isEmpty()) return;

    try {
      XposedHelpers.findAndHookMethod(
          config.chat.searchKeywordTypeClass,
          lpparam.classLoader,
          config.chat.searchKeywordTypeMethod,
          String.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              String str = (String) param.args[0];
              if (str == null) {
                param.setResult(false);
                return;
              }
              param.setResult(hasMeaningfulKeyword(str));
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: SearchMin1CharHook error: " + t);
    }
  }
}
