package app.zipper.knot;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.HashMap;
import java.util.Map;

public class LineVersion {

  public static class Config {
    public String linePkg = "jp.naver.line.android";

    public Main main = new Main();
    public Settings settings = new Settings();
    public PlusMenu plusMenu = new PlusMenu();
    public Unsend unsend = new Unsend();
    public Thrift thrift = new Thrift();
    public Tabs tabs = new Tabs();
    public Ads ads = new Ads();
    public Home home = new Home();
    public Chat chat = new Chat();
    public Res res = new Res();
    public ReadReceipt readReceipt = new ReadReceipt();
    public ChatHeader chatHeader = new ChatHeader();
    public Font font = new Font();
    public ThemeFree themeFree = new ThemeFree();
    public StickerTrial stickerTrial = new StickerTrial();
    public Notification notification = new Notification();
    public TalkTabHeader talkTabHeader = new TalkTabHeader();
    public SearchBarAgentI searchBarAgentI = new SearchBarAgentI();
    public AiIcon aiIcon = new AiIcon();

    public static class Main {
      public String mainActivity = "";
      public String headerButton = "";
      public String headerButtonInnerField = "";
      public String headerButtonTypeClass = "";
      public String slotFarLeft = "";
      public String headerInterfaceA = "";
      public String fieldHeaderHelper = "";
      public String fieldChatActivity = "";
      public String methodSetHeaderButton = "";
      public String methodSetHeaderIcon = "";
      public String methodSetHeaderLabel = "";
      public String methodSetHeaderButtonVisibility = "";
      public String methodGetHeaderButtonView = "";
      public String methodSetHeaderOnClickListener = "";
      public String methodRefreshNavHeader = "";
      public String methodHeaderSetTitle = "";
      public String methodHeaderSetButtonVisibility = "";
      public String methodHeaderSetButtonListener = "";
      public String fieldNavHeader = "";
    }

    public static class Settings {
      public String mainSettingsFragmentClass = "";
      public String settingsAdapterClass = "";
      public String settingsItemClass = "";
      public String settingsBaseAdapterClass = "";
      public String settingsSearchHelperClass = "";
      public String settingsAdapterWrapperClass = "";
      public String methodSetItems = "";
      public String methodBindViewHolder = "";
      public String methodGetItem = "";
      public String viewHolderSwitch = "";
      public String methodBindDescription = "";
      public String fieldItemModel = "";
      public String fieldModelTag = "";
      public String fieldViewHolderView = "";
      public String settingsHeaderItemClass = "";
      public String settingsRowItemClass = "";
      public String settingsHandlerBaseClass = "";
      public String methodGetItemCount = "";
      public String methodBindItem = "";
      public String methodCreateViewHolder = "";
      public String fieldItemType = "";
      public String methodGetItemType = "";
      public String fieldIsClickable = "";
      public String fieldIsEditable = "";
      public String fieldIsVisible = "";
      public String fieldLayoutId = "";
      public String fieldActionHandler = "";
      public String fieldIconProvider = "";
      public String fieldDescriptionProvider = "";
      public String fieldSubActionHandler = "";
      public String fieldVisibilityFilter = "";
      public String fieldDefaultHandler = "";
      public String fieldCommonHandler = "";
      public String methodSetDescription = "";
      public String methodProxyGetItemType = "";
      public String methodSetTitleText = "";
      public String methodSetChecked = "";
      public String methodSetItemType = "";
      public String methodSetSyncStatus = "";
      public String methodSetDividerVisible = "";
    }

    public static class PlusMenu {
      public String plusMenuComponentClass = "";
      public String plusMenuComposerClass = "";
      public String plusMenuComposerImplClass = "";
      public String plusMenuScopeClass = "";
      public String plusMenuCallbackClass = "";
      public String plusMenuModifierClass = "";
      public String plusMenuOnClickItemClass = "";

      public String methodAddMenuItem = "";
      public String methodCreateMenu = "";
      public String methodExecuteAction = "";

      public String editChatDrawable = "";
      public boolean isSwapModifierCallback = false;
      public boolean isNewMenuItemSignature = false;
      public String moduleId = "app.zipper.knot";
      public String targetPkg = "jp.naver.line.android";
    }

    public static class ReadReceipt {
      public String readReceiptManagerClass = "";
      public String readReceiptQueueClass = "";
      public String methodEnqueueReadReceipt = "";
      public String methodSendReadReceipt = "";
      public String methodExecuteReadReceiptAsync = "";
      public String methodReadAll = "";
      public String operationNotifiedReadName = "NOTIFIED_READ_MESSAGE";
      public String badgeClearClass = "";
    }

