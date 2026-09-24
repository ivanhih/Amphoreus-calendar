package com.amphoreus.calendar.ui;

import android.content.Context;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small, dependency-free translation table for the hand-built native UI. */
public final class LocaleText {
    private static final Map<String,String> EN=new HashMap<>();
    private static final Map<String,String> TW=new HashMap<>();
    static {
        put(EN,"设置","Settings"); put(TW,"设置","設定");
        put(EN,"返回","Back"); put(TW,"返回","返回");
        put(EN,"暂时无法完成","Couldn't complete that"); put(TW,"暂时无法完成","暫時無法完成");
        put(EN,"请稍后重试","Please try again later"); put(TW,"请稍后重试","請稍後重試");
        put(EN,"知道了","OK"); put(TW,"知道了","知道了");
        put(EN,"‹ 返回","‹ Back"); put(TW,"‹ 返回","‹ 返回");
        put(EN,"选择随机开屏素材","Choose splash artwork"); put(TW,"选择随机开屏素材","選擇隨機開屏素材");
        put(EN,"全选","Select all"); put(TW,"全选","全選");
        put(EN,"全不选","Select none"); put(TW,"全不选","全不選");
        put(EN,"保存","Save"); put(TW,"保存","儲存");
        put(EN,"取消","Cancel"); put(TW,"取消","取消");
        put(EN,"至少保留一张开屏图片","Keep at least one splash image"); put(TW,"至少保留一张开屏图片","至少保留一張開屏圖片");
        put(EN,"颜色","Colors"); put(TW,"颜色","顏色");
        put(EN,"开屏素材：全部 13 张（随机）","Splash artwork: all 13 (random)"); put(TW,"开屏素材：全部 13 张（随机）","開屏素材：全部 13 張（隨機）");

        put(EN,"日历与同步","Calendar & sync"); put(TW,"日历与同步","行事曆與同步");
        put(EN,"连接手机日历后，可直接读取与编辑系统日程。账号云同步由手机上已有的同步服务完成。","Connect your phone calendar to read and edit system events. Account sync is handled by the sync service already on your phone.");
        put(TW,"连接手机日历后，可直接读取与编辑系统日程。账号云同步由手机上已有的同步服务完成。","連接手機行事曆後，可直接讀取與編輯系統行程。帳號雲端同步由手機上已有的同步服務完成。");
        put(EN,"日历权限已开启 · 管理权限","Calendar permission on · Manage"); put(TW,"日历权限已开启 · 管理权限","行事曆權限已開啟 · 管理權限");
        put(EN,"连接系统日历","Connect system calendar"); put(TW,"连接系统日历","連接系統行事曆");
        put(EN,"账号同步设置","Account sync settings"); put(TW,"账号同步设置","帳號同步設定");
        put(EN,"权限被拒绝？前往应用权限设置","Permission denied? Open app permissions"); put(TW,"权限被拒绝？前往应用权限设置","權限被拒絕？前往應用程式權限設定");
        put(EN,"日期显示","Date display"); put(TW,"日期显示","日期顯示");
        put(EN,"首页上半是当月原版海报插画，下半是应用自己计算的真实日期格，两者在同一页面；主题插画按月份切换，真实日历支持跨年和闰年。","The original monthly illustration sits above a real calendar grid calculated by the app. Illustrations change by month, and the calendar handles leap years and year changes.");
        put(TW,"首页上半是当月原版海报插画，下半是应用自己计算的真实日期格，两者在同一页面；主题插画按月份切换，真实日历支持跨年和闰年。","首頁上半是當月原版海報插畫，下半是應用程式自行計算的真實日期格；主題插畫按月份切換，真實行事曆支援跨年與閏年。");
        put(EN,"每周从周一开始","Start weeks on Monday"); put(TW,"每周从周一开始","每週從週一開始");
        put(EN,"开屏动画","Splash animation"); put(TW,"开屏动画","開屏動畫");
        put(EN,"每次从图标启动应用时，随机展示所选素材的角色插画和寄语；原海报下方的月份日历、标题与印刷文字不会出现在开屏。","When the app starts from its icon, it randomly shows a selected character illustration and quote. The printed calendar, title and poster text are left out.");
        put(TW,"每次从图标启动应用时，随机展示所选素材的角色插画和寄语；原海报下方的月份日历、标题与印刷文字不会出现在开屏。","每次從圖示啟動應用程式時，隨機展示所選素材的角色插畫與寄語；原海報下方的月份行事曆、標題與印刷文字不會出現在開屏。");
        put(EN,"本地日程提醒","Local event reminders"); put(TW,"本地日程提醒","本機行程提醒");
        put(EN,"系统日程的提醒由手机日历服务发送。本地日程的通知与精确提醒状态如下；厂商省电限制仍可能影响后台提醒。","System event reminders are sent by the phone calendar service. The local notification and exact-alarm status is shown below; battery-saving limits may still affect background reminders.");
        put(TW,"系统日程的提醒由手机日历服务发送。本地日程的通知与精确提醒状态如下；厂商省电限制仍可能影响后台提醒。","系統行程提醒由手機行事曆服務傳送。本機通知與精確提醒狀態如下；製造商的省電限制仍可能影響背景提醒。");
        put(EN,"通知已允许 · 查看设置","Notifications allowed · View settings"); put(TW,"通知已允许 · 查看设置","通知已允許 · 檢視設定");
        put(EN,"允许提醒通知","Allow reminder notifications"); put(TW,"允许提醒通知","允許提醒通知");
        put(EN,"精确提醒已允许","Exact alarms allowed"); put(TW,"精确提醒已允许","精確提醒已允許");
        put(EN,"开启精确提醒（当前可能延迟）","Enable exact alarms (may be delayed)"); put(TW,"开启精确提醒（当前可能延迟）","開啟精確提醒（目前可能延遲）");
        put(EN,"当前系统支持精确提醒","This system supports exact alarms"); put(TW,"当前系统支持精确提醒","目前系統支援精確提醒");
        put(EN,"桌面小组件","Home-screen widgets"); put(TW,"桌面小组件","桌面小工具");
        put(EN,"也可以长按手机桌面 → 小组件 → 翁法罗斯月历。添加后可长按调整尺寸。卡片可配置是否展示日程。","Long-press your home screen → Widgets → Amphoreus Calendar. After adding one, long-press to resize it. Each card can choose whether to show events.");
        put(TW,"也可以长按手机桌面 → 小组件 → 翁法罗斯月历。添加后可长按调整尺寸。卡片可配置是否展示日程。","也可以長按手機桌面 → 小工具 → 翁法羅斯行事曆。加入後可長按調整尺寸。卡片可設定是否顯示行程。");
        put(EN,"添加主题月历卡片","Add theme calendar card"); put(TW,"添加主题月历卡片","加入主題行事曆卡片");
        put(EN,"添加今日安排卡片","Add today's agenda card"); put(TW,"添加今日安排卡片","加入今日安排卡片");
        put(EN,"添加图文月历卡片","Add split calendar card"); put(TW,"添加图文月历卡片","加入圖文行事曆卡片");
        put(EN,"关于","About"); put(TW,"关于","關於");
        put(EN,"翁法罗斯月历 1.0.0\n离线图片 · 本地日程 · 系统日历 · ICS 订阅\n原图中的署名与版权信息保留在完整海报中。\n本应用不包含广告、统计服务或账号登录。","Amphoreus Calendar 1.0.0\nOffline art · Local events · System calendar · ICS feeds\nCredits and copyright remain in the complete posters.\nNo ads, analytics or account login.");
        put(TW,"翁法罗斯月历 1.0.0\n离线图片 · 本地日程 · 系统日历 · ICS 订阅\n原图中的署名与版权信息保留在完整海报中。\n本应用不包含广告、统计服务或账号登录。","翁法羅斯行事曆 1.0.0\n離線圖片 · 本機行程 · 系統行事曆 · ICS 訂閱\n原圖中的署名與版權資訊保留在完整海報中。\n本應用程式不包含廣告、統計服務或帳號登入。");

        put(EN,"语言与外观","Language & appearance"); put(TW,"语言与外观","語言與外觀");
        put(EN,"选择应用语言，并设置浅色、深色或按时间自动切换的颜色。","Choose the app language and use light, dark, or time-based colors.");
        put(TW,"选择应用语言，并设置浅色、深色或按时间自动切换的颜色。","選擇應用程式語言，並設定淺色、深色或按時間自動切換的顏色。");
        put(EN,"跟随系统","Follow system"); put(TW,"跟随系统","跟隨系統");
        put(EN,"简体中文","Simplified Chinese"); put(TW,"简体中文","簡體中文");
        put(EN,"繁體中文","Traditional Chinese"); put(TW,"繁體中文","繁體中文");
        put(EN,"按时间自动切换颜色（白天浅色，晚上深色）","Switch colors by time (light by day, dark at night)"); put(TW,"按时间自动切换颜色（白天浅色，晚上深色）","按時間自動切換顏色（白天淺色，晚上深色）");
        put(EN,"颜色：按时间自动（白天浅色 / 晚上深色）","Colors: automatic by time (light day / dark night)"); put(TW,"颜色：按时间自动（白天浅色 / 晚上深色）","顏色：按時間自動（白天淺色 / 晚上深色）");
        put(EN,"颜色：跟随系统","Colors: follow system"); put(TW,"颜色：跟随系统","顏色：跟隨系統");
        put(EN,"颜色：浅色","Colors: light"); put(TW,"颜色：浅色","顏色：淺色");
        put(EN,"颜色：深色","Colors: dark"); put(TW,"颜色：深色","顏色：深色");
        put(EN,"语言：跟随系统","Language: follow system"); put(TW,"语言：跟随系统","語言：跟隨系統");
        put(EN,"语言：简体中文","Language: Simplified Chinese"); put(TW,"语言：简体中文","語言：簡體中文");
        put(EN,"语言：繁體中文","Language: Traditional Chinese"); put(TW,"语言：繁體中文","語言：繁體中文");
        put(EN,"语言：English","Language: English"); put(TW,"语言：English","語言：English");
        put(EN,"浅色","Light"); put(TW,"浅色","淺色");
        put(EN,"深色","Dark"); put(TW,"深色","深色");

        put(EN,"年历封面","Annual cover"); put(TW,"年历封面","年曆封面");
        put(EN,"门关月","Gate Month"); put(TW,"门关月","門關月");
        put(EN,"平衡月","Balance Month"); put(TW,"平衡月","平衡月");
        put(EN,"长夜月","Long Night Month"); put(TW,"长夜月","長夜月");
        put(EN,"耕耘月","Cultivation Month"); put(TW,"耕耘月","耕耘月");
        put(EN,"欢喜月","Joy Month"); put(TW,"欢喜月","歡喜月");
        put(EN,"长昼月","Long Day Month"); put(TW,"长昼月","長晝月");
        put(EN,"自由月","Freedom Month"); put(TW,"自由月","自由月");
        put(EN,"收获月","Harvest Month"); put(TW,"收获月","收穫月");
        put(EN,"拾线月","Threadpick Month"); put(TW,"拾线月","拾線月");
        put(EN,"纷争月","Strife Month"); put(TW,"纷争月","紛爭月");
        put(EN,"哀悼月","Mourning Month"); put(TW,"哀悼月","哀悼月");
        put(EN,"机缘月","Fortune Month"); put(TW,"机缘月","機緣月");
        put(EN,"昔涟","Xilian"); put(TW,"昔涟","昔漣");

        put(EN,"翁法罗斯","Amphoreus"); put(TW,"翁法罗斯","翁法羅斯");
        put(EN,"今天","Today"); put(TW,"今天","今天");
        put(EN,"今日安排","Today's agenda"); put(TW,"今日安排","今日安排");
        put(EN,"今天暂无安排","No plans today"); put(TW,"今天暂无安排","今天暫無安排");
        put(EN,"日程","Agenda"); put(TW,"日程","行程");
        put(EN,"月历","Calendar"); put(TW,"月历","月曆");
        put(EN,"把日子，留给重要的事。","Leave the days for what matters."); put(TW,"把日子，留给重要的事。","把日子，留給重要的事。");
        put(EN,"＋ 新建","＋ New"); put(TW,"＋ 新建","＋ 新增");
        put(EN,"当天","Today"); put(TW,"当天","當天");
        put(EN,"本周","This week"); put(TW,"本周","本週");
        put(EN,"未来 30 天","Next 30 days"); put(TW,"未来 30 天","未來 30 天");
        put(EN,"搜索当前范围：标题、地点、备注","Search this range: title, location, notes"); put(TW,"搜索当前范围：标题、地点、备注","搜尋目前範圍：標題、地點、備註");
        put(EN,"正在读取日程…","Loading events…"); put(TW,"正在读取日程…","正在讀取行程…");
        put(EN,"暂无日程\n给这一天，留一点期待。","No events\nLeave a little room for anticipation."); put(TW,"暂无日程\n给这一天，留一点期待。","暫無行程\n給這一天，留一點期待。");
        put(EN,"此范围内没有匹配日程","No matching events in this range"); put(TW,"此范围内没有匹配日程","此範圍內沒有符合的行程");

        put(EN,"桌面卡片","Home-screen card"); put(TW,"桌面卡片","桌面卡片");
        put(EN,"让每一天，都有插画相伴。","Let an illustration accompany every day."); put(TW,"让每一天，都有插画相伴。","讓每一天，都有插畫相伴。");
        put(EN,"在此卡片展示日程标题与日期标记","Show event titles and date markers on this card"); put(TW,"在此卡片展示日程标题与日期标记","在此卡片顯示行程標題與日期標記");
        put(EN,"打开后，手机桌面可直接看到你的日程。\n月历卡片默认跟随当前月份，也可以单独翻月。","After enabling, your events appear directly on the home screen.\nCalendar cards follow the current month by default, and can also be changed independently.");
        put(TW,"打开后，手机桌面可直接看到你的日程。\n月历卡片默认跟随当前月份，也可以单独翻月。","開啟後，手機桌面可直接看到你的行程。\n月曆卡片預設跟隨目前月份，也可以單獨切換月份。");
        put(EN,"保存卡片","Save card"); put(TW,"保存卡片","儲存卡片");
        put(EN,"图文月历","Split calendar"); put(TW,"图文月历","圖文月曆");
        put(EN,"日历读取失败 · 点击打开应用重试","Calendar read failed · Tap to retry"); put(TW,"日历读取失败 · 点击打开应用重试","行事曆讀取失敗 · 點選開啟應用程式重試");
        put(EN,"全天","All day"); put(TW,"全天","全天");
        put(EN,"未来 30 天暂无安排","No events in the next 30 days"); put(TW,"未来 30 天暂无安排","未來 30 天沒有安排");
        put(EN,"日程内容已隐藏","Event details hidden"); put(TW,"日程内容已隐藏","行程內容已隱藏");
        put(EN,"给日子留一点期待","Leave a little room for anticipation"); put(TW,"给日子留一点期待","給日子留一點期待");
        put(EN,"点击齿轮，选择展示日程","Tap the gear to show events"); put(TW,"点击齿轮，选择展示日程","點選齒輪，選擇顯示行程");
        put(EN,"近期安排","Upcoming"); put(TW,"近期安排","近期安排");
        put(EN,"更新","updated"); put(TW,"更新","更新");
        put(EN,"• 有日程  ·  点击日期查看","• Events · Tap a date to view"); put(TW,"• 有日程  ·  点击日期查看","• 有行程 · 點選日期檢視");
        put(EN,"点击日期查看 · 齿轮配置日程显示","Tap a date · Use the gear to show events"); put(TW,"点击日期查看 · 齿轮配置日程显示","點選日期檢視 · 使用齒輪設定顯示行程");
        put(EN,"今天的全天日程","Today's all-day event"); put(TW,"今天的全天日程","今天的全天行程");
        put(EN,"你的日程即将开始","Your event is about to start"); put(TW,"你的日程即将开始","你的行程即將開始");
        put(EN,"角色提醒","Character reminder"); put(TW,"角色提醒","角色提醒");
        put(EN,"发送通知预览","Send notification preview"); put(TW,"发送通知预览","傳送通知預覽");
        put(EN,"通知预览已发送","Notification preview sent"); put(TW,"通知预览已发送","通知預覽已傳送");
        put(EN,"系统通知已关闭，请在设置中打开","System notifications are off; enable them in Settings"); put(TW,"系统通知已关闭，请在设置中打开","系統通知已關閉，請在設定中開啟");
        put(EN,"日程已保存，但系统通知已关闭，提醒不会弹出","Event saved, but system notifications are off, so the reminder will not appear"); put(TW,"日程已保存，但系统通知已关闭，提醒不会弹出","行程已儲存，但系統通知已關閉，提醒不會跳出");
        put(EN,"日程已保存，提醒通知已允许","Event saved; reminder notifications are allowed"); put(TW,"日程已保存，提醒通知已允许","行程已儲存，提醒通知已允許");
        put(EN,"日程已保存，但未允许通知，提醒不会弹出","Event saved, but notifications were not allowed, so the reminder will not appear"); put(TW,"日程已保存，但未允许通知，提醒不会弹出","行程已儲存，但未允許通知，提醒不會跳出");
        put(EN,"角色提醒图片仅支持本地日程，请改选本地日历","Character reminder images are available only for local events; choose Local calendar"); put(TW,"角色提醒图片仅支持本地日程，请改选本地日历","角色提醒圖片僅支援本機行程，請改選本機行事曆");
        put(EN,"系统日历提醒","System calendar reminder"); put(TW,"系统日历提醒","系統行事曆提醒");
        put(EN,"应用内提醒","In-app reminder"); put(TW,"应用内提醒","應用程式內提醒");
        put(EN,"由手机日历服务发送；关闭这里不会影响本应用的独立提醒。","Sent by the phone calendar service; turning this off does not affect the app's independent reminder."); put(TW,"由手机日历服务发送；关闭这里不会影响本应用的独立提醒。","由手機行事曆服務傳送；關閉此項不會影響應用程式的獨立提醒。");
        put(EN,"由本应用独立发送，可用于节日、只读日历和手机日历中的其他事件。","Sent independently by this app for festivals, read-only calendars, and other phone-calendar events."); put(TW,"由本应用独立发送，可用于节日、只读日历和手机日历中的其他事件。","由本應用程式獨立傳送，可用於節日、唯讀行事曆及手機行事曆中的其他事件。");
        put(EN,"选择角色图片后，请先选择应用内提醒时间","Choose an in-app reminder time before selecting a character image"); put(TW,"选择角色图片后，请先选择应用内提醒时间","選擇角色圖片後，請先選擇應用程式內提醒時間");
        put(EN,"保存应用提醒","Save app reminder"); put(TW,"保存应用提醒","儲存應用程式提醒");
        put(EN,"应用提醒已保存","App reminder saved"); put(TW,"应用提醒已保存","應用程式提醒已儲存");
        put(EN,"应用提醒已保存，但系统通知已关闭，提醒不会弹出","App reminder saved, but system notifications are off, so it will not appear"); put(TW,"应用提醒已保存，但系统通知已关闭，提醒不会弹出","應用程式提醒已儲存，但系統通知已關閉，提醒不會跳出");
        put(EN,"新建日程","New event"); put(TW,"新建日程","新增行程");
        put(EN,"日程详情","Event details"); put(TW,"日程详情","行程詳情");
        put(EN,"正在读取…","Loading…"); put(TW,"正在读取…","正在讀取…");
        put(EN,"标题","Title"); put(TW,"标题","標題");
        put(EN,"给这件事取个名字","Give this event a name"); put(TW,"给这件事取个名字","為這件事取個名字");
        put(EN,"所属日历","Calendar"); put(TW,"所属日历","所屬行事曆");
        put(EN,"全天日程","All-day event"); put(TW,"全天日程","全天行程");
        put(EN,"开始","Start"); put(TW,"开始","開始");
        put(EN,"结束（全天事件包含所选结束日）","End (all-day events include the selected end date)"); put(TW,"结束（全天事件包含所选结束日）","結束（全天行程包含選取的結束日期）");
        put(EN,"时区：","Time zone: "); put(TW,"时区：","時區：");
        put(EN,"重复","Repeat"); put(TW,"重复","重複");
        put(EN,"不重复","Does not repeat"); put(TW,"不重复","不重複");
        put(EN,"每天","Every day"); put(TW,"每天","每天");
        put(EN,"每周","Every week"); put(TW,"每周","每週");
        put(EN,"每月","Every month"); put(TW,"每月","每月");
        put(EN,"每年","Every year"); put(TW,"每年","每年");
        put(EN,"提前提醒","Reminder"); put(TW,"提前提醒","提前提醒");
        put(EN,"提醒图片","Reminder image"); put(TW,"提醒图片","提醒圖片");
        put(EN,"选择提醒图片","Choose reminder image"); put(TW,"选择提醒图片","選擇提醒圖片");
        put(EN,"随机角色图片","Random character image"); put(TW,"随机角色图片","隨機角色圖片");
        put(EN,"不使用角色图片","No character image"); put(TW,"不使用角色图片","不使用角色圖片");
        put(EN,"只对本地日程通知使用；系统日历自己的提醒由系统日历服务控制。","Used for local event notifications only; system-calendar reminders are controlled by the system calendar service.");
        put(TW,"只对本地日程通知使用；系统日历自己的提醒由系统日历服务控制。","僅用於本機行程通知；系統行事曆提醒由系統行事曆服務控制。");
        put(EN,"不提醒","No reminder"); put(TW,"不提醒","不提醒");
        put(EN,"日程开始时","At event start"); put(TW,"日程开始时","行程開始時");
        put(EN,"提前 1 天","1 day before"); put(TW,"提前 1 天","提前 1 天");
        put(EN,"提前 3 天","3 days before"); put(TW,"提前 3 天","提前 3 天");
        put(EN,"地点","Location"); put(TW,"地点","地點");
        put(EN,"地点（选填）","Location (optional)"); put(TW,"地点（选填）","地點（選填）");
        put(EN,"备注","Notes"); put(TW,"备注","備註");
        put(EN,"想记住的细节（选填）","Details to remember (optional)"); put(TW,"想记住的细节（选填）","想記住的細節（選填）");
        put(EN,"保存日程","Save event"); put(TW,"保存日程","儲存行程");
        put(EN,"正在保存…","Saving…"); put(TW,"正在保存…","正在儲存…");
        put(EN,"删除日程","Delete event"); put(TW,"删除日程","刪除行程");
        put(EN,"删除整个重复系列","Delete entire series"); put(TW,"删除整个重复系列","刪除整個重複系列");
        put(EN,"在系统日历中打开","Open in system calendar"); put(TW,"在系统日历中打开","在系統行事曆中開啟");
    }

