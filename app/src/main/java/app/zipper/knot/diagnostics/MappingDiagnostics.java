package app.zipper.knot.diagnostics;

import app.zipper.knot.Knot;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Reflect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MappingDiagnostics {

  private MappingDiagnostics() {}

  private static final Set<String> NON_CLASS_FIELDS =
      new HashSet<>(
          Arrays.asList("linePkg", "targetPkg", "moduleId", "classAdSdkBase", "classAdMolinBase"));

  private static final class Mapping {
    final String path;
    final String value;

    Mapping(String path, String value) {
      this.path = path;
      this.value = value;
    }
  }

  private static final class Scan {
    int total;
    int resolved;
    final List<Mapping> missing = new ArrayList<>();
  }

  public static void runAndLog(ClassLoader cl) {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cl == null) return;

    Scan scan = scanMappings(cfg, cl);
    Map<String, List<String>> candidates = findCandidates(cl, scan.missing);

    logSummary(scan);
    logStaleMappings(scan.missing, candidates);
    logBrokenHooks();
    Knot.log("==========================================");
  }

  private static Scan scanMappings(LineVersion.Config cfg, ClassLoader cl) {
    Scan scan = new Scan();
    for (Field group : LineVersion.Config.class.getFields()) {
      if (group.getType().getDeclaringClass() != LineVersion.Config.class) continue;
      Object owner = read(group, cfg);
      if (owner == null) continue;

      for (Field f : owner.getClass().getFields()) {
        if (f.getType() != String.class || NON_CLASS_FIELDS.contains(f.getName())) continue;
        String value = (String) read(f, owner);
        if (value == null || value.isEmpty() || value.indexOf('.') < 0) continue;

        scan.total++;
        if (resolves(value, cl)) {
          scan.resolved++;
        } else {
          scan.missing.add(new Mapping(group.getName() + "." + f.getName(), value));
        }
      }
    }
    return scan;
  }

  private static void logSummary(Scan scan) {
    Knot.log("===== Knot diagnostics: LINE " + version() + " =====");
    Knot.log(
        "mappings "
            + scan.resolved
            + "/"
            + scan.total
            + " | hooks "
            + HookLog.installedCount()
            + "/"
            + HookLog.total()
            + " | resolve-fail "
            + HookLog.resolveFailureCount());
  }

  private static void logStaleMappings(
      List<Mapping> missing, Map<String, List<String>> candidates) {
    Knot.log("-- stale mappings (" + missing.size() + ") --");
    for (Mapping m : missing) {
      List<String> found = candidates.get(m.path);
      String line = m.path + " = " + m.value;
      if (found != null) {
        line += found.isEmpty() ? "  => (no match)" : "  => " + String.join(", ", found);
      }
      Knot.log(line);
    }
  }

  private static void logBrokenHooks() {
    Map<String, List<String>> resolveFailures = HookLog.resolveFailuresByHook();
    Map<String, String> installFailures = HookLog.installFailures();

    LinkedHashSet<String> hooks = new LinkedHashSet<>();
    hooks.addAll(resolveFailures.keySet());
    hooks.addAll(installFailures.keySet());

    Knot.log("-- broken hooks (" + hooks.size() + ") --");
    for (String hook : hooks) {
      List<String> targets = resolveFailures.get(hook);
      if (targets != null && !targets.isEmpty()) {
        Knot.log(hook + ": " + String.join(", ", targets));
      } else {
        Knot.log(hook + ": " + installFailures.get(hook));
      }
    }
  }

  private static Map<String, List<String>> findCandidates(ClassLoader cl, List<Mapping> missing) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    Map<String, List<String>> wanted = new HashMap<>();
    for (Mapping m : missing) {
      String simple = simpleName(m.value);
      if (!isMeaningful(simple)) continue;
      result.put(m.path, new ArrayList<>());
      wanted.computeIfAbsent(simple, k -> new ArrayList<>()).add(m.path);
    }
    if (wanted.isEmpty()) return result;

    try {
      Object pathList = Reflect.getObjectField(cl, "pathList");
      for (Object element : (Object[]) Reflect.getObjectField(pathList, "dexElements")) {
        Object dexFile = Reflect.getObjectField(element, "dexFile");
        if (dexFile == null) continue;
        @SuppressWarnings("unchecked")
        Enumeration<String> entries = (Enumeration<String>) Reflect.callMethod(dexFile, "entries");
        while (entries.hasMoreElements()) {
          String fqn = entries.nextElement();
          List<String> paths = wanted.get(simpleName(fqn));
          if (paths == null) continue;
          for (String path : paths) {
            List<String> bucket = result.get(path);
            if (!bucket.contains(fqn)) bucket.add(fqn);
          }
        }
      }
    } catch (Throwable ignored) {
    }
    return result;
  }

  private static boolean resolves(String className, ClassLoader cl) {
    try {
      Class.forName(className, false, cl);
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  private static Object read(Field field, Object owner) {
    try {
      return field.get(owner);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  private static String simpleName(String fqn) {
    int i = Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$'));
    return i >= 0 ? fqn.substring(i + 1) : fqn;
  }

  private static boolean isMeaningful(String name) {
    if (name.length() < 5) return false;
    for (int i = 0; i < name.length(); i++) {
      if (Character.isUpperCase(name.charAt(i))) return true;
    }
    return false;
  }

  private static String version() {
    String v = LineVersion.detectedVersion();
    return v != null ? v : "?";
  }
}