    public static class Unsend {
      public String talkServiceHookClass = "";
      public String chatMessageViewHolderClass = "";
      public String chatMessageViewDataClass = "";

      public String methodReadBuffer = "";
      public String methodBind = "";
      public String methodOperationTypeValueOf = "";
      public int methodBindIndex = 0;
      public String methodGetItemView = "";
      public String methodGetCommonData = "";
      public int operationTypeDummy = 0;

      public String chatServiceConfigClass = "";
      public String methodUnsendLimit = "";
      public String methodUnsendPremiumLimit = "";
      public String appInfoProviderClass = "";
      public String methodGetFullUserAgent = "";
      public String methodGetSimpleUserAgent = "";
      public String methodGetFullUserAgentWithContext = "";
      public String methodGetSimpleUserAgentWithContext = "";
      public String methodUnsendThrift = "";
      public String methodUnsendThriftSilent = "";
      public String methodUnsendAnnouncement = "";
      public String operationTypeField = "";
      public String operationParam1Field = "";
      public String operationParam2Field = "";
      public String operationParam3Field = "";
      public String operationCreatedTimeField = "";
      public String chatMessageIdField = "";
      public boolean isOpReadScheme = false;

      public String operationUnsendName = "DESTROY_MESSAGE";
      public String operationNotifiedUnsendName = "NOTIFIED_DESTROY_MESSAGE";
      public String unsendDestroyHandlerClass = "";
    }

    public static class Thrift {
      public String talkServiceClientImplClass = "";
      public String talkServiceClientInterface = "";
      public String chatSearchActivityClass = "";
      public String sendMessage = "";
      public String v1 = "";
      public String v2 = "";
      public String protocolClass = "";
      public String messageClass = "";
      public String methodWriteMessageBegin = "";

      public String methodDestroyMessage = "destroyMessage";
      public String methodDestroyMessages = "destroyMessages";
    }

    public static class Tabs {
      public String bottomNavigationBarTextViewClass = "";
      public String resVoom = "";
      public String resNews = "";
      public String resMini = "";
      public String resContainer = "";
      public String resBtnText = "";
      public String resBtnImg = "";
      public String resBtnAnimImg = "";
    }

    public static class Ads {
      public String ladAdView = "";
      public String smartChannel = "";
      public String classAdSdkBase = "";
      public String classAdMolinBase = "";
    }

    public static class Home {
      public String resRecommendation = "";
      public String resServiceCarouselId = "";
      public String resServiceTitleId = "";
      public String resNoServicesId = "";
      public String lypRecommendationModuleArgClass = "";
      public String lypRecommendationContextClass = "";
      public String lypRecommendationComposerClass = "";
      public String lypRecommendationModuleClass = "";
      public String lypRecommendationControllerClass = "";
    }

    public static class Chat {
      public String headerController = "";
      public String headerHelper = "";
      public String chatIdField = "";
      public String methodGetChatId = "";
    }

    public static class ChatHeader {
      public String chatHistoryActivity = "";
      public String fieldChatConfigChatId = "";
      public String fieldChatConfigIsMuted = "";
      public String fieldChatConfigCategory = "";
      public String fieldChatConfigType = "";
      public String fieldAppInfoVersion = "";
      public String fieldAppInfoPkg = "";
      public String fieldAppInfoId = "";
    }

    public static class ThemeFree {
      public String productDataClass;
      public String themeRepositoryClass;
      public String methodSetOwnership;
      public String fieldThemeProduct;
      public String methodIsThemeOwned;
      public String fieldIsOwnedA;
      public String fieldIsOwnedB;
      public String fieldIsOwnedC;
      public String fieldIsOwnedD;
      public String fieldIsOwned;
      public String fieldProductType;
      public String methodGetProductTypeName;
      public String themeDetailActivityClass;
      public String[] methodThemeDetailUpdates;
    }

    public static class StickerTrial {
      public String freeTrialStatusCheckerClass;
      public String stickerDatabaseHelperClass;
      public String stickerContentValueDataClass;
      public String methodGetContentValues = "";
      public String methodGetLimitCount = "";
      public String methodGetLimitRemaining = "";
      public String methodCheckAvailability = "";
      public String methodCheckStatus = "";
      public String methodInsertSticker = "";
    }