    private static void put(Map<String,String> map,String key,String value) { map.put(key,value); }

    public static String t(Context context,String value) {
        if(value==null||value.isEmpty())return value;
        Map<String,String> map=english(context)?EN:traditional(context)?TW:null;
        if(map==null)return value;
        String translated=map.get(value);
        return translated==null?value:translated;
    }

    public static Locale locale(Context context) { return AppSettings.locale(context); }

    public static String[] languageLabels(Context context) {
        if(english(context))return new String[]{"Follow system","简体中文","繁體中文","English"};
        if(traditional(context))return new String[]{"跟隨系統","簡體中文","繁體中文","English"};
        return new String[]{"跟随系统","简体中文","繁體中文","English"};
    }

    public static String[] themeLabels(Context context) {
        if(english(context))return new String[]{"Follow system","Light","Dark"};
        if(traditional(context))return new String[]{"跟隨系統","淺色","深色"};
        return new String[]{"跟随系统","浅色","深色"};
    }

    public static String[] weekdays(Context context,boolean monday) {
        if(english(context)) {
            return monday?new String[]{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"}:new String[]{"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        }
        return monday?new String[]{"一","二","三","四","五","六","日"}:new String[]{"日","一","二","三","四","五","六"};
    }

    public static String splashLabel(Context context,int index) {
        if(english(context)) return index==0?"Cover · "+t(context,"昔涟"):index+" · "+t(context,Art.NAMES[index]);
        return (index==0?t(context,"年历封面"):index+"月")+" · "+t(context,index==0?"昔涟":Art.NAMES[index]);
    }

    public static boolean isEnglish(Context context) { return english(context); }
    public static boolean isTraditional(Context context) { return traditional(context); }
    private static boolean english(Context context) { return AppSettings.locale(context).getLanguage().equals("en"); }
    private static boolean traditional(Context context) { return AppSettings.locale(context).getLanguage().equals("zh") && AppSettings.locale(context).getCountry().equalsIgnoreCase("TW"); }

    private LocaleText() {}
}
