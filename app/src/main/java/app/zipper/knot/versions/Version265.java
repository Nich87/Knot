package app.zipper.knot.versions;

import app.zipper.knot.LineVersion;

public class Version265 {
  public static LineVersion.Config create() {
    LineVersion.Config v = new LineVersion.Config();

    v.main.mainActivity = "jp.naver.line.android.activity.main.MainActivity";
    v.main.headerButton =
        "jp.naver.line.android.common.view.header.HeaderButton";
    v.main.headerButtonInnerField = "f185958a";
    v.main.headerButtonTypeClass = "t98.d";
    v.main.slotFarLeft = "FAR_LEFT";
    v.main.headerInterfaceA = "jp.naver.line.android.common.view.header.a";
    v.main.fieldHeaderHelper = "f";
    v.main.fieldChatActivity = "a";
    v.main.methodSetHeaderButton = "i";
    v.main.methodSetHeaderIcon = "o";
    v.main.methodSetHeaderLabel = "k";
    v.main.methodSetHeaderButtonVisibility = "u";
    v.main.methodGetHeaderButtonView = "h";
    v.main.methodSetHeaderOnClickListener = "t";
    v.main.methodRefreshNavHeader = "a";
    v.main.methodHeaderSetTitle = "setTitle";
    v.main.methodHeaderSetButtonVisibility =
        "setUpButtonVisibility$common_libs_release";
    v.main.methodHeaderSetButtonListener =
        "setUpButtonOnClickListener$common_libs_release";
    v.main.fieldNavHeader = "i";

    v.settings.mainSettingsFragmentClass =
        "com.linecorp.line.settings.main.LineUserMainSettingsFragment";
    v.settings.settingsAdapterClass = "x58.f";
    v.settings.settingsItemClass = "x58.f$c";
    v.settings.settingsBaseAdapterClass = "x58.f$b";
    v.settings.settingsSearchHelperClass = "xn4.b";
    v.settings.settingsAdapterWrapperClass = "uj4.a";
    v.settings.settingsHeaderItemClass = "vj4.q";
    v.settings.settingsRowItemClass = "vj4.s";
    v.settings.settingsHandlerBaseClass = "vj4.w";
    v.settings.methodSetItems = "n";
    v.settings.methodBindViewHolder = "r";
    v.settings.methodGetItem = "q";
    v.settings.viewHolderSwitch = "ak4.u2";
    v.settings.methodBindDescription = "D0";
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

    v.plusMenu.plusMenuComponentClass = "iu0.o";
    v.plusMenu.plusMenuComposerClass = "t2.k";
    v.plusMenu.plusMenuComposerImplClass = "t2.l";
    v.plusMenu.plusMenuScopeClass = "t2.k2";
    v.plusMenu.plusMenuCallbackClass = "ah8.a";
    v.plusMenu.plusMenuModifierClass = "androidx.compose.ui.e";
    v.plusMenu.plusMenuOnClickItemClass = "ah8.l";
    v.plusMenu.methodAddMenuItem = "a";
    v.plusMenu.methodCreateMenu = "c";
    v.plusMenu.methodExecuteAction = "X";
    v.plusMenu.editChatDrawable = "chat_tab_ui_header_plusmenu_edit_chat";
    v.plusMenu.isSwapModifierCallback = true;

    v.readReceipt.readReceiptManagerClass = "qr2.e";
    v.readReceipt.readReceiptQueueClass = "jd8.b";
    v.readReceipt.methodEnqueueReadReceipt = "c";
    v.readReceipt.methodSendReadReceipt = "d";
    v.readReceipt.methodExecuteReadReceiptAsync = "e";
    v.readReceipt.methodReadAll = "c";
    v.readReceipt.operationNotifiedReadName = "NOTIFIED_READ_MESSAGE";

    v.unsend.talkServiceHookClass = "hg8.ae$a";
    v.unsend.chatMessageViewHolderClass = "lc1.i";
    v.unsend.chatMessageViewDataClass = "lc1.v";
    v.unsend.methodReadBuffer = "b";
    v.unsend.methodBind = "o";
    v.unsend.methodOperationTypeValueOf = "a";
    v.unsend.methodBindIndex = 1;
    v.unsend.methodGetItemView = "U";
    v.unsend.methodGetCommonData = "b";
    v.unsend.operationTypeDummy = 40;
    v.unsend.chatServiceConfigClass =
        "com.linecorp.line.serviceconfiguration.o";
    v.unsend.methodUnsendLimit = "i";
    v.unsend.methodUnsendPremiumLimit = "h";
    v.unsend.appInfoProviderClass = "nd8.d";
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

    v.thrift.talkServiceClientImplClass =
        "jp.naver.line.android.thrift.client.impl.TalkServiceClientImpl";
    v.thrift.talkServiceClientInterface =
        "jp.naver.line.android.thrift.client.TalkServiceClient";
    v.thrift.chatSearchActivityClass =
        "com.linecorp.line.chat.search.ChatSearchActivity";
    v.thrift.sendMessage = "w2";
    v.thrift.v1 = "v1";
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

    v.chat.headerController = "q71.f1";
    v.chat.headerHelper = "jp.naver.line.android.common.view.header.b";
    v.chat.chatIdField = "j";
    v.chat.methodGetChatId = "O";

    v.chatHeader.chatHistoryActivity =
        "jp.naver.line.android.activity.chathistory.ChatHistoryActivity";
    v.chatHeader.fieldChatConfigChatId = "k31.a";
    v.chatHeader.fieldChatConfigIsMuted = "i31.a";
    v.chatHeader.fieldChatConfigCategory = "qz0.g";
    v.chatHeader.fieldChatConfigType = "q71.t0";
    v.chatHeader.fieldAppInfoVersion = "bh1.n";
    v.chatHeader.fieldAppInfoPkg = "qz0.a";
    v.chatHeader.fieldAppInfoId = "jl0.d";

    v.font.fontConfigClass = "k6.n";
    v.font.fontManagerClass = "k6.m";
    v.font.fontSettingsClass = "k64.e";
    v.font.fontCallbackClass = "k6.n$c";
    v.font.fontInjectedClass = "m64.i";
    v.font.fieldTypeface = "f201036a";

    v.themeFree.productDataClass = "z05.f";
    v.themeFree.themeRepositoryClass = "l57.n";
    v.themeFree.methodSetOwnership = "r";
    v.themeFree.fieldThemeProduct = "u";
    v.themeFree.methodIsThemeOwned = "b";
    v.themeFree.fieldIsOwnedA = "A";
    v.themeFree.fieldIsOwnedB = "B";
    v.themeFree.fieldIsOwnedC = "C";
    v.themeFree.fieldIsOwnedD = "D";
    v.themeFree.fieldIsOwned = "s";
    v.themeFree.fieldProductType = "q";
    v.themeFree.methodGetProductTypeName = "d";
    v.themeFree.themeDetailActivityClass =
        "com.linecorp.shop.impl.theme.endpage.ShopThemeDetailActivity";
    v.themeFree.methodThemeDetailUpdates =
        new String[] {"p5", "r5", "s5", "u5", "h5"};

    v.stickerTrial.freeTrialStatusCheckerClass = "lz4.d0";
    v.stickerTrial.stickerDatabaseHelperClass = "m15.e";
    v.stickerTrial.stickerContentValueDataClass = "m15.d";
    v.stickerTrial.methodGetContentValues = "a";
    v.stickerTrial.methodGetLimitCount = "d";
    v.stickerTrial.methodGetLimitRemaining = "e";
    v.stickerTrial.methodCheckAvailability = "b";
    v.stickerTrial.methodCheckStatus = "c";
    v.stickerTrial.methodInsertSticker = "b";

    v.res.idSettingList = 0x7f0b23ed;
    v.res.idPersonalInfo = 0x7f1535e3;
    v.res.typeSection = 0x7f0e055a;
    v.res.typeRow = 0x7f0e055d;
    v.res.idIcon = 0x7f0b23de;
    v.res.idDesc = 0x7f0b23d0;
    v.res.idMark = 0x7f0b1779;
    v.res.idSeparator = 0x7f0b2397;
    v.res.idArrow = 0x7f0b23b8;
    v.res.idNewMark = 0x7f0b1a3c;
    v.res.idNoticeDot = 0x7f0b1aaa;
    v.res.idTitle = 0x7f0b241f;
    v.res.layoutCheckbox = 0x7f0e054f;
    v.res.layoutSectionHeader = 0x7f0e055a;
    v.res.layoutSettingsMain = 0x7f0e0554;
    v.res.idHeader = 0x7f0b11b5;
    v.res.idStatusBarGuide = 0x7f0b2694;
    v.res.idTimestamp = 0x7f0b08f7;
    v.res.resSettingsHeaderBtn = "settings_header_button";
    v.res.resSettingsBtn = "settings_button";

    v.notification.chatHistoryRequestClass =
        "com.linecorp.line.chat.request.ChatHistoryRequest";
    v.notification.chatHistoryActivityLaunchActivityClass =
        "jp.naver.line.android.activity.chathistory." +
        "ChatHistoryActivityLaunchActivity";
    v.notification.lineAppVersionClass =
        "jp.naver.line.android.common.LineAppVersion";

    v.talkTabHeader.chatTabHeaderStateClass = "jo1.f";
    v.talkTabHeader.iconListStateField = "w";
    v.talkTabHeader.buttonListStateField = "C";
    v.talkTabHeader.iconTypeClass = "gu0.n";
    v.talkTabHeader.iconTypeFieldInButton = "a";

    return v;
  }
}