    public static class Font {
      public String fontConfigClass = "";
      public String fontManagerClass = "";
      public String fontSettingsClass = "";
      public String fontCallbackClass = "";
      public String fontInjectedClass = "";
      public String methodGetFontConfig = "a";
      public String methodInitializeFont = "b";
      public String methodGetFontSettings = "e";
      public String methodOnFontChanged = "b";
      public String fieldTypeface = "";
    }

    public static class Res {
      public int idSettingList = 0;
      public int idPersonalInfo = 0;
      public int typeSection = 0;
      public int typeRow = 0;
      public int idIcon = 0;
      public int idDesc = 0;
      public int idMark = 0;
      public int idSeparator = 0;
      public int idArrow = 0;
      public int idNewMark = 0;
      public int idNoticeDot = 0;
      public int idTitle = 0;
      public int layoutCheckbox = 0;
      public int layoutSectionHeader = 0;
      public int layoutSettingsMain = 0;
      public int idHeader = 0;
      public int idStatusBarGuide = 0;
      public int idTimestamp = 0;
      public int idChatMessageText = 0;
      public String resSettingsHeaderBtn = "";
      public String resSettingsBtn = "";
    }

    public static class Notification {
      public String chatHistoryRequestClass = "";
      public String chatHistoryActivityLaunchActivityClass = "";
      public String lineAppVersionClass = "";
    }

    public static class TalkTabHeader {
      public String chatTabHeaderStateClass = "";
      public String iconListStateField = "";
      public String buttonListStateField = "";
      public String iconTypeClass = "";
      public String iconTypeFieldInButton = "";
    }

    public static class SearchBarAgentI {
      public String talkVisibleMethod = "";
      public String talkClickMethod = "";
      public String homeSearchBarClass = "";
      public String homeRefreshMethod = "";
      public String homeRootViewField = "";
      public String homeTabTypeField = "";
      public String homeTabName = "";
      public String homeTabV2Name = "";
      public int homeAiContainerId = 0;
      public int homeGuidelineId = 0;
      public int homeGuidelineEndDp = 0;
      public String homeGuidelineClass = "";
    }

    public static class AiIcon {
      public String repoClass = "";
      public String methodGetShownAfterMillis = "";
    }
  }

  private static final Map<String, Config> VERSION_TABLE = new HashMap<>();
  static {
    VERSION_TABLE.put("26.5.", app.zipper.knot.versions.Version265.create());
    VERSION_TABLE.put("26.6.", app.zipper.knot.versions.Version266.create());
  }

  private static volatile Config cachedConfig = null;

  public static Config get() { return cachedConfig; }

  public static Config get(ClassLoader cl) {
    if (cachedConfig != null)
      return cachedConfig;
    return detect(cl);
  }

  public static Config detect(ClassLoader cl) {
    if (cachedConfig != null)
      return cachedConfig;
    if (cl == null)
      return null;
    try {
      Class<?> verCls =
          cl.loadClass("jp.naver.line.android.common.LineAppVersion");
      String verName =
          (String)XposedHelpers.callStaticMethod(verCls, "getVerName");
      XposedBridge.log("Knot: Detected LINE version: " + verName);

      for (Map.Entry<String, Config> entry : VERSION_TABLE.entrySet()) {
        if (verName.startsWith(entry.getKey())) {
          cachedConfig = entry.getValue();
          return cachedConfig;
        }
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Version detection via class failed: " +
                       t.getMessage());
    }

    return null;
  }

  public static Config detectWithContext(android.content.Context context) {
    if (cachedConfig != null)
      return cachedConfig;
    if (context == null)
      return null;
    try {
      String verName = context.getPackageManager()
                           .getPackageInfo(context.getPackageName(), 0)
                           .versionName;
      XposedBridge.log("Knot: Detected LINE version via PackageInfo: " +
                       verName);
      for (Map.Entry<String, Config> entry : VERSION_TABLE.entrySet()) {
        if (verName.startsWith(entry.getKey())) {
          cachedConfig = entry.getValue();
          return cachedConfig;
        }
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Version detection via PackageInfo failed: " +
                       t.getMessage());
    }
    return null;
  }

  public static String getSupportedVersions() {
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (String key : VERSION_TABLE.keySet()) {
      if (count > 0)
        sb.append(", ");
      // Convert "26.5." -> "26.5.0" for display
      String ver = key.endsWith(".") ? key + "0" : key;
      sb.append(ver);
      count++;
    }
    return sb.toString();
  }
}
