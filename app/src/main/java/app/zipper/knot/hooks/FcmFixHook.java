package app.zipper.knot.hooks;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Queue;

public class FcmFixHook implements BaseHook {

  private static final boolean VERBOSE_LOGGING = false;

  private static boolean isEnabled(KnotConfig config) {
    return config.experimentalFcmFix.enabled;
  }

  private static void logVerbose(String message) {
    if (VERBOSE_LOGGING) {
      XposedBridge.log("Knot: " + message);
    }
  }

  private static Object findSurroundingObject(Object innerObject,
      String outerClassName) {
    if (innerObject == null) {
      return null;
    }

    try {
      return XposedHelpers.getSurroundingThis(innerObject);
    } catch (Throwable ignored) {
    }

    try {
      return XposedHelpers.getObjectField(innerObject, "this$0");
    } catch (Throwable ignored) {
    }

    try {
      for (Field field : innerObject.getClass().getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        Class<?> fieldType = field.getType();
        if (fieldType == null || !outerClassName.equals(fieldType.getName())) {
          continue;
        }
        field.setAccessible(true);
        return field.get(innerObject);
      }
    } catch (Throwable ignored) {
    }

    try {
      return XposedHelpers.getObjectField(innerObject, "a");
    } catch (Throwable ignored) {
    }

    return null;
  }

  private static Object readFieldCandidate(Object target,
      String... fieldNames)
      throws NoSuchFieldException, IllegalAccessException {
    for (String fieldName : fieldNames) {
      try {
        return XposedHelpers.getObjectField(target, fieldName);
      } catch (Throwable ignored) {
      }
    }

    for (String fieldName : fieldNames) {
      try {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
      } catch (Throwable ignored) {
      }
    }

    throw new NoSuchFieldException(target.getClass().getName());
  }

  private static void writeLongFieldCandidate(Object target, long value,
      String... fieldNames)
      throws NoSuchFieldException {
    for (String fieldName : fieldNames) {
      try {
        XposedHelpers.setLongField(target, fieldName, value);
        return;
      } catch (Throwable ignored) {
      }
    }
    throw new NoSuchFieldException(target.getClass().getName());
  }

  private static void writeBooleanFieldCandidate(Object target, boolean value,
      String... fieldNames)
      throws NoSuchFieldException {
    for (String fieldName : fieldNames) {
      try {
        XposedHelpers.setBooleanField(target, fieldName, value);
        return;
      } catch (Throwable ignored) {
      }
    }
    throw new NoSuchFieldException(target.getClass().getName());
  }

  @SuppressWarnings("deprecation")
  private static Object readBundleValue(Bundle bundle, String key) {
    return bundle.get(key);
  }

