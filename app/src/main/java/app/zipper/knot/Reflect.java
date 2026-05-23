package app.zipper.knot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class Reflect {

  private Reflect() {}

  private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = new HashMap<>();

  static {
    PRIMITIVE_TO_WRAPPER.put(boolean.class, Boolean.class);
    PRIMITIVE_TO_WRAPPER.put(byte.class, Byte.class);
    PRIMITIVE_TO_WRAPPER.put(char.class, Character.class);
    PRIMITIVE_TO_WRAPPER.put(short.class, Short.class);
    PRIMITIVE_TO_WRAPPER.put(int.class, Integer.class);
    PRIMITIVE_TO_WRAPPER.put(long.class, Long.class);
    PRIMITIVE_TO_WRAPPER.put(float.class, Float.class);
    PRIMITIVE_TO_WRAPPER.put(double.class, Double.class);
  }

  public static Class<?> findClass(String className, ClassLoader cl) {
    try {
      return Class.forName(className, false, cl);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Class not found: " + className, e);
    }
  }

  private static Class<?> toClass(Object spec, ClassLoader cl) {
    if (spec instanceof Class) return (Class<?>) spec;
    if (spec instanceof String) return findClass((String) spec, cl);
    throw new IllegalArgumentException("Parameter type must be Class or String, got: " + spec);
  }

  private static Class<?>[] resolveTypes(Object[] specs, ClassLoader cl) {
    Class<?>[] types = new Class<?>[specs.length];
    for (int i = 0; i < specs.length; i++) {
      types[i] = toClass(specs[i], cl);
    }
    return types;
  }

  public static Method findMethodExact(Class<?> clazz, String name, Object... paramTypeSpecs) {
    Class<?>[] types = resolveTypes(paramTypeSpecs, clazz.getClassLoader());
    Class<?> c = clazz;
    while (c != null) {
      try {
        Method m = c.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
      } catch (NoSuchMethodException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchMethodError(clazz.getName() + "#" + name + describe(types));
  }

  public static Method findMethodExact(
      String className, ClassLoader cl, String name, Object... paramTypeSpecs) {
    return findMethodExact(findClass(className, cl), name, paramTypeSpecs);
  }

  public static Constructor<?> findConstructorExact(Class<?> clazz, Object... paramTypeSpecs) {
    Class<?>[] types = resolveTypes(paramTypeSpecs, clazz.getClassLoader());
    try {
      Constructor<?> ctor = clazz.getDeclaredConstructor(types);
      ctor.setAccessible(true);
      return ctor;
    } catch (NoSuchMethodException e) {
      throw new NoSuchMethodError(clazz.getName() + ".<init>" + describe(types));
    }
  }

  public static Constructor<?> findConstructorExact(
      String className, ClassLoader cl, Object... paramTypeSpecs) {
    return findConstructorExact(findClass(className, cl), paramTypeSpecs);
  }

  public static Object callMethod(Object obj, String name, Object... args) {
    Method m = resolveBestMethod(obj.getClass(), name, args);
    try {
      return m.invoke(obj, args);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  public static Object callStaticMethod(Class<?> clazz, String name, Object... args) {
    Method m = resolveBestMethod(clazz, name, args);
    try {
      return m.invoke(null, args);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  public static Object newInstance(Class<?> clazz, Object... args) {
    Constructor<?> best = null;
    for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
      if (parametersMatch(ctor.getParameterTypes(), args)
          && (best == null || isMoreSpecific(ctor.getParameterTypes(), best.getParameterTypes()))) {
        best = ctor;
      }
    }
    if (best == null) {
      throw new NoSuchMethodError(clazz.getName() + ".<init> (no matching constructor)");
    }
    best.setAccessible(true);
    try {
      return best.newInstance(args);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static Method resolveBestMethod(Class<?> clazz, String name, Object[] args) {
    Method best = null;
    Class<?> c = clazz;
    while (c != null) {
      for (Method m : c.getDeclaredMethods()) {
        if (!m.getName().equals(name)) continue;
        if (!parametersMatch(m.getParameterTypes(), args)) continue;
        if (best == null || isMoreSpecific(m.getParameterTypes(), best.getParameterTypes())) {
          best = m;
        }
      }
      c = c.getSuperclass();
    }
    if (best == null) {
      for (Class<?> iface : allInterfaces(clazz)) {
        for (Method m : iface.getDeclaredMethods()) {
          if (!m.getName().equals(name)) continue;
          if (!parametersMatch(m.getParameterTypes(), args)) continue;
          if (best == null || isMoreSpecific(m.getParameterTypes(), best.getParameterTypes())) {
            best = m;
          }
        }
      }
    }
    if (best == null) {
      throw new NoSuchMethodError(clazz.getName() + "#" + name + " (no matching overload)");
    }
    best.setAccessible(true);
    return best;
  }

  private static Field findField(Class<?> clazz, String name) {
    Class<?> c = clazz;
    while (c != null) {
      try {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldError(clazz.getName() + "#" + name);
  }

  public static Object getObjectField(Object obj, String name) {
    try {
      return findField(obj.getClass(), name).get(obj);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setObjectField(Object obj, String name, Object value) {
    try {
      findField(obj.getClass(), name).set(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static int getIntField(Object obj, String name) {
    try {
      return findField(obj.getClass(), name).getInt(obj);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setIntField(Object obj, String name, int value) {
    try {
      findField(obj.getClass(), name).setInt(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static long getLongField(Object obj, String name) {
    try {
      return findField(obj.getClass(), name).getLong(obj);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setLongField(Object obj, String name, long value) {
    try {
      findField(obj.getClass(), name).setLong(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setBooleanField(Object obj, String name, boolean value) {
    try {
      findField(obj.getClass(), name).setBoolean(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object getStaticObjectField(Class<?> clazz, String name) {
    try {
      return findField(clazz, name).get(null);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setStaticLongField(Class<?> clazz, String name, long value) {
    try {
      findField(clazz, name).setLong(null, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object getSurroundingThis(Object obj) {
    return getObjectField(obj, "this$0");
  }

  private static boolean parametersMatch(Class<?>[] params, Object[] args) {
    if (params.length != args.length) return false;
    for (int i = 0; i < params.length; i++) {
      Class<?> p = params[i];
      Object a = args[i];
      if (a == null) {
        if (p.isPrimitive()) return false;
      } else if (!isCompatible(p, a.getClass())) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCompatible(Class<?> param, Class<?> argType) {
    if (param.isPrimitive()) {
      param = PRIMITIVE_TO_WRAPPER.get(param);
      if (param == null) return false;
    }
    if (param.isAssignableFrom(argType)) return true;
    return isWideningNumeric(param, argType);
  }

  private static boolean isWideningNumeric(Class<?> param, Class<?> argType) {
    if (!Number.class.isAssignableFrom(param) || !Number.class.isAssignableFrom(argType)) {
      return false;
    }
    int p = numericRank(param);
    int a = numericRank(argType);
    return p >= 0 && a >= 0 && p >= a;
  }

  private static int numericRank(Class<?> c) {
    if (c == Byte.class) return 0;
    if (c == Short.class) return 1;
    if (c == Integer.class) return 2;
    if (c == Long.class) return 3;
    if (c == Float.class) return 4;
    if (c == Double.class) return 5;
    return -1;
  }

  private static boolean isMoreSpecific(Class<?>[] candidate, Class<?>[] current) {
    for (int i = 0; i < candidate.length; i++) {
      Class<?> cand = box(candidate[i]);
      Class<?> cur = box(current[i]);
      if (cand != cur && cur.isAssignableFrom(cand)) return true;
    }
    return false;
  }

  private static Class<?> box(Class<?> c) {
    Class<?> w = PRIMITIVE_TO_WRAPPER.get(c);
    return w != null ? w : c;
  }

  private static java.util.List<Class<?>> allInterfaces(Class<?> clazz) {
    java.util.List<Class<?>> result = new java.util.ArrayList<>();
    Class<?> c = clazz;
    while (c != null) {
      collectInterfaces(c, result);
      c = c.getSuperclass();
    }
    return result;
  }

  private static void collectInterfaces(Class<?> c, java.util.List<Class<?>> out) {
    for (Class<?> i : c.getInterfaces()) {
      if (!out.contains(i)) {
        out.add(i);
        collectInterfaces(i, out);
      }
    }
  }

  private static RuntimeException rethrow(Throwable t) {
    if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
      t = t.getCause();
    }
    if (t instanceof RuntimeException) return (RuntimeException) t;
    return new RuntimeException(t);
  }

  private static String describe(Class<?>[] types) {
    StringBuilder sb = new StringBuilder("(");
    for (int i = 0; i < types.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append(types[i].getName());
    }
    return sb.append(")").toString();
  }
}
