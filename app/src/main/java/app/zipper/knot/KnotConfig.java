package app.zipper.knot;

import app.zipper.knot.utils.ModuleStrings;

public class KnotConfig {

  public enum Category {
    CHAT(ModuleStrings.CAT_CHAT),
    DISPLAY(ModuleStrings.CAT_DISPLAY),
    EXPERIMENTAL(ModuleStrings.CAT_EXPERIMENTAL),
    NOTIFICATION(ModuleStrings.CAT_NOTIFICATION),
    SYSTEM(ModuleStrings.CAT_SYSTEM),
    BACKUP(ModuleStrings.CAT_BACKUP),
    OTHER(ModuleStrings.CAT_OTHER);

    public final String label;

    Category(String label) {
      this.label = label;
    }
  }

  public static class Item {
    public final String key;
    public final String label;
    public final String description;
    public boolean enabled;
    public String value = "";
    public final Category category;

    public Item(
        String key, String label, String description, boolean defaultEnabled, Category category) {
      this.key = key;
      this.label = label;
      this.description = description;
      this.enabled = defaultEnabled;
      this.category = category;
    }
  }

  public Item removeAds =
      new Item(
          "remove_ads",
          ModuleStrings.OPT_REMOVE_ADS_LABEL,
          ModuleStrings.OPT_REMOVE_ADS_DESC,
          false,
          Category.DISPLAY);

  public Item removeTabVoom =
      new Item(
          "remove_tab_voom",
          ModuleStrings.OPT_REMOVE_TAB_VOOM_LABEL,
          ModuleStrings.OPT_REMOVE_TAB_VOOM_DESC,
          false,
          Category.DISPLAY);

  public Item removeTabNews =
      new Item(
          "remove_tab_news",
          ModuleStrings.OPT_REMOVE_TAB_NEWS_LABEL,
          ModuleStrings.OPT_REMOVE_TAB_NEWS_DESC,
          false,
          Category.DISPLAY);

  public Item removeTabMini =
      new Item(
          "remove_tab_mini",
          ModuleStrings.OPT_REMOVE_TAB_MINI_LABEL,
          ModuleStrings.OPT_REMOVE_TAB_MINI_DESC,
          false,
          Category.DISPLAY);

  public Item extendTabClickArea =
      new Item(
          "extend_tab_click_area",
          ModuleStrings.OPT_EXTEND_TAB_CLICK_AREA_LABEL,
          ModuleStrings.OPT_EXTEND_TAB_CLICK_AREA_DESC,
          false,
          Category.DISPLAY);

  public Item hideTabText =
      new Item(
          "hide_tab_text",
          ModuleStrings.OPT_HIDE_TAB_TEXT_LABEL,
          ModuleStrings.OPT_HIDE_TAB_TEXT_DESC,
          false,
          Category.DISPLAY);

  public Item removeHomeRecommendations =
      new Item(
          "remove_home_recommendations",
          ModuleStrings.OPT_REMOVE_HOME_RECOMMENDATIONS_LABEL,
          ModuleStrings.OPT_REMOVE_HOME_RECOMMENDATIONS_DESC,
          false,
          Category.DISPLAY);

  public Item removeHomeServices =
      new Item(
          "remove_home_services",
          ModuleStrings.OPT_REMOVE_HOME_SERVICES_LABEL,
          ModuleStrings.OPT_REMOVE_HOME_SERVICES_DESC,
          false,
          Category.DISPLAY);

  public Item removeHomeAccordion =
      new Item(
          "remove_home_accordion",
          ModuleStrings.OPT_REMOVE_HOME_ACCORDION_LABEL,
          ModuleStrings.OPT_REMOVE_HOME_ACCORDION_DESC,
          false,
          Category.DISPLAY);

  public Item preventMarkAsRead =
      new Item(
          "prevent_mark_as_read",
          ModuleStrings.OPT_PREVENT_MARK_AS_READ_LABEL,
          ModuleStrings.OPT_PREVENT_MARK_AS_READ_DESC,
          false,
          Category.CHAT);

  public Item preventUnsendMessage =
      new Item(
          "prevent_unsend_message",
          ModuleStrings.OPT_PREVENT_UNSEND_MESSAGE_LABEL,
          ModuleStrings.OPT_PREVENT_UNSEND_MESSAGE_DESC,
          false,
          Category.CHAT);

  public Item spoofVersion =
      new Item(
          "spoof_version",
          ModuleStrings.OPT_SPOOF_VERSION_LABEL,
          ModuleStrings.OPT_SPOOF_VERSION_DESC,
          false,
          Category.EXPERIMENTAL);

  public Item safeSettingsResources =
      new Item(
          "safe_settings_resources",
          ModuleStrings.OPT_FIX_SETTINGS_TALK_CRASH_LABEL,
          ModuleStrings.OPT_FIX_SETTINGS_TALK_CRASH_DESC,
          true,
          Category.EXPERIMENTAL);

