package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LoadParam;

public interface BaseHook {
  void hook(KnotConfig config, LoadParam lpparam) throws Throwable;
}
