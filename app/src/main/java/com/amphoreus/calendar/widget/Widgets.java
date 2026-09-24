package com.amphoreus.calendar.widget;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;
import com.amphoreus.calendar.*;
import com.amphoreus.calendar.core.*;
import com.amphoreus.calendar.data.EventRepository;
import com.amphoreus.calendar.ui.*;
import java.time.*;
import java.util.*;

public final class Widgets {
    // 卡片尺寸的账全部在 core.WidgetBudget 里算（那里解释了为什么日期区必须是固定高度块），
    // 本类只负责把预算变成位图与 RemoteViews。
    public static boolean owned(Context c,int id) { AppWidgetProviderInfo info=AppWidgetManager.getInstance(c).getAppWidgetInfo(id); return info!=null && info.provider.getPackageName().equals(c.getPackageName()); }
    public static YearMonth month(Context c,int id) {
        SharedPreferences p=c.getSharedPreferences("widgets",0); if(p.getBoolean("follow_"+id,true))return YearMonth.now();
        try { return YearMonth.parse(p.getString("month_"+id,YearMonth.now().toString())); } catch(Exception e) { return YearMonth.now(); }
    }
    public static void updateAll(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c); EventRepository repository=((CalendarApp)c.getApplicationContext()).repository();
        for(int id:manager.getAppWidgetIds(new ComponentName(c,MonthWidget.class))) update(c,manager,repository,id,false);
        for(int id:manager.getAppWidgetIds(new ComponentName(c,AgendaWidget.class))) update(c,manager,repository,id,true);
        for(int id:manager.getAppWidgetIds(new ComponentName(c,SplitMonthWidget.class))) updateSplit(c,manager,repository,id);
    }
    private static void update(Context c,AppWidgetManager manager,EventRepository repository,int id,boolean agenda) {
        try { manager.updateAppWidget(id,render(c,manager,repository,id,agenda)); }
        catch(Exception e) {
            android.util.Log.e("CalendarWidget","Widget update failed",e);
            RemoteViews error=new RemoteViews(c.getPackageName(),agenda?R.layout.widget_agenda:R.layout.widget_month);
            error.setTextViewText(R.id.widget_title,LocaleText.t(c,"翁法罗斯")+" "+LocaleText.t(c,"月历")); error.setTextViewText(R.id.widget_footer,LocaleText.t(c,"日历读取失败 · 点击打开应用重试"));
            error.setOnClickPendingIntent(R.id.widget_root,openDate(c,LocalDate.now())); manager.updateAppWidget(id,error);
        }
    }
    private static void updateSplit(Context c,AppWidgetManager manager,EventRepository repository,int id) {
        try { manager.updateAppWidget(id,renderSplit(c,manager,repository,id,manager.getAppWidgetOptions(id))); }
        catch(Exception e) {
            android.util.Log.e("CalendarWidget","Split widget update failed",e);
            RemoteViews error=new RemoteViews(c.getPackageName(),R.layout.widget_split);
            error.setTextViewText(R.id.split_title,LocaleText.t(c,"图文月历")); error.setTextViewText(R.id.split_footer,LocaleText.t(c,"日历读取失败 · 点击打开应用重试"));
            error.setOnClickPendingIntent(R.id.split_root,openDate(c,LocalDate.now())); manager.updateAppWidget(id,error);
        }
    }
    public static RemoteViews render(Context c,AppWidgetManager manager,EventRepository repository,int id,boolean agenda) {
        return render(c,manager,repository,id,agenda,manager.getAppWidgetOptions(id));
    }
    /** 带尺寸的重载：设备测试用它注入假想的卡片尺寸，生产路径由 {@link #render} 传启动器上报的值。 */
    public static RemoteViews render(Context c,AppWidgetManager manager,EventRepository repository,int id,boolean agenda,Bundle options) {
        YearMonth month=agenda?YearMonth.now():month(c,id); LocalDate today=LocalDate.now();
        // 未配置过的卡片默认显示日程；只有用户明确关闭后才保持隐藏。
        boolean showEvents=c.getSharedPreferences("widgets",0).getBoolean("events_"+id,true);
        RemoteViews views=new RemoteViews(c.getPackageName(),agenda?R.layout.widget_agenda:R.layout.widget_month);
        // 卡片跟随当月主题色，与应用内页面同一套取值（过去两张卡片写死金色，和页面不属于同一个应用）。
        int accent=Ui.accent(month.getMonthValue());
        float density=c.getResources().getDisplayMetrics().density;
        if(agenda) {
            int innerWidth=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250)-WidgetBudget.AGENDA_PADDING_DP;
            int innerHeight=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,130)-WidgetBudget.AGENDA_PADDING_DP;
            WidgetBudget.Plan plan=WidgetBudget.agendaCard(Math.max(0,innerWidth),Math.max(0,innerHeight),Art.aspect(month.getMonthValue()));
            // 议程卡按图片栏的真实宽高取景，并将窗口对准本月角色焦点；这样角色不会贴在
            // 图片栏与日程栏的分界线上再被二次裁切。
            views.setImageViewBitmap(R.id.widget_art,Art.agendaPoster(c,month.getMonthValue(),
                Math.max(1,Math.round(plan.artWidth*density)),Math.max(1,Math.round(plan.artHeight*density))));
            views.setTextColor(R.id.widget_title,accent);
            views.setTextColor(R.id.widget_config,Ui.MUTED);
            views.setTextColor(R.id.widget_add,accent);
            views.setTextColor(R.id.widget_footer,Ui.MUTED);
        } else {
            // 日期区是固定高度块，插画块 weight=1 吃掉剩余高度（见 WidgetBudget）。
            // 位图是整幅插画按自身比例缩放；控件 centerCrop 按真实卡片尺寸居中裁，比例永不变形。
            int innerWidth=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250)-WidgetBudget.MONTH_PADDING_DP;
            int innerHeight=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,320)-WidgetBudget.MONTH_PADDING_DP;
            WidgetBudget.Plan plan=WidgetBudget.monthCard(Math.max(0,innerWidth),Math.max(0,innerHeight),Art.aspect(month.getMonthValue()));
            // 位图的宽按块宽给（与真实控件同宽），高由插画比例决定；控件 centerCrop 居中裁。
            views.setImageViewBitmap(R.id.widget_art,Art.poster(c,month.getMonthValue(),Math.round(plan.artWidth*density)));
            views.setTextColor(R.id.widget_title,accent);
            views.setTextColor(R.id.widget_previous,accent);
            views.setTextColor(R.id.widget_next,accent);
            views.setTextColor(R.id.widget_config,Ui.MUTED);
            views.setTextColor(R.id.widget_footer,Ui.MUTED);
            views.setTextColor(R.id.widget_today_label,accent);
            views.setOnClickPendingIntent(R.id.widget_today,openDate(c,today));
        }
        views.setTextViewText(R.id.widget_title,agenda?today.getMonthValue()+"月"+today.getDayOfMonth()+"日 · "+LocaleText.t(c,Art.NAMES[month.getMonthValue()]):Art.monthLabel(c,month.getMonthValue(),month.getYear()));
        // 月历卡：点月名回到本月（旧版点的是单独一行上的 widget_month_label，那行已合并掉）。
        views.setOnClickPendingIntent(R.id.widget_title,agenda?openDate(c,today):todayBroadcast(c,id,month));
        Intent config=new Intent(c,WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("amphoreus://widget/config/"+id));
        views.setOnClickPendingIntent(R.id.widget_config,PendingIntent.getActivity(c,id,config,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        if(agenda) {
            views.setOnClickPendingIntent(R.id.widget_add,PendingIntent.getActivity(c,id,new Intent(c,EventActivity.class).putExtra("date",today.toString()).setData(Uri.parse("amphoreus://widget/new/"+id)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            views.removeAllViews(R.id.widget_events);
            List<Occurrence> events=showEvents?repository.between(today,today.plusDays(30)):Collections.emptyList();
            int height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,180);
            // 卡片被拉高后，安排列表按高度增加容量，最多展示六项；小尺寸仍保留至少一项。
            int limit=height<180?1:height<250?2:height<320?3:height<390?4:height<460?5:6;
            int count=0; for(Occurrence o:events) {
                if(!o.event.allDay && o.end<=System.currentTimeMillis())continue;
                RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_event);
                row.setTextViewText(R.id.widget_event_title,o.event.title);
                row.setTextColor(R.id.widget_event_title,Ui.TEXT);
                row.setTextColor(R.id.widget_event_time,accent);
                row.setTextViewText(R.id.widget_event_time,(o.firstDate(ZoneId.systemDefault()).equals(today)?LocaleText.t(c,"今天"):Ui.date(o.firstDate(ZoneId.systemDefault())))+" · "+(o.event.allDay?LocaleText.t(c,"全天"):Ui.time(o.start,ZoneId.systemDefault())));
                row.setOnClickPendingIntent(R.id.widget_event_row,PendingIntent.getActivity(c,0,new Intent(c,EventActivity.class).putExtra("key",o.event.key()).setData(Uri.parse("amphoreus://event/"+o.event.key())),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
                views.addView(R.id.widget_events,row); if(++count==limit)break;
            }
            if(count==0) { RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_event); row.setTextViewText(R.id.widget_event_title,showEvents?LocaleText.t(c,"未来 30 天暂无安排"):LocaleText.t(c,"日程内容已隐藏")); row.setTextColor(R.id.widget_event_title,Ui.TEXT); row.setTextColor(R.id.widget_event_time,accent); row.setTextViewText(R.id.widget_event_time,showEvents?LocaleText.t(c,"给日子留一点期待"):LocaleText.t(c,"点击齿轮，选择展示日程")); row.setOnClickPendingIntent(R.id.widget_event_row,openDate(c,today)); views.addView(R.id.widget_events,row); }
            views.setTextViewText(R.id.widget_footer,LocaleText.t(c,"近期安排")+" · "+Ui.time(System.currentTimeMillis(),ZoneId.systemDefault())+" "+LocaleText.t(c,"更新"));
        } else {
            // 「回到本月」在这一版落在月名上（widget_title，见上面的 todayBroadcast）；这里只接翻月。
            String[] suffix={"PREV","NEXT"}; int[] actions={R.id.widget_previous,R.id.widget_next};
            for(int i=0;i<suffix.length;i++) {
                Intent intent=new Intent(c,MonthWidget.class).setAction("com.amphoreus.calendar.MONTH_"+suffix[i]).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("amphoreus://widget/"+id+"/"+suffix[i]));
                views.setOnClickPendingIntent(actions[i],PendingIntent.getBroadcast(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            }
            boolean monday=c.getSharedPreferences("settings",0).getBoolean("monday",true);
            String[] weekdays=LocaleText.weekdays(c,monday);
            views.removeAllViews(R.id.widget_weekdays); for(String weekday:weekdays) { RemoteViews day=new RemoteViews(c.getPackageName(),R.layout.widget_weekday); day.setTextViewText(R.id.widget_weekday,weekday); day.setTextColor(R.id.widget_weekday,accent); views.addView(R.id.widget_weekdays,day); }
            List<LocalDate> grid=CalendarMath.grid(month,monday); List<Occurrence> events=showEvents?repository.between(grid.get(0),grid.get(0).plusDays(42)):Collections.emptyList();
            views.removeAllViews(R.id.widget_grid);
            for(int r=0;r<6;r++) { RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_row);
                for(int col=0;col<7;col++) {
                    LocalDate date=grid.get(r*7+col); RemoteViews day=new RemoteViews(c.getPackageName(),R.layout.widget_day);
                    day.setTextViewText(R.id.widget_day,String.valueOf(date.getDayOfMonth()));
                    boolean inMonth=YearMonth.from(date).equals(month),highlighted=date.equals(today)&&inMonth;
                    int numberColor=inMonth?Ui.TEXT:Ui.DIM;
                    styleDayCell(day,R.id.widget_cell,R.id.widget_day,R.id.widget_dot,highlighted,numberColor,accent);
                    boolean has=false; for(Occurrence o:events)if(o.onDate(date,ZoneId.systemDefault())) { has=true; break; }
                    day.setTextViewText(R.id.widget_dot,has?"•":""); day.setContentDescription(R.id.widget_cell,Ui.date(date)+(date.equals(today)?"，今天":"")+(has?"，有日程":""));
                    day.setOnClickPendingIntent(R.id.widget_cell,openDate(c,date)); row.addView(R.id.widget_row,day);
                } views.addView(R.id.widget_grid,row);
            }
            views.setTextViewText(R.id.widget_footer,showEvents?LocaleText.t(c,"• 有日程  ·  点击日期查看"):LocaleText.t(c,"点击日期查看 · 齿轮配置日程显示"));
            renderToday(c,views,repository,today,showEvents,accent);
        }
        views.setOnClickPendingIntent(R.id.widget_footer,openDate(c,today)); return views;
    }
    /** Render the extra sixth-row summary without disturbing the original month grid above it. */
    private static void renderToday(Context c,RemoteViews views,EventRepository repository,LocalDate today,boolean showEvents,int accent) {
        views.setTextViewText(R.id.widget_today_label,LocaleText.t(c,"今日安排"));
        views.removeAllViews(R.id.widget_today_events);
        if(!showEvents) {
            addTodayRow(c,views,LocaleText.t(c,"日程内容已隐藏"),"",accent);
            return;
        }
        ZoneId zone=ZoneId.systemDefault(); long now=System.currentTimeMillis(); List<Occurrence> todayEvents=new ArrayList<>();
        for(Occurrence occurrence:repository.between(today,today.plusDays(1))) {
            if(!occurrence.onDate(today,zone))continue;
            if(!occurrence.event.allDay&&occurrence.end<=now)continue;
            todayEvents.add(occurrence);
        }
        if(todayEvents.isEmpty()) { addTodayRow(c,views,LocaleText.t(c,"今天暂无安排"),"",accent); return; }
        int visible=Math.min(2,todayEvents.size());
        for(int i=0;i<visible;i++) {
            Occurrence occurrence=todayEvents.get(i);
            addTodayRow(c,views,occurrence.event.title,occurrence.event.allDay?LocaleText.t(c,"全天"):Ui.time(occurrence.start,zone),accent);
        }
        if(todayEvents.size()>2) {
            int remaining=todayEvents.size()-1;
            String more=LocaleText.isEnglish(c)?remaining+" more":LocaleText.isTraditional(c)?"還有 "+remaining+" 項安排":"还有 "+remaining+" 项安排";
            // 只保留一条事件加一条汇总，避免 44dp 行被拆成过细的三行。
            views.removeAllViews(R.id.widget_today_events);
            Occurrence first=todayEvents.get(0);
            addTodayRow(c,views,first.event.title,first.event.allDay?LocaleText.t(c,"全天"):Ui.time(first.start,zone),accent);
            addTodayRow(c,views,more,LocaleText.t(c,"点击日期查看"),accent);
        }
    }
    private static void addTodayRow(Context c,RemoteViews views,String title,String time,int accent) {
        RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_today_event);
        row.setTextViewText(R.id.widget_today_event_title,title); row.setTextColor(R.id.widget_today_event_title,Ui.TEXT);
        row.setTextViewText(R.id.widget_today_event_time,time); row.setTextColor(R.id.widget_today_event_time,accent);
        views.addView(R.id.widget_today_events,row);
    }
    /**
     * 两列图文月历：左侧保留当月角色插画，右侧放紧凑的六行真实月历。
     * 这是独立的第三种卡片，和主题月历/今日安排的月份状态互不干扰。
     */
    public static RemoteViews renderSplit(Context c,AppWidgetManager manager,EventRepository repository,int id,Bundle options) {
        YearMonth month=month(c,id); LocalDate today=LocalDate.now();
        boolean showEvents=c.getSharedPreferences("widgets",0).getBoolean("events_"+id,true);
        RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget_split);
        int accent=Ui.accent(month.getMonthValue());
        float density=c.getResources().getDisplayMetrics().density;
        int innerWidth=Math.max(0,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,320)-20);
        int imageWidth=Math.max(1,innerWidth/2);
        views.setImageViewBitmap(R.id.split_art,Art.poster(c,month.getMonthValue(),Math.max(1,Math.round(imageWidth*density))));
        views.setTextColor(R.id.split_title,accent); views.setTextColor(R.id.split_previous,accent); views.setTextColor(R.id.split_next,accent);
        views.setTextColor(R.id.split_footer,Ui.MUTED);
        views.setTextViewText(R.id.split_title,Art.monthLabel(c,month.getMonthValue(),month.getYear()));
        views.setOnClickPendingIntent(R.id.split_title,splitMonthBroadcast(c,id,"TODAY"));
        views.setOnClickPendingIntent(R.id.split_previous,splitMonthBroadcast(c,id,"PREV"));
        views.setOnClickPendingIntent(R.id.split_next,splitMonthBroadcast(c,id,"NEXT"));
        boolean monday=c.getSharedPreferences("settings",0).getBoolean("monday",true);
        String[] weekdays=LocaleText.weekdays(c,monday);
        views.removeAllViews(R.id.split_weekdays);
        for(String weekday:weekdays) { RemoteViews day=new RemoteViews(c.getPackageName(),R.layout.widget_weekday); day.setTextViewText(R.id.widget_weekday,weekday); day.setTextColor(R.id.widget_weekday,accent); views.addView(R.id.split_weekdays,day); }
        List<LocalDate> grid=CalendarMath.grid(month,monday);
        List<Occurrence> events=showEvents?repository.between(grid.get(0),grid.get(0).plusDays(42)):Collections.emptyList();
        views.removeAllViews(R.id.split_grid);
        for(int r=0;r<6;r++) {
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_row);
            for(int col=0;col<7;col++) {
                LocalDate date=grid.get(r*7+col); RemoteViews day=new RemoteViews(c.getPackageName(),R.layout.widget_split_day);
                boolean inMonth=YearMonth.from(date).equals(month),has=false;
                for(Occurrence occurrence:events) if(occurrence.onDate(date,ZoneId.systemDefault())) { has=true; break; }
                day.setTextViewText(R.id.split_day,String.valueOf(date.getDayOfMonth()));
                styleDayCell(day,R.id.split_cell,R.id.split_day,R.id.split_dot,date.equals(today)&&inMonth,inMonth?Ui.TEXT:Ui.DIM,accent);
                day.setTextViewText(R.id.split_dot,has?"•":""); day.setContentDescription(R.id.split_cell,Ui.date(date)+(date.equals(today)?"，今天":"")+(has?"，有日程":""));
                day.setOnClickPendingIntent(R.id.split_cell,openDate(c,date)); row.addView(R.id.widget_row,day);
            }
            views.addView(R.id.split_grid,row);
        }
        views.setTextViewText(R.id.split_footer,showEvents?LocaleText.t(c,"• 有日程  ·  点击日期查看"):LocaleText.t(c,"点击日期查看 · 齿轮配置日程显示"));
        views.setOnClickPendingIntent(R.id.split_footer,openDate(c,today));
        views.setOnClickPendingIntent(R.id.split_root,openDate(c,today));
        return views;
    }
    /** The home page fills the selected date; widgets have no separate selection, so today is the selected cell. */
    private static void styleDayCell(RemoteViews day,int cellId,int numberId,int dotId,boolean highlighted,int normalColor,int accent) {
        day.setInt(cellId,"setBackgroundColor",highlighted?accent:Color.TRANSPARENT);
        day.setTextColor(numberId,highlighted?Ui.BG:normalColor);
        day.setTextColor(dotId,highlighted?Ui.BG:accent);
    }
    private static PendingIntent openDate(Context c,LocalDate date) { return PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class).putExtra("date",date.toString()).setData(Uri.parse("amphoreus://date/"+date)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    /** 月历卡上点月名 = 回到本月（原 widget_month_label 的行为，那一行已合并进标题行）。 */
    private static PendingIntent todayBroadcast(Context c,int id,YearMonth month) {
        Intent intent=new Intent(c,MonthWidget.class).setAction("com.amphoreus.calendar.MONTH_TODAY").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("amphoreus://widget/"+id+"/TODAY"));
        return PendingIntent.getBroadcast(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent splitMonthBroadcast(Context c,int id,String action) {
        Intent intent=new Intent(c,SplitMonthWidget.class).setAction("com.amphoreus.calendar.SPLIT_"+action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("amphoreus://split-widget/"+id+"/"+action));
        return PendingIntent.getBroadcast(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Widgets() {}
}