  public Item spoofVersionUnsendOnly =
      new Item(
          "spoof_version_unsend_only",
          ModuleStrings.OPT_SPOOF_VERSION_UNSEND_ONLY_LABEL,
          ModuleStrings.OPT_SPOOF_VERSION_UNSEND_ONLY_DESC,
          false,
          Category.CHAT);

  public Item recordReadHistory =
      new Item(
          "record_read_history",
          ModuleStrings.OPT_RECORD_READ_HISTORY_LABEL,
          ModuleStrings.OPT_RECORD_READ_HISTORY_DESC,
          false,
          Category.CHAT);

  public Item hideAiIconPermanently =
      new Item(
          "hide_ai_icon_permanently",
          ModuleStrings.OPT_HIDE_AI_ICON_PERMANENTLY_LABEL,
          ModuleStrings.OPT_HIDE_AI_ICON_PERMANENTLY_DESC,
          false,
          Category.CHAT);

  public Item removeNotificationMuteButton =
      new Item(
          "remove_notification_mute_button",
          ModuleStrings.OPT_REMOVE_NOTIFICATION_MUTE_BUTTON_LABEL,
          ModuleStrings.OPT_REMOVE_NOTIFICATION_MUTE_BUTTON_DESC,
          false,
          Category.NOTIFICATION);

  public Item experimentalFcmFix =
      new Item(
          "experimental_fcm_fix",
          ModuleStrings.OPT_EXPERIMENTAL_FCM_FIX_LABEL,
          ModuleStrings.OPT_EXPERIMENTAL_FCM_FIX_DESC,
          false,
          Category.EXPERIMENTAL);

  public Item reactionNotification =
      new Item(
          "reaction_notification",
          ModuleStrings.OPT_REACTION_NOTIFICATION_LABEL,
          ModuleStrings.OPT_REACTION_NOTIFICATION_DESC,
          false,
          Category.NOTIFICATION);

  public Item removeAiFriendsButton =
      new Item(
          "remove_ai_friends_button",
          ModuleStrings.OPT_REMOVE_AI_FRIENDS_BUTTON_LABEL,
          ModuleStrings.OPT_REMOVE_AI_FRIENDS_BUTTON_DESC,
          false,
          Category.DISPLAY);

  public Item removeSearchBarAgentIButton =
      new Item(
          "remove_search_bar_agent_i_button",
          ModuleStrings.OPT_REMOVE_SEARCH_BAR_AGENT_I_BUTTON_LABEL,
          ModuleStrings.OPT_REMOVE_SEARCH_BAR_AGENT_I_BUTTON_DESC,
          false,
          Category.DISPLAY);

  public Item removeOpenChatButton =
      new Item(
          "remove_open_chat_button",
          ModuleStrings.OPT_REMOVE_OPEN_CHAT_BUTTON_LABEL,
          ModuleStrings.OPT_REMOVE_OPEN_CHAT_BUTTON_DESC,
          false,
          Category.DISPLAY);

  public Item openUrlInDefaultBrowser =
      new Item(
          "open_url_in_default_browser",
          ModuleStrings.OPT_OPEN_URL_IN_DEFAULT_BROWSER_LABEL,
          ModuleStrings.OPT_OPEN_URL_IN_DEFAULT_BROWSER_DESC,
          false,
          Category.CHAT);

  public Item useCustomFont =
      new Item(
          "use_custom_font",
          ModuleStrings.OPT_USE_CUSTOM_FONT_LABEL,
          ModuleStrings.OPT_USE_CUSTOM_FONT_DESC,
          false,
          Category.DISPLAY);

  public Item customFontPath =
      new Item(
          "custom_font_path",
          ModuleStrings.OPT_CUSTOM_FONT_PATH_LABEL,
          ModuleStrings.OPT_CUSTOM_FONT_PATH_DESC,
          false,
          Category.DISPLAY);

  public Item highQualityPhoto =
      new Item(
          "high_quality_photo",
          ModuleStrings.OPT_HIGH_QUALITY_PHOTO_LABEL,
          ModuleStrings.OPT_HIGH_QUALITY_PHOTO_DESC,
          false,
          Category.CHAT);

  public Item[] items = {
    removeAiFriendsButton,
    removeSearchBarAgentIButton,
    removeOpenChatButton,
    removeAds,
    removeTabVoom,
    removeTabNews,
    removeTabMini,
    extendTabClickArea,
    hideTabText,
    removeHomeRecommendations,
    removeHomeServices,
    removeHomeAccordion,
    useCustomFont,
    customFontPath,
    preventMarkAsRead,
    preventUnsendMessage,
    hideAiIconPermanently,
    spoofVersionUnsendOnly,
    recordReadHistory,
    openUrlInDefaultBrowser,
    removeNotificationMuteButton,
    reactionNotification,
    experimentalFcmFix,
    spoofVersion,
    safeSettingsResources,
    highQualityPhoto
  };
}
