package app.zipper.knot.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HookLog {

  private HookLog() {}

  private static int installedCount;
  private static final Map<String, String> installFailed = new LinkedHashMap<>();
  private static final Map<String, List<String>> resolveFailures = new LinkedHashMap<>();

  private static volatile String currentHook;

  public static void enterHook(String name) {
    currentHook = name;
  }

  public static void exitHook() {
    currentHook = null;
  }

  public static synchronized void recordInstall(String hook, Throwable error) {
    if (error == null) {
      installedCount++;
    } else {
      installFailed.put(hook, error.toString());
    }
  }

  public static synchronized void recordResolveFailure(String target) {
    String hook = currentHook;
    if (hook == null) return;
    List<String> list = resolveFailures.computeIfAbsent(hook, k -> new ArrayList<>());
    if (!list.contains(target)) {
      list.add(target);
    }
  }

  public static synchronized int installedCount() {
    return installedCount;
  }

  public static synchronized int total() {
    return installedCount + installFailed.size();
  }

  public static synchronized Map<String, String> installFailures() {
    return new LinkedHashMap<>(installFailed);
  }

  public static synchronized Map<String, List<String>> resolveFailuresByHook() {
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> e : resolveFailures.entrySet()) {
      copy.put(e.getKey(), new ArrayList<>(e.getValue()));
    }
    return copy;
  }

  public static synchronized int resolveFailureCount() {
    int n = 0;
    for (List<String> v : resolveFailures.values()) {
      n += v.size();
    }
    return n;
  }
}
