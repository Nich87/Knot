package app.zipper.knot.versions;

import app.zipper.knot.LineVersion;

public class Version266 {
  public static LineVersion.Config create() {
    LineVersion.Config v = new LineVersion.Config();

    v.main.mainActivity = "jp.naver.line.android.activity.main.MainActivity";
    v.main.headerButton =
        "jp.naver.line.android.common.view.header.HeaderButton";
    v.main.headerButtonInnerField = "f190567a";
    v.main.headerButtonTypeClass = "ra8.d";
    v.main.slotFarLeft = "FAR_LEFT";
    v.main.headerInterfaceA = "jp.naver.line.android.common.view.header.a";
    v.main.fieldHeaderHelper = "f";
    v.main.fieldChatActivity = "a";
    v.main.methodSetHeaderButton = "i";
    v.main.methodSetHeaderIcon = "o";
    v.main.methodSetHeaderLabel = "k";
    v.main.methodSetHeaderButtonVisibility = "s";
    v.main.methodGetHeaderButtonView = "h";
    v.main.methodSetHeaderOnClickListener = "r";
    v.main.methodRefreshNavHeader = "a";
    v.main.methodHeaderSetTitle = "setTitle";
    v.main.methodHeaderSetButtonVisibility =
        "setUpButtonVisibility$common_libs";
    v.main.methodHeaderSetButtonListener =
        "setUpButtonOnClickListener$common_libs";
    v.main.fieldNavHeader = "i";

    v.settings.mainSettingsFragmentClass =
        "com.linecorp.line.settings.main.LineUserMainSettingsFragment";
    v.settings.settingsAdapterClass = "v68.f";
    v.settings.settingsItemClass = "v68.f$c";
    v.settings.settingsBaseAdapterClass = "v68.f$b";
    v.settings.settingsSearchHelperClass = "pp4.b";
    v.settings.settingsAdapterWrapperClass = "jl4.a";
    v.settings.settingsHeaderItemClass = "kl4.q";
    v.settings.settingsRowItemClass = "kl4.s";
    v.settings.settingsHandlerBaseClass = "kl4.z";
    v.settings.methodSetItems = "n";
    v.settings.methodBindViewHolder = "r";
    v.settings.methodGetItem = "q";
    v.settings.viewHolderSwitch = "pl4.o2";
    v.settings.methodBindDescription = "M0";
    v.settings.fieldItemModel = "a";
    v.settings.fieldModelTag = "a";
    v.settings.fieldViewHolderView = "a";
    v.settings.methodGetItemCount = "q";
    v.settings.methodBindItem = "o";
    v.settings.methodCreateViewHolder = "v";
    v.settings.fieldItemType = "u";
    v.settings.methodGetItemType = "n";
    v.settings.fieldIsClickable = "s";
    v.settings.fieldIsEditable = "t";
    v.settings.fieldIsVisible = "y";
    v.settings.fieldLayoutId = "b";
    v.settings.fieldActionHandler = "d";
    v.settings.fieldIconProvider = "f";
    v.settings.fieldDescriptionProvider = "g";
    v.settings.fieldSubActionHandler = "h";
    v.settings.fieldVisibilityFilter = "j";
    v.settings.fieldDefaultHandler = "p";
    v.settings.fieldCommonHandler = "m";
    v.settings.methodSetDescription = "b";
    v.settings.methodProxyGetItemType = "g";
    v.settings.methodSetTitleText = "setTitleText";
    v.settings.methodSetChecked = "setChecked";
    v.settings.methodSetItemType = "setItemType";
    v.settings.methodSetSyncStatus = "setSyncStatus";
    v.settings.methodSetDividerVisible = "setDividerVisible";

    v.plusMenu.plusMenuComponentClass = "av0.t";
    v.plusMenu.plusMenuComposerClass = "t2.k";
    v.plusMenu.plusMenuComposerImplClass = "t2.l";
    v.plusMenu.plusMenuScopeClass = "t2.k2";
    v.plusMenu.plusMenuCallbackClass = "yh8.a";
    v.plusMenu.plusMenuModifierClass = "androidx.compose.ui.e";
    v.plusMenu.plusMenuOnClickItemClass = "yh8.l";
    v.plusMenu.methodAddMenuItem = "a";
    v.plusMenu.methodCreateMenu = "c";
    v.plusMenu.methodExecuteAction = "X";
    v.plusMenu.editChatDrawable = "chat_tab_ui_header_plusmenu_edit_chat";
    v.plusMenu.isSwapModifierCallback = true;
    v.plusMenu.isNewMenuItemSignature = true;

    v.readReceipt.readReceiptManagerClass = "at2.e";
    v.readReceipt.readReceiptQueueClass = "he8.b";
    v.readReceipt.methodEnqueueReadReceipt = "c";
    v.readReceipt.methodSendReadReceipt = "d";
    v.readReceipt.methodExecuteReadReceiptAsync = "e";
    v.readReceipt.methodReadAll = "c";
    v.readReceipt.operationNotifiedReadName = "NOTIFIED_READ_MESSAGE";
    v.readReceipt.badgeClearClass = "dc8.b";

    v.unsend.talkServiceHookClass = "fh8.ae$a";
    v.unsend.chatMessageViewHolderClass = "nd1.f";
    v.unsend.chatMessageViewDataClass = "g21.h";
    v.unsend.methodReadBuffer = "a";
    v.unsend.methodBind = "N";
    v.unsend.methodOperationTypeValueOf = "a";
    v.unsend.methodBindIndex = 1;
    v.unsend.methodGetItemView = "c0";
    v.unsend.methodGetCommonData = "b";
    v.unsend.operationTypeDummy = 40;
    v.unsend.chatServiceConfigClass = "fk4.p";
    v.unsend.methodUnsendLimit = "i";
    v.unsend.methodUnsendPremiumLimit = "h";
    v.unsend.appInfoProviderClass = "le8.d";
    v.unsend.methodGetFullUserAgent = "h";
    v.unsend.methodGetSimpleUserAgent = "k";
    v.unsend.methodGetFullUserAgentWithContext = "i";
    v.unsend.methodGetSimpleUserAgentWithContext = "l";
    v.unsend.methodUnsendThrift = "unsendMessage";
    v.unsend.methodUnsendThriftSilent = "silentlyUnsendMessage";
    v.unsend.methodUnsendAnnouncement = "unsendChatRoomAnnouncement";
    v.unsend.operationTypeField = "c";
    v.unsend.operationParam1Field = "g";
    v.unsend.operationParam2Field = "h";
    v.unsend.operationParam3Field = "i";
    v.unsend.operationCreatedTimeField = "b";
    v.unsend.chatMessageIdField = "d";
    v.unsend.isOpReadScheme = true;
    v.unsend.operationUnsendName = "DESTROY_MESSAGE";
    v.unsend.operationNotifiedUnsendName = "NOTIFIED_DESTROY_MESSAGE";
    v.unsend.unsendDestroyHandlerClass = "if8.y0";

    v.thrift.talkServiceClientImplClass =
        "jp.naver.line.android.thrift.client.impl.LegacyTalkServiceClientImpl";
    v.thrift.talkServiceClientInterface =
        "jp.naver.line.android.thrift.client.TalkServiceClient";
    v.thrift.chatSearchActivityClass =
        "com.linecorp.line.chat.search.ChatSearchActivity";
    v.thrift.sendMessage = "u0";
    v.thrift.v1 = "r1";
    v.thrift.v2 = "v2";
    v.thrift.protocolClass = "org.apache.thrift.p";
    v.thrift.messageClass = "org.apache.thrift.e";
    v.thrift.methodWriteMessageBegin = "b";
    v.thrift.methodDestroyMessage = "destroyMessage";
    v.thrift.methodDestroyMessages = "destroyMessages";

    v.tabs.bottomNavigationBarTextViewClass =
        "jp.naver.line.android.activity.main.bottomnavigationbar."
        + "BottomNavigationBarTextView";
    v.tabs.resVoom = "bnb_timeline";
    v.tabs.resNews = "bnb_news";
    v.tabs.resMini = "bnb_mini";
    v.tabs.resContainer = "main_tab_container";
    v.tabs.resBtnText = "bnb_button_text";
    v.tabs.resBtnImg = "bnb_button_image";
    v.tabs.resBtnAnimImg = "bnb_animated_button_image";

    v.ads.classAdSdkBase = "com.linecorp.line.ladsdk";
    v.ads.classAdMolinBase = "com.linecorp.line.admolin";
    v.ads.ladAdView =
        v.ads.classAdSdkBase + ".ui.common.view.lifecycle.LadAdView";
    v.ads.smartChannel =
        v.ads.classAdMolinBase + ".smartch.v2.view.SmartChannelViewLayout";

    v.home.resRecommendation = "home_tab_contents_recommendation_placement";
    v.home.resServiceCarouselId = "home_tab_service_carousel";
    v.home.resServiceTitleId = "home_tab_service_title";
    v.home.resNoServicesId = "home_tab_no_services_title";

    v.chat.headerController = "h81.m1";
    v.chat.headerHelper = "jp.naver.line.android.common.view.header.b";
    v.chat.chatIdField = "j";
    v.chat.methodGetChatId = "Q";

    v.chatHeader.chatHistoryActivity =
        "jp.naver.line.android.activity.chathistory.ChatHistoryActivity";
    v.chatHeader.fieldChatConfigChatId = "b41.a";
    v.chatHeader.fieldChatConfigIsMuted = "z31.a";
    v.chatHeader.fieldChatConfigCategory = "h01.g";
    v.chatHeader.fieldChatConfigType = "h81.y0";
    v.chatHeader.fieldAppInfoVersion = "hi1.n";
    v.chatHeader.fieldAppInfoPkg = "h01.a";
    v.chatHeader.fieldAppInfoId = "wl0.d";

    v.font.fontConfigClass = "k6.n";
    v.font.fontManagerClass = "k6.m";
    v.font.fontSettingsClass = "v74.e";
    v.font.fontCallbackClass = "k6.n$c";
    v.font.fontInjectedClass = "x74.g";
    v.font.methodGetFontConfig = "a";
    v.font.methodInitializeFont = "b";
    v.font.methodGetFontSettings = "g";
    v.font.methodOnFontChanged = "b";
    v.font.fieldTypeface = "f197160a";

    v.themeFree.productDataClass = "r25.f";
    v.themeFree.themeRepositoryClass = "i67.m";
    v.themeFree.methodSetOwnership = "r";
    v.themeFree.fieldThemeProduct = "u";
    v.themeFree.methodIsThemeOwned = "a";
    v.themeFree.fieldIsOwnedA = "A";
    v.themeFree.fieldIsOwnedB = "B";
    v.themeFree.fieldIsOwnedC = "C";
    v.themeFree.fieldIsOwnedD = "D";
    v.themeFree.fieldIsOwned = "s";
    v.themeFree.fieldProductType = "q";
    v.themeFree.methodGetProductTypeName = "e";
    v.themeFree.themeDetailActivityClass =
        "com.linecorp.shop.impl.theme.endpage.ShopThemeDetailActivity";
    v.themeFree.methodThemeDetailUpdates =
        new String[] {"A5", "B5", "E5", "i5", "q5", "r5", "y5"};

    v.stickerTrial.freeTrialStatusCheckerClass = "d15.e0";
    v.stickerTrial.stickerDatabaseHelperClass = "e35.e";
    v.stickerTrial.stickerContentValueDataClass = "f35.d";
    v.stickerTrial.methodGetContentValues = "a";
    v.stickerTrial.methodGetLimitCount = "d";
    v.stickerTrial.methodGetLimitRemaining = "e";
    v.stickerTrial.methodCheckAvailability = "b";
    v.stickerTrial.methodCheckStatus = "c";
    v.stickerTrial.methodInsertSticker = "b";

    v.res.idSettingList = 0x7f0b2373;
    v.res.idPersonalInfo = 0x7f15365b;
    v.res.typeSection = 0x7f0e0559;
    v.res.typeRow = 0x7f0e055c;
    v.res.idIcon = 0x7f0b2364;
    v.res.idDesc = 0x7f0b2356;
    v.res.idMark = 0x7f0b2377;
    v.res.idSeparator = 0x7f0b239d;
    v.res.idArrow = 0x7f0b233e;
    v.res.idNewMark = 0x7f0b19c9;
    v.res.idNoticeDot = 0x7f0b1a36;
    v.res.idTitle = 0x7f0b23a5;
    v.res.layoutCheckbox = 0x7f0e054e;
    v.res.layoutSectionHeader = 0x7f0e0559;
    v.res.layoutSettingsMain = 0x7f0e0553;
    v.res.idHeader = 0x7f0b1161;
    v.res.idStatusBarGuide = 0x7f0b2616;
    v.res.idTimestamp = 0x7f0b08b3;
    v.res.idChatMessageText = 0x7f0b07a9;
    v.res.resSettingsHeaderBtn = "settings_header_button";
    v.res.resSettingsBtn = "settings_button";

    v.notification.chatHistoryRequestClass =
        "com.linecorp.line.chat.request.ChatHistoryRequest";
    v.notification.chatHistoryActivityLaunchActivityClass =
        "jp.naver.line.android.activity.chathistory."
        + "ChatHistoryActivityLaunchActivity";
    v.notification.lineAppVersionClass =
        "jp.naver.line.android.common.LineAppVersion";

    v.talkTabHeader.chatTabHeaderStateClass = "pp1.e";
    v.talkTabHeader.iconListStateField = "x";
    v.talkTabHeader.buttonListStateField = "C";
    v.talkTabHeader.iconTypeClass = "yu0.n";
    v.talkTabHeader.iconTypeFieldInButton = "a";

    v.searchBarAgentI.talkVisibleMethod = "w";
    v.searchBarAgentI.talkClickMethod = "r";
    v.searchBarAgentI.homeSearchBarClass = "ni4.h";
    v.searchBarAgentI.homeRefreshMethod = "e";
    v.searchBarAgentI.homeRootViewField = "c";
    v.searchBarAgentI.homeTabTypeField = "b";
    v.searchBarAgentI.homeTabName = "HOME";
    v.searchBarAgentI.homeTabV2Name = "HOME_V2";
    v.searchBarAgentI.homeAiContainerId = 0x7f0b16db;
    v.searchBarAgentI.homeGuidelineId = 0x7f0b16dd;
    v.searchBarAgentI.homeGuidelineEndDp = 55;
    v.searchBarAgentI.homeGuidelineClass =
        "androidx.constraintlayout.widget.Guideline";

    v.aiIcon.repoClass = "yw0.c";
    v.aiIcon.methodGetShownAfterMillis = "i";

    return v;
  }
}
