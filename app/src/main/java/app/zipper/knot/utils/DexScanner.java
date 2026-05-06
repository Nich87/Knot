package app.zipper.knot.utils;

import de.robv.android.xposed.XposedBridge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@SuppressWarnings("deprecation")
public class DexScanner {

  private static volatile List<String> sAllClasses = null;

  public static List<String> getAllClassNames(String apkPath) {
    if (sAllClasses != null) return sAllClasses;
    synchronized (DexScanner.class) {
      if (sAllClasses != null) return sAllClasses;
      List<String> names = new ArrayList<>(30000);
      try {
        dalvik.system.DexFile dex = new dalvik.system.DexFile(apkPath);
        Enumeration<String> entries = dex.entries();
        while (entries.hasMoreElements()) names.add(entries.nextElement());
        dex.close();
      } catch (Throwable t) {
        XposedBridge.log("Knot: failed to scan " + apkPath + ": " + t);
      }
      sAllClasses = Collections.unmodifiableList(names);
      XposedBridge.log("Knot: scanned " + sAllClasses.size() + " classes");
    }
    return sAllClasses;
  }

  public static Class<?> findClass(
      ClassLoader cl, String apkPath, ClassMatcher matcher, String... pkgPrefixes) {
    for (String name : getAllClassNames(apkPath)) {
      if (pkgPrefixes != null && pkgPrefixes.length > 0) {
        boolean ok = false;
        for (String p : pkgPrefixes) {
          if (name.startsWith(p)) {
            ok = true;
            break;
          }
        }
        if (!ok) continue;
      }
      try {
        Class<?> c = cl.loadClass(name);
        if (matcher.matches(c)) return c;
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  public interface ClassMatcher {
    boolean matches(Class<?> c);
  }
}
