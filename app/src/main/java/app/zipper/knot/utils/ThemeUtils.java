package app.zipper.knot.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import app.zipper.knot.LineVersion;

public class ThemeUtils {

  public static boolean isContextDarkTheme(Context ctx) {
    try {
      LineVersion.Config cfg = LineVersion.get();
      View dummy = LayoutInflater.from(ctx).inflate(cfg.res.layoutCheckbox, null, false);
      TextView title = dummy.findViewById(cfg.res.idTitle);
      if (title != null) {
        int color = title.getCurrentTextColor();

        double luminance =
            0.2126 * Color.red(color) / 255.0
                + 0.7152 * Color.green(color) / 255.0
                + 0.0722 * Color.blue(color) / 255.0;
        return luminance > 0.5;
      }
    } catch (Throwable ignored) {
    }

    int currentNightMode =
        ctx.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
    return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
  }

  public static void applyDarkThemeToView(View view) {
    if (view == null) return;
    if (view instanceof ViewGroup) {
      ViewGroup vg = (ViewGroup) view;
      for (int i = 0; i < vg.getChildCount(); i++) {
        applyDarkThemeToView(vg.getChildAt(i));
      }
    } else if (view instanceof TextView) {
      ((TextView) view).setTextColor(Color.WHITE);
    } else if (view instanceof ImageView) {
      ((ImageView) view).setColorFilter(Color.WHITE);
    }
  }
}