  private static String describeBundle(Bundle bundle) {
    if (bundle == null || bundle.isEmpty()) {
      return "{}";
    }

    ArrayList<String> keys = new ArrayList<>(bundle.keySet());
    Collections.sort(keys);
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (String key : keys) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(key).append('=');

      Object value = readBundleValue(bundle, key);
      if (value instanceof byte[]) {
        sb.append("byte[").append(((byte[]) value).length).append(']');
        continue;
      }
      if (value instanceof Bundle) {
        sb.append("Bundle");
        continue;
      }

      String text = String.valueOf(value);
      if (text.length() > 120) {
        text = text.substring(0, 120) + "...";
      }
      sb.append(text);
    }
    sb.append('}');
    return sb.toString();
  }

  private static String describeIntent(Intent intent) {
    if (intent == null) {
      return "intent=null";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("action=").append(intent.getAction());
    if (intent.getPackage() != null) {
      sb.append(", pkg=").append(intent.getPackage());
    }

    ComponentName component = intent.getComponent();
    if (component != null) {
      sb.append(", component=").append(component.flattenToShortString());
    }

    sb.append(", flags=0x").append(Integer.toHexString(intent.getFlags()));

    Bundle extras = intent.getExtras();
    if (extras != null && !extras.isEmpty()) {
      sb.append(", extras=").append(describeBundle(extras));
    }
    return sb.toString();
  }

  private static void logIntent(String prefix, Intent intent) {
    if (!VERBOSE_LOGGING) {
      return;
    }
    XposedBridge.log("Knot: " + prefix + " " + describeIntent(intent));
  }

  private static Object resolveLegyStreamingManager(Object observer,
      LineVersion.Config.NotificationFix fixCfg) {
    String outerClassName = fixCfg.legyStreamingLifecycleClass.split("\\$")[0];
    return findSurroundingObject(observer, outerClassName);
  }

  private static void suppressLegyBackgroundDisconnect(Object streamingManager,
      LineVersion.Config.NotificationFix fixCfg, Object backgroundState)
      throws Throwable {
    Object stateField = readFieldCandidate(
        streamingManager, fixCfg.legyStateFieldCandidates);
    XposedHelpers.callMethod(stateField, "setValue", backgroundState);
    writeLongFieldCandidate(streamingManager, Long.MAX_VALUE,
        fixCfg.legyTimeoutFieldCandidates);
    writeBooleanFieldCandidate(
        streamingManager, false,
        fixCfg.legyBackgroundWorkerFlagFieldCandidates);

    Handler handler = (Handler) readFieldCandidate(
        streamingManager, fixCfg.legyHandlerFieldCandidates);
    Runnable closeRunnable = (Runnable) readFieldCandidate(
        streamingManager, fixCfg.legyRunnableFieldCandidates);
    handler.removeCallbacks(closeRunnable);
  }

  private static boolean deliverMessagingIntentDirectly(
      Context context, ClassLoader cl,
      LineVersion.Config.NotificationFix fixCfg, Intent originalIntent) {
    if (context == null || cl == null || originalIntent == null) {
      return false;
    }

    try {
      Context appContext = context.getApplicationContext();
      Class<?> dispatcherClass = XposedHelpers.findClass(fixCfg.firebaseDispatcherClass, cl);
      Object dispatcher = XposedHelpers.callStaticMethod(
          dispatcherClass, fixCfg.firebaseDispatcherAccessorMethod);
      Object queueObj = XposedHelpers.getObjectField(
          dispatcher, fixCfg.firebaseDispatcherQueueField);
      if (!(queueObj instanceof Queue)) {
        XposedBridge.log("Knot: Firebase direct-delivery queue unavailable");
        return false;
      }

      @SuppressWarnings("unchecked")
      Queue<Intent> queue = (Queue<Intent>) queueObj;
      Intent queuedIntent = new Intent(originalIntent);
      if (!queue.offer(queuedIntent)) {
        XposedBridge.log("Knot: Firebase direct-delivery queue rejected intent");
        return false;
      }

      Intent serviceIntent = new Intent("com.google.firebase.MESSAGING_EVENT");
      serviceIntent.setPackage(appContext.getPackageName());
      serviceIntent.setClassName(appContext.getPackageName(),
          fixCfg.lineFcmServiceClass);

      Object component = null;
      try {
        component = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass(fixCfg.firebaseWakefulStartClass, cl),
            fixCfg.firebaseWakefulStartMethod, appContext, serviceIntent);
      } catch (Throwable wakefulFailure) {
        XposedBridge.log(
            "Knot: wakeful Firebase direct-delivery failed, falling back: " +
                wakefulFailure);
        component = appContext.startService(serviceIntent);
      }

      if (component == null) {
        queue.remove(queuedIntent);
        XposedBridge.log(
            "Knot: Firebase direct-delivery startService returned null");
        return false;
      }

      logVerbose("forced direct Firebase service delivery");
      logIntent("forced-direct-intent", originalIntent);
      return true;
    } catch (Throwable t) {
      XposedBridge.log("Knot: Firebase direct-delivery failed: " + t);
      return false;
    }
  }

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    final ClassLoader cl = lpparam.classLoader;
    LineVersion.Config versionConfig = LineVersion.get();
    if (versionConfig == null) {
      return;
    }
    final LineVersion.Config.NotificationFix fixCfg = versionConfig.notificationFix;

    try {
      XposedHelpers.findAndHookMethod(
          fixCfg.lineLifecycleObserverClass, cl,
          fixCfg.lineLifecycleObserverMethod, LifecycleOwner.class,
          Lifecycle.Event.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;

              Lifecycle.Event event = (Lifecycle.Event) param.args[1];
              if (event != Lifecycle.Event.ON_STOP) {
                return;
              }

              logVerbose("suppressing LINE background lifecycle stop");
              param.setResult(null);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      final Class<?> streamingStateClass = XposedHelpers.findClass(fixCfg.legyStreamingStateClass, cl);
      final Object backgroundState = XposedHelpers.getStaticObjectField(
          streamingStateClass, fixCfg.legyBackgroundStateField);

      XposedHelpers.findAndHookMethod(
          fixCfg.legyStreamingLifecycleClass, cl,
          fixCfg.legyStreamingLifecycleMethod,
          XposedHelpers.findClass(fixCfg.legyLifecycleOwnerClass, cl),
          XposedHelpers.findClass(fixCfg.legyLifecycleEventClass, cl),
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;

              Object event = param.args[1];
              if (event == null || !"ON_STOP".equals(event.toString())) {
                return;
              }

              try {
                Object observer = param.thisObject;
                Object streamingManager = resolveLegyStreamingManager(observer, fixCfg);
                if (streamingManager == null) {
                  XposedBridge.log(
                      "Knot: failed to resolve legy streaming outer instance");
                  return;
                }

                suppressLegyBackgroundDisconnect(
                    streamingManager, fixCfg, backgroundState);
                logVerbose(
                    "suppressed legy streaming background disconnect timer");
                param.setResult(null);
              } catch (Throwable t) {
                XposedBridge.log(
                    "Knot: failed to suppress legy background disconnect: " + t);
              }
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          fixCfg.legyDisconnectRunnableClass, cl, "run",
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;
              logVerbose("blocked legy streaming disconnect runnable");
              param.setResult(null);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      Class<?> fcmServiceClass = XposedHelpers.findClass(
          fixCfg.lineFcmServiceClass, cl);
      Class<?> remoteMessageClass = XposedHelpers.findClass(fixCfg.firebaseRemoteMessageClass, cl);

      XposedHelpers.findAndHookMethod(
          fcmServiceClass, fixCfg.lineFcmDispatchMethod, remoteMessageClass,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;
              logVerbose("LINE FCM message dispatch entered");
            }
          });

      for (Method method : fcmServiceClass.getDeclaredMethods()) {
        if (!fixCfg.lineFcmOwnershipMethod.equals(method.getName()) ||
            method.getParameterTypes().length != 2) {
          continue;
        }
        XposedBridge.hookMethod(method, new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
              throws Throwable {
            if (!isEnabled(config))
              return;

            param.setResult(Boolean.TRUE);
            logVerbose("forced LINE FCM ownership validation pass");
          }
        });
        break;
      }

      XposedHelpers.findAndHookMethod(
          fcmServiceClass, fixCfg.lineFcmTokenMethod, String.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;
              logVerbose("LINE received Firebase token refresh: " + param.args[0]);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      Class<?> receiverEnvelopeClass = XposedHelpers.findClass(fixCfg.firebaseReceiverEnvelopeClass, cl);
      XposedHelpers.findAndHookMethod(
          fixCfg.firebaseReceiverClass, cl, fixCfg.firebaseReceiverMethod,
          Context.class, receiverEnvelopeClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;

              Object envelope = param.args[1];
              Intent intent = envelope == null
                  ? null
                  : (Intent) XposedHelpers.getObjectField(
                      envelope, fixCfg.firebaseReceiverIntentField);
              logIntent("FirebaseInstanceIdReceiver received", intent);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          fixCfg.firebaseDispatcherClass, cl, fixCfg.firebaseDispatcherMethod,
          Intent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;

              Intent intent = (Intent) param.args[0];
              logIntent("Firebase dispatcher queued", intent);

              if (intent == null ||
                  !"com.google.android.c2dm.intent.RECEIVE".equals(
                      intent.getAction())) {
                return;
              }

              Context context = (Context) XposedHelpers.getObjectField(
                  param.thisObject, fixCfg.firebaseDispatcherContextField);
              if (!deliverMessagingIntentDirectly(context, cl, fixCfg,
                  intent)) {
                return;
              }

              Object completedTask = XposedHelpers.callStaticMethod(
                  XposedHelpers.findClass(fixCfg.firebaseCompletedTaskClass, cl),
                  fixCfg.firebaseCompletedTaskMethod,
                  Integer.valueOf(-1));
              param.setResult(completedTask);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          fixCfg.firebaseBindDeliveryClass, cl,
          fixCfg.firebaseBindDeliveryMethod, Intent.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;
              logIntent("Firebase bind-delivery queued", (Intent) param.args[0]);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          fixCfg.firebaseMessagingServiceClass, cl,
          fixCfg.firebaseMessagingHandleMethod, Intent.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;
              logIntent("FirebaseMessagingService handling",
                  (Intent) param.args[0]);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          fixCfg.lineFcmServiceBaseClass, cl, "onStartCommand", Intent.class,
          int.class, int.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!isEnabled(config))
                return;
              if (!(param.thisObject instanceof Service))
                return;

              Service service = (Service) param.thisObject;
              if (!fixCfg.lineFcmServiceClass.equals(service.getClass()
                  .getName())) {
                return;
              }

              logIntent("LineFirebaseMessagingService onStartCommand",
                  (Intent) param.args[0]);
            }
          });
    } catch (Throwable ignored) {
    }
  }
}
