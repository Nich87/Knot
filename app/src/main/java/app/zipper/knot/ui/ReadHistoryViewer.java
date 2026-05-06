package app.zipper.knot.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XposedBridge;
import org.json.JSONObject;

public class ReadHistoryViewer {

  public static void show(Activity activity, String targetChatId) {
    try {
      JSONObject historyJson = SettingsStore.loadReadHistory();
      JSONObject chats = historyJson.optJSONObject("c");

      String chatName = app.zipper.knot.utils.LineDBUtils.resolveChatName(targetChatId);

      ScrollView scrollView = new ScrollView(activity);
      LinearLayout container = new LinearLayout(activity);
      container.setOrientation(LinearLayout.VERTICAL);
      container.setPadding(40, 20, 40, 40);
      scrollView.addView(container);

      boolean isDark = app.zipper.knot.utils.ThemeUtils.isContextDarkTheme(activity);

      TextView header = new TextView(activity);
      header.setText(
          chatName != null ? chatName : app.zipper.knot.utils.ModuleStrings.READ_HISTORY_TITLE);
      header.setTextSize(18);
      header.setTextColor(isDark ? Color.WHITE : Color.BLACK);
      header.setPadding(0, 20, 0, 30);
      container.addView(header);

      boolean found = false;
      if (chats != null) {
        if (targetChatId != null) {
          JSONObject chat = chats.optJSONObject(targetChatId);
          if (chat != null) {
            JSONObject messages = chat.optJSONObject("m");
            if (messages != null) {
              found = true;
              renderMessages(activity, container, messages, isDark);
            }
          }
        } else {
          java.util.Iterator<String> chatKeys = chats.keys();
          while (chatKeys.hasNext()) {
            JSONObject chat = chats.optJSONObject(chatKeys.next());
            if (chat != null) {
              JSONObject messages = chat.optJSONObject("m");
              if (messages != null) {
                found = true;
                renderMessages(activity, container, messages, isDark);
              }
            }
          }
        }
      }

      if (!found) {
        TextView empty = new TextView(activity);
        empty.setText(app.zipper.knot.utils.ModuleStrings.READ_HISTORY_EMPTY);
        empty.setPadding(0, 100, 0, 100);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(Color.GRAY);
        container.addView(empty);
      }

      int themeId =
          isDark ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
      AlertDialog.Builder builder = new AlertDialog.Builder(activity, themeId);
      builder.setView(scrollView);
      builder.setPositiveButton(app.zipper.knot.utils.ModuleStrings.READ_HISTORY_CLOSE, null);
      if (targetChatId != null && found) {
        builder.setNeutralButton(
            app.zipper.knot.utils.ModuleStrings.READ_HISTORY_DELETE,
            (dialog, which) -> {
              new AlertDialog.Builder(activity, themeId)
                  .setTitle(app.zipper.knot.utils.ModuleStrings.READ_HISTORY_DELETE_CONFIRM_TITLE)
                  .setMessage(app.zipper.knot.utils.ModuleStrings.READ_HISTORY_DELETE_CONFIRM_MSG)
                  .setPositiveButton(
                      app.zipper.knot.utils.ModuleStrings.SETTINGS_YES,
                      (d, w) -> {
                        clearChatHistory(targetChatId);
                      })
                  .setNegativeButton(app.zipper.knot.utils.ModuleStrings.SETTINGS_CANCEL, null)
                  .show();
            });
      }
      builder.show();

    } catch (Throwable t) {
      XposedBridge.log("Knot: error: " + t.getMessage());
    }
  }

  private static void renderMessages(
      Activity activity, LinearLayout container, JSONObject messages, boolean isDark) {
    java.util.List<String> sortedKeys = new java.util.ArrayList<>();
    java.util.Iterator<String> keys = messages.keys();
    while (keys.hasNext()) {
      sortedKeys.add(keys.next());
    }
    java.util.Collections.sort(
        sortedKeys,
        (a, b) -> {
          try {
            return Long.compare(Long.parseLong(b), Long.parseLong(a));
          } catch (Exception e) {
            return b.compareTo(a);
          }
        });

    for (String msgId : sortedKeys) {
      JSONObject msg = messages.optJSONObject(msgId);
      if (msg == null) continue;
      addMessageCard(activity, container, msg, isDark);
    }
  }

  private static void addMessageCard(
      Activity activity, LinearLayout container, JSONObject msg, boolean isDark) {
    String messageText = msg.optString("c", "");

    LinearLayout card = new LinearLayout(activity);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(25, 20, 25, 20);
    LinearLayout.LayoutParams cardLp =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardLp.setMargins(0, 5, 0, 5);
    card.setLayoutParams(cardLp);

    android.graphics.drawable.GradientDrawable gd =
        new android.graphics.drawable.GradientDrawable();
    gd.setColor(isDark ? Color.parseColor("#08FFFFFF") : Color.parseColor("#08000000"));
    gd.setCornerRadius(12f);
    card.setBackground(gd);

    TextView contentText = new TextView(activity);
    contentText.setText(
        messageText != null && !messageText.isEmpty()
            ? messageText
            : app.zipper.knot.utils.ModuleStrings.READ_HISTORY_UNKNOWN_MSG);
    contentText.setTextColor(isDark ? Color.parseColor("#E0E0E0") : Color.parseColor("#333333"));
    contentText.setTextSize(17);
    contentText.setPadding(0, 0, 0, 15);
    card.addView(contentText);

    JSONObject readers = msg.optJSONObject("r");
    if (readers != null) {
      java.util.Iterator<String> rKeys = readers.keys();
      while (rKeys.hasNext()) {
        String rMid = rKeys.next();
        JSONObject reader = readers.optJSONObject(rMid);
        if (reader == null) continue;

        String readerName = reader.optString("n", "Unknown");
        String readTime = reader.optString("t", "");

        LinearLayout detailRow = new LinearLayout(activity);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);
        detailRow.setPadding(0, 5, 0, 5);

        TextView nameText = new TextView(activity);
        nameText.setText(readerName);
        nameText.setTextColor(isDark ? Color.parseColor("#AAAAAA") : Color.parseColor("#666666"));
        nameText.setTextSize(15);
        detailRow.addView(nameText);

        TextView timeText = new TextView(activity);
        timeText.setText(readTime);
        timeText.setTextSize(12);
        timeText.setTextColor(isDark ? Color.parseColor("#888888") : Color.parseColor("#999999"));
        timeText.setPadding(20, 0, 0, 0);
        detailRow.addView(timeText);

        card.addView(detailRow);
      }
    }

    container.addView(card);
    View margin = new View(activity);
    container.addView(
        margin, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 15));
  }

  private static void clearChatHistory(String chatId) {
    try {
      JSONObject historyJson = SettingsStore.loadReadHistory();
      JSONObject chats = historyJson.optJSONObject("c");
      if (chats != null && chats.has(chatId)) {
        chats.remove(chatId);
        SettingsStore.saveReadHistory(historyJson);
      }
    } catch (Exception e) {
    }
  }
}
