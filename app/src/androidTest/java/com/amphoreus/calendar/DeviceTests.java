package com.amphoreus.calendar;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.CalendarContract;
import android.provider.CalendarContract.*;
import android.view.*;
import android.widget.*;
import com.amphoreus.calendar.core.*;
import com.amphoreus.calendar.data.*;
import com.amphoreus.calendar.reminder.Reminders;
import com.amphoreus.calendar.ui.*;
import org.json.JSONObject;
import com.amphoreus.calendar.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;

public final class DeviceTests extends Instrumentation {
    private int assertions;
    private Context context;
    private EventRepository repository;
    private Uri calendarUri;
    private long localId;
    private void check(boolean condition,String message) { assertions++; if(!condition)throw new AssertionError(message); }
    private void log(String message) { Bundle b=new Bundle(); b.putString("stream",message+"\n"); sendStatus(0,b); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        waitForIdleSync();
        context=getTargetContext(); repository=((CalendarApp)context.getApplicationContext()).repository(); Bundle result=new Bundle(); int resultCode=Activity.RESULT_CANCELED;
        try {
            testLocal(); testSystem(); testWidgets(); testReminder(); testHomeLayout(); testSplashAnimation();
            result.putString("stream","PASS: "+assertions+" device assertions\n"); resultCode=Activity.RESULT_OK;
        } catch(Throwable e) { result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e)); }
        finally {
            if(localId!=0)repository.local.delete(localId);
            if(calendarUri!=null) context.getContentResolver().delete(calendarUri,null,null);
            Reminders.reschedule(context);
        }
        finish(resultCode,result);
    }
    private CalendarEvent event(String title,LocalDate date) {
        CalendarEvent e=new CalendarEvent(); e.title=title; e.zone=ZoneId.systemDefault().getId(); e.start=date.atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); e.end=e.start+3600000; e.reminderMinutes=10; return e;
    }
    private void testLocal() throws Exception {
        CalendarEvent e=event("Local persistence",LocalDate.of(2026,9,10)); e.reminderArt=7; localId=repository.save(e); e.id=localId;
        LocalStore reopened=new LocalStore(context); CalendarEvent reopenedEvent=reopened.get(localId); check(reopenedEvent.title.equals(e.title),"SQLite persistence after reopening"); check(reopenedEvent.reminderArt==7,"reminder artwork persists after reopening"); reopened.close();
        e.title="Local edited"; repository.save(e); check(repository.get(e.key()).title.equals("Local edited"),"local update");
        check(repository.between(LocalDate.of(2026,9,10),LocalDate.of(2026,9,11)).stream().anyMatch(o->o.event.id==localId&&!o.event.system),"local occurrence query");
        repository.delete(e); check(repository.get(e.key())==null,"local delete"); localId=0; log("PASS local CRUD and persistence");
    }
    private void testSystem() throws Exception {
        check(repository.system.canWrite(),"calendar permission granted");
        Uri calendars=Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true").appendQueryParameter(Calendars.ACCOUNT_NAME,"AmphoreusDeviceTests").appendQueryParameter(Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build();
        ContentValues cv=new ContentValues(); cv.put(Calendars.ACCOUNT_NAME,"AmphoreusDeviceTests"); cv.put(Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL); cv.put(Calendars.NAME,"AmphoreusDeviceTests"); cv.put(Calendars.CALENDAR_DISPLAY_NAME,"测试系统日历"); cv.put(Calendars.OWNER_ACCOUNT,"AmphoreusDeviceTests"); cv.put(Calendars.CALENDAR_COLOR,0xffaabbea); cv.put(Calendars.CALENDAR_ACCESS_LEVEL,Calendars.CAL_ACCESS_OWNER); cv.put(Calendars.VISIBLE,1); cv.put(Calendars.SYNC_EVENTS,1); cv.put(Calendars.MAX_REMINDERS,1); cv.put(Calendars.ALLOWED_REMINDERS,"1"); cv.put(Calendars.CALENDAR_TIME_ZONE,ZoneId.systemDefault().getId());
        Uri inserted=context.getContentResolver().insert(calendars,cv); check(inserted!=null,"scratch system calendar created"); long calendarId=ContentUris.parseId(inserted);
        calendarUri=ContentUris.withAppendedId(Calendars.CONTENT_URI,calendarId).buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true").appendQueryParameter(Calendars.ACCOUNT_NAME,"AmphoreusDeviceTests").appendQueryParameter(Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build();
        check(repository.sources().stream().anyMatch(s->s.id==calendarId&&s.writable),"source list and access level");
        CalendarEvent e=event("System write",LocalDate.of(2026,9,10)); e.system=true; e.calendarId=calendarId; e.appReminderMinutes=10; e.reminderArt=7; e.id=repository.save(e);
        CalendarEvent read=repository.get(e.key()); check(read!=null&&read.title.equals(e.title),"system insert/read"); check(read.reminderMinutes==10,"system reminder stored"); check(read.appReminderMinutes==10&&read.reminderArt==7,"independent app reminder persists for a system event"); check(!read.advanced,"simple event remains editable");
        CalendarEvent upcoming=event("System app alarm",LocalDate.now().plusDays(1)); upcoming.system=true; upcoming.calendarId=calendarId; upcoming.appReminderMinutes=0; upcoming.reminderArt=7; upcoming.id=repository.save(upcoming);
        Reminders.reschedule(context); check(context.getSharedPreferences("reminders",0).getStringSet("ids",Collections.emptySet()).contains("s:"+upcoming.id),"system event enters the independent app alarm schedule"); repository.delete(upcoming); Reminders.reschedule(context);
        ContentValues external=new ContentValues(); external.put(Events.TITLE,"External edit visible"); context.getContentResolver().update(ContentUris.withAppendedId(Events.CONTENT_URI,e.id),external,null,null);
        check(repository.get(e.key()).title.equals("External edit visible"),"external provider edit visible");
        e.title="App edit visible to provider"; repository.save(e);
        try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(Events.CONTENT_URI,e.id),new String[]{Events.TITLE},null,null,null)) { check(c.moveToFirst()&&c.getString(0).equals(e.title),"app edit visible through direct provider read"); }
        check(repository.between(LocalDate.of(2026,9,10),LocalDate.of(2026,9,11)).stream().anyMatch(o->o.event.system&&o.event.id==e.id),"system Instances query");
        repository.delete(e); check(repository.get(e.key())==null,"system delete");
        CalendarEvent repeating=event("Monthly 31",LocalDate.of(2026,1,31)); repeating.system=true; repeating.calendarId=calendarId; repeating.recurrence="FREQ=MONTHLY"; repeating.id=repository.save(repeating);
        check(repository.get(repeating.key()).end-repository.get(repeating.key()).start==3600000,"recurring duration decoded");
        long instances=repository.between(LocalDate.of(2026,1,1),LocalDate.of(2026,4,1)).stream().filter(o->o.event.system&&o.event.id==repeating.id).count(); check(instances==2,"provider monthly 31 skips February");
        repeating.recurrence=""; repository.save(repeating); check(repository.get(repeating.key()).recurrence.isEmpty(),"convert series to nonrecurring clears duration"); repository.delete(repeating);
        CalendarEvent allDay=event("All day",LocalDate.of(2026,9,10)); allDay.system=true; allDay.calendarId=calendarId; allDay.allDay=true; allDay.start=CalendarMath.midnightUtc(LocalDate.of(2026,9,10)); allDay.end=CalendarMath.midnightUtc(LocalDate.of(2026,9,12)); allDay.id=repository.save(allDay);
        List<Occurrence> allDayRows=repository.between(LocalDate.of(2026,9,11),LocalDate.of(2026,9,12)); check(allDayRows.stream().anyMatch(o->o.event.system&&o.event.id==allDay.id),"all-day spans second date");
        check(repository.between(LocalDate.of(2026,9,12),LocalDate.of(2026,9,13)).stream().noneMatch(o->o.event.system&&o.event.id==allDay.id),"all-day excludes final boundary"); repository.delete(allDay);
        CalendarEvent complex=event("Complex series",LocalDate.of(2026,9,10)); complex.system=true; complex.calendarId=calendarId; complex.recurrence="FREQ=WEEKLY"; complex.id=repository.save(complex);
        ContentValues rule=new ContentValues(); rule.put(Events.RRULE,"FREQ=WEEKLY;BYDAY=MO,WE"); context.getContentResolver().update(ContentUris.withAppendedId(Events.CONTENT_URI,complex.id),rule,null,null);
        check(repository.get(complex.key()).advanced,"complex RRULE protected"); boolean rejected=false; try { repository.save(complex); } catch(IllegalStateException ex) { rejected=true; } check(rejected,"complex RRULE not overwritten");
        context.getContentResolver().delete(ContentUris.withAppendedId(Events.CONTENT_URI,complex.id),null,null);
        log("PASS system provider CRUD, two-way visibility, repeat, all-day and complex-event protection");
    }
    private void testWidgets() throws Exception {
        context.getSharedPreferences("widgets",0).edit().putBoolean("events_901",true).putBoolean("follow_901",false).putString("month_901","2026-09").putBoolean("follow_902",false).putString("month_902","2026-12").putBoolean("follow_903",false).putString("month_903","2026-10").apply();
        check(Widgets.month(context,901).equals(YearMonth.of(2026,9)),"widget one month state"); check(Widgets.month(context,902).equals(YearMonth.of(2026,12)),"widget two independent state"); check(Widgets.month(context,903).equals(YearMonth.of(2026,10)),"split widget month state");
        // 每种卡片渲染多档尺寸，各写一张 PNG 加一个 sidecar JSON（记录插画块实测边界与预算值）。
        // tools/verify_widget.py 拿这两样去核对"插画真的是当月海报、窗口对准了主体、没有被文字压住"。
        // 4x4/4x5/4x6/4x7 对应卡片 340x370/480/560/620：预算账见 core.WidgetBudget。
        int[][] monthSizes={{340,370},{340,480},{340,560},{340,620}};
        int[][] agendaSizes={{340,170},{340,250}};
        for(int[] size:monthSizes) renderAndRecord(901,false,size,size);
        for(int[] size:agendaSizes) renderAndRecord(901,true,size,size);
        renderAndRecord(901,true,new int[]{340,170},new int[]{340,250});
        for(int[] size:new int[][]{{320,220},{360,280}}) renderSplitAndRecord(903,size);
        // 真机上启动器上报的尺寸与卡片实际占的像素经常不一致（上报 460dp 高、实际 560dp）。
        // 这一档专测"上报 != 实测"：位图按上报值生成、控件按实测值布局；月历用 centerCrop，
        // 今日安排用 fitCenter，二者都不能因为尺寸偏差而变形或把主体二次裁掉。
        // 旧验证拿同一个值当上报与实测，结构上抓不到这类问题。
        renderAndRecord(901,false,new int[]{340,460},new int[]{340,560});
        context.getSharedPreferences("widgets",0).edit().remove("events_901").remove("follow_901").remove("month_901").remove("follow_902").remove("month_902").remove("follow_903").remove("month_903").apply();
        log("PASS three RemoteViews widgets at multiple sizes and independent month state");
    }
    /**
     * 按给定尺寸渲染一张卡片：结构断言 + Binder 序列化检查 + 位图 PNG + sidecar JSON。
     *
     * @param optionSize 传给 Widgets.render 的"启动器上报尺寸"，位图按它生成
     * @param boxSize    实际测量布局用的尺寸，控件按它摆放（真机上两者常不一致）
     */
    private void renderAndRecord(int widgetId,boolean agenda,int[] optionSize,int[] boxSize) throws Exception {
        Bundle options=new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,optionSize[0]);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,optionSize[1]);
        int monthValue=Widgets.month(context,widgetId).getMonthValue();
        RemoteViews remote=Widgets.render(context,AppWidgetManager.getInstance(context),repository,widgetId,agenda,options);
        String name=(agenda?"widget-agenda":"widget-month")+"-"+boxSize[0]+"x"+boxSize[1]
            +(java.util.Arrays.equals(optionSize,boxSize)?"":"-reported"+optionSize[0]+"x"+optionSize[1]);
        float density=context.getResources().getDisplayMetrics().density;
        int width=Math.round(boxSize[0]*density),height=Math.round(boxSize[1]*density);
        // 布局的真相用实测尺寸算；位图用的上报尺寸单独算一份，用来核对两者是不是同宽高比。
        WidgetBudget.Plan plan=agenda
            ?WidgetBudget.agendaCard(boxSize[0]-WidgetBudget.AGENDA_PADDING_DP,boxSize[1]-WidgetBudget.AGENDA_PADDING_DP,Art.aspect(monthValue))
            :WidgetBudget.monthCard(boxSize[0]-WidgetBudget.MONTH_PADDING_DP,boxSize[1]-WidgetBudget.MONTH_PADDING_DP,Art.aspect(monthValue));
        // 真的走一遍 Binder 序列化：真机上 RemoteViews 就是这样交给启动器的，位图超预算会被截断
        // （进程内 apply() 不经过这一步，所以旧验证结构上看不到这个问题）。
        int parcelBytes;
        Parcel parcel=Parcel.obtain();
        try {
            remote.writeToParcel(parcel,0);
            parcelBytes=parcel.dataSize();
            check(parcelBytes<900*1024,name+" RemoteViews fits the Binder budget ("+parcelBytes+" bytes)");
            parcel.setDataPosition(0);
            RemoteViews restored=RemoteViews.CREATOR.createFromParcel(parcel);
            check(restored!=null&&restored.getLayoutId()==remote.getLayoutId(),name+" RemoteViews survives a parcel round trip");
        } finally {
            parcel.recycle();
        }
        final int recordedParcelBytes=parcelBytes;
        final Throwable[] failure={null};
        runOnMainSync(()->{ try {
            FrameLayout parent=new FrameLayout(context); View view=remote.apply(context,parent); parent.addView(view,new FrameLayout.LayoutParams(-1,-1));
            parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); parent.layout(0,0,width,height);
            ImageView art=view.findViewById(R.id.widget_art); View title=view.findViewById(R.id.widget_title);
            check(art!=null&&title!=null,"art and title views exist for "+name);
            if(!agenda) check(((TextView)title).getText().toString().startsWith(monthValue+"月"),
                "month widget title explains the numeric month for "+name+" ("+((TextView)title).getText()+")");
            // 插画块与预算一致（±2px：布局按 dp 取整的误差）。
            check(Math.abs(art.getWidth()-Math.round(plan.artWidth*density))<=2&&Math.abs(art.getHeight()-Math.round(plan.artHeight*density))<=2,
                "art block matches the budget for "+name+" ("+art.getWidth()+"x"+art.getHeight()+" vs "+Math.round(plan.artWidth*density)+"x"+Math.round(plan.artHeight*density)+")");
            // 位图必须与"上报尺寸推出的块"同宽高比、且像素数在预算内：比例不一致会被控件缩放变形，
            // 像素数超预算则过不了 Binder 事务（真机上表现为画面错位/挤压）。
            android.graphics.drawable.Drawable drawable=art.getDrawable();
            int bitmapWidth=0,bitmapHeight=0;
            if(drawable instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap drawn=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap();
                bitmapWidth=drawn.getWidth(); bitmapHeight=drawn.getHeight();
                int bitmapPixels=bitmapWidth*bitmapHeight;
                check(bitmapPixels<=950000,"art bitmap stays within the pixel budget for "+name+" ("+bitmapPixels+" px)");
                // 位图必须保持请求的取景比例；月历 centerCrop 只裁不缩，今日安排 fitCenter
                // 在上报与实测不一致时保留焦点图，避免二次横向裁切。
                double bitmapAspect=bitmapWidth/(double)bitmapHeight;
                WidgetBudget.Plan bitmapPlan=agenda
                    ?WidgetBudget.agendaCard(optionSize[0]-WidgetBudget.AGENDA_PADDING_DP,optionSize[1]-WidgetBudget.AGENDA_PADDING_DP,Art.aspect(monthValue))
                    :plan;
                double expectedAspect=agenda
                    ?bitmapPlan.artWidth/(double)Math.max(1,bitmapPlan.artHeight)
                    :Art.aspect(monthValue);
                check(Math.abs(bitmapAspect-expectedAspect)/expectedAspect<0.01,
                    (agenda?"agenda art bitmap matches its lane framing for ":"art bitmap keeps the artwork aspect for ")+name+
                    " ("+String.format("%.4f",bitmapAspect)+" vs "+String.format("%.4f",expectedAspect)+")");
            } else {
                check(false,"art view has a bitmap for "+name);
            }
            // 文字绝不压在图上：插画块与标题块不相交（上一轮用户投诉的问题）。
            int[] artBox=absBounds(art),titleBox=absBounds(title);
            check(artBox[2]<=titleBox[0]||titleBox[2]<=artBox[0]||artBox[3]<=titleBox[1]||titleBox[3]<=artBox[1],
                "artwork and title do not overlap for "+name);
            if(agenda) {
                View eventsView=view.findViewById(R.id.widget_events);
                check(eventsView!=null,"agenda events view exists for "+name);
                int[] eventsBox=absBounds(eventsView);
                check(artBox[2]<=eventsBox[0]||eventsBox[2]<=artBox[0]||artBox[3]<=eventsBox[1]||eventsBox[3]<=artBox[1],
                    "agenda artwork and event list do not overlap for "+name);
            }
            int rowHeight=0;
            if(!agenda) {
                LinearLayout grid=view.findViewById(R.id.widget_grid);
                check(grid.getChildCount()==6,name+" six date rows");
                for(int i=0;i<6;i++) { LinearLayout row=(LinearLayout)grid.getChildAt(i); check(row.getChildCount()==7,name+" seven days in row"); rowHeight=Math.max(rowHeight,row.getHeight()); }
                check(rowHeight>=Math.round(18*density),name+" date rows stay tappable ("+rowHeight+"px)");
            }
            // 该档位下插画可见比例的期望下限：短卡只承诺"有一条带"，高卡承诺接近零裁切。
            double minVisible=agenda?(boxSize[1]<=200?0.75:0.50):boxSize[1]>=620?0.95:boxSize[1]>=560?0.85:boxSize[1]>=460?0.65:0.35;
            JSONObject sidecar=new JSONObject();
            sidecar.put("name",name).put("agenda",agenda).put("size",new org.json.JSONArray(boxSize)).put("density",density);
            sidecar.put("art",new org.json.JSONArray(artBox)).put("title",new org.json.JSONArray(titleBox)).put("rowHeight",rowHeight);
            sidecar.put("artWidthDp",plan.artWidth).put("artHeightDp",plan.artHeight)
                   .put("artVisible",plan.artVisible).put("minVisible",minVisible).put("degraded",plan.degraded);
            sidecar.put("optionSize",new org.json.JSONArray(optionSize)).put("boxSize",new org.json.JSONArray(boxSize));
            sidecar.put("artBitmap",new org.json.JSONArray(new int[]{bitmapWidth,bitmapHeight}));
            sidecar.put("parcelBytes",recordedParcelBytes);
            sidecar.put("accent",String.format("#%08x",Ui.accent(monthValue)));
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); parent.draw(new Canvas(bitmap));
            File png=new File(context.getExternalFilesDir(null),name+".png");
            try(FileOutputStream out=new FileOutputStream(png)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
            File json=new File(context.getExternalFilesDir(null),name+".json");
            try(FileOutputStream out=new FileOutputStream(json)) { out.write(sidecar.toString().getBytes("UTF-8")); }
            bitmap.recycle();
        } catch(Throwable e) { failure[0]=e; } });
        if(failure[0]!=null)throw new AssertionError("RemoteViews inflation failed for "+name,failure[0]);
        check(true,"RemoteViews inflated and rendered "+name);
    }
    private void renderSplitAndRecord(int widgetId,int[] size) throws Exception {
        Bundle options=new Bundle(); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,size[0]); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,size[1]);
        RemoteViews remote=Widgets.renderSplit(context,AppWidgetManager.getInstance(context),repository,widgetId,options);
        String name="widget-split-"+size[0]+"x"+size[1];
        Parcel parcel=Parcel.obtain();
        try { remote.writeToParcel(parcel,0); check(parcel.dataSize()<900*1024,name+" RemoteViews fits the Binder budget"); parcel.setDataPosition(0); check(RemoteViews.CREATOR.createFromParcel(parcel)!=null,name+" RemoteViews survives a parcel round trip"); }
        finally { parcel.recycle(); }
        final Throwable[] failure={null};
        runOnMainSync(()->{ try {
            float density=context.getResources().getDisplayMetrics().density; int width=Math.round(size[0]*density),height=Math.round(size[1]*density);
            FrameLayout parent=new FrameLayout(context); View view=remote.apply(context,parent); parent.addView(view,new FrameLayout.LayoutParams(-1,-1));
            parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); parent.layout(0,0,width,height);
            ImageView art=view.findViewById(R.id.split_art); TextView title=view.findViewById(R.id.split_title); LinearLayout grid=view.findViewById(R.id.split_grid);
            check(art!=null&&title!=null&&grid!=null,name+" art, title and grid views exist"); check(grid.getChildCount()==6,name+" six date rows");
            check(title.getText().toString().contains("10月"),name+" split widget title explains the numeric month ("+title.getText()+")");
            check(art.getWidth()>=Math.round((size[0]-20)*density/2f)-2&&art.getHeight()>=Math.round((size[1]-20)*density)-2,name+" image column fills the left half");
            for(int i=0;i<6;i++)check(((LinearLayout)grid.getChildAt(i)).getChildCount()==7,name+" seven days in row "+i);
            check(art.getDrawable() instanceof android.graphics.drawable.BitmapDrawable,name+" image column has a bitmap");
        } catch(Throwable e) { failure[0]=e; } });
        if(failure[0]!=null)throw new AssertionError("Split RemoteViews inflation failed for "+name,failure[0]);
        check(true,"RemoteViews inflated and rendered "+name);
    }
    /** 视图在渲染位图里的绝对边界（沿父链累加偏移；根固定在 (0,0)）。 */
    private int[] absBounds(View view) {
        int x=0,y=0;
        for(View v=view;v!=null&&v.getParent() instanceof View;v=(View)v.getParent()) { x+=v.getLeft(); y+=v.getTop(); }
        return new int[]{x,y,x+view.getWidth(),y+view.getHeight()};
    }
    /**
     * 首页首屏的版式：上半插画、下半整月日期格，两者都在第一屏里。
     *
     * 这是本次改版的核心承诺（"不要点开看完整图，把他融合进 app"），所以在设备测试里也钉一遍，
     * 而不是只靠 tools 里的截图核对——截图核对依赖 adb 与 python 都在场。
     * 判据：插画面板全宽且占屏高 ≥ 40%；日期格 35 或 42 格（5/6 行 × 7 列）、每格 ≥ 32dp、
     * 且最后一格的屏幕坐标仍在屏内（不能被底部导航盖住或推到屏幕外）。
     */
    private void testHomeLayout() throws Exception {
        for(int i=0;i<=12;i++) check(Art.QUOTES[i]!=null&&!Art.QUOTES[i].trim().isEmpty(),"artwork quote exists for splash asset "+i);
        Activity activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            runOnMainSync(()->{});                       // 先让首帧落地
            assertStatus(activity,"waiting for the first layout");
            final java.util.List<View> cells=new ArrayList<>();
            final View[] art={null};
            final View[] quote={null};
            runOnMainSync(()->collect(activity.getWindow().getDecorView(),art,cells,quote));
            check(art[0]!=null,"home page shows the poster illustration panel");
            check(quote[0]!=null,"home page shows the current month's quote");
            if(quote[0] instanceof TextView) check(((TextView)quote[0]).getText().toString().equals(Art.QUOTES[YearMonth.now().getMonthValue()]),
                "home page quote matches the current month artwork");
            if(art[0]!=null) {
                int screenWidth=activity.getResources().getDisplayMetrics().widthPixels;
                int screenHeight=activity.getResources().getDisplayMetrics().heightPixels;
                check(art[0].getWidth()>=screenWidth*0.9f,"illustration panel spans the full width ("+art[0].getWidth()+" of "+screenWidth+")");
                check(art[0].getHeight()>=screenHeight*0.4f,"illustration panel is at least 40% of the screen height ("+art[0].getHeight()+" of "+screenHeight+")");
                checkMotionFrameChanges(art[0]);
            }
            check(cells.size()==35||cells.size()==42,"date grid has 5 or 6 rows of seven days ("+cells.size()+" cells)");
            int minHeight=Integer.MAX_VALUE,maxBottom=0,screenHeight=activity.getResources().getDisplayMetrics().heightPixels;
            int[] location=new int[2];
            for(View cell:cells) {
                minHeight=Math.min(minHeight,cell.getHeight());
                cell.getLocationOnScreen(location);
                maxBottom=Math.max(maxBottom,location[1]+cell.getHeight());
            }
            check(minHeight>=Ui.dp(context,32),"every date cell is at least 32dp tall ("+minHeight+"px)");
            check(maxBottom<=screenHeight,"the last date row is inside the screen ("+maxBottom+" <= "+screenHeight+")");
            log("PASS home page fusion layout on the first screen");
        } finally {
            final Activity target=activity;
            runOnMainSync(()->target.finish());          // 结束测试用的 Activity，别让它留在栈上
        }
    }
    /** 开屏回归：验证所选月度角色图动起来，并在动画结束后确实把用户带到主页。 */
    private void testSplashAnimation() throws Exception {
        SharedPreferences preferences=context.getSharedPreferences(SplashActivity.SETTINGS_NAME,0);
        Set<String> previous=preferences.contains(SplashActivity.MONTH_POOL_KEY)
            ?new HashSet<>(preferences.getStringSet(SplashActivity.MONTH_POOL_KEY,Collections.emptySet())):null;
        preferences.edit().putStringSet(SplashActivity.MONTH_POOL_KEY,new HashSet<>(Collections.singleton("6"))).apply();
        Activity splash=startActivitySync(new Intent(context,SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            assertStatus(splash,"waiting for the splash animation");
            final View[] view={null};
            runOnMainSync(()->{
                ViewGroup content=splash.getWindow().getDecorView().findViewById(android.R.id.content);
                if(content!=null&&content.getChildCount()>0)view[0]=content.getChildAt(0);
            });
            check(view[0]!=null&&view[0].getContentDescription()!=null&&view[0].getContentDescription().toString().startsWith("翁法罗斯月历开屏动画：长昼月"),
                "launcher opens the animated splash view");
            if(view[0]!=null&&view[0].getWidth()>0&&view[0].getHeight()>0) {
                final Bitmap[] frames={null,null};
                runOnMainSync(()->frames[0]=snapshot(view[0]));
                SystemClock.sleep(450);
                runOnMainSync(()->frames[1]=snapshot(view[0]));
                boolean changed=false; int step=Math.max(1,frames[0].getWidth()/48);
                for(int y=0;y<frames[0].getHeight()&&!changed;y+=step) for(int x=0;x<frames[0].getWidth();x+=step)
                    if(frames[0].getPixel(x,y)!=frames[1].getPixel(x,y)) { changed=true; break; }
                frames[0].recycle(); frames[1].recycle();
                check(changed,"splash animation changes the rendered frame");
            }
            SystemClock.sleep(1050);
            waitForIdleSync();
            check(splash.isFinishing()||splash.isDestroyed(),"splash transitions to the home activity");
            log("PASS selected splash asset animation transitions to home");
        } finally {
            final Activity target=splash;
            runOnMainSync(()->{ if(!target.isFinishing()&&!target.isDestroyed())target.finish(); });
            SharedPreferences.Editor restore=preferences.edit();
            if(previous==null)restore.remove(SplashActivity.MONTH_POOL_KEY); else restore.putStringSet(SplashActivity.MONTH_POOL_KEY,previous);
            restore.apply();
        }
    }
    /** 动效回归：不能只存在一个 View 类名，实际绘制的两帧也必须不同。 */
    private void checkMotionFrameChanges(View view) throws Exception {
        check(view instanceof ArtMotionImageView,"home illustration uses the motion image view");
        if(!(view instanceof ArtMotionImageView)||view.getWidth()<=0||view.getHeight()<=0)return;
        final Bitmap[] frames={null,null};
        runOnMainSync(()->frames[0]=snapshot(view));
        SystemClock.sleep(500);
        runOnMainSync(()->frames[1]=snapshot(view));
        boolean changed=false;
        int step=Math.max(1,frames[0].getWidth()/48);
        for(int y=0;y<frames[0].getHeight()&&!changed;y+=step) for(int x=0;x<frames[0].getWidth();x+=step) {
            if(frames[0].getPixel(x,y)!=frames[1].getPixel(x,y)) { changed=true; break; }
        }
        frames[0].recycle(); frames[1].recycle();
        check(changed,"home illustration animation changes the rendered frame");
    }
    private Bitmap snapshot(View view) {
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap)); return bitmap;
    }
    /** 递归收集首页里的插画面板与日期格：两者的身份来自应用自己设置的无障碍描述。 */
    private void collect(View view,View[] art,java.util.List<View> cells,View[] quote) {
        CharSequence description=view.getContentDescription();
        if(description!=null) {
            String text=description.toString();
            if(text.contains("月历插画")&&view instanceof ImageView) art[0]=view;
            else if(text.startsWith("本月寄语：")) quote[0]=view;
            else if(text.matches("\\d+月\\d+日 星期.*")) cells.add(view);
        }
        if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) collect(group.getChildAt(i),art,cells,quote); }
    }
    /** 等主线程空闲，顺带报出当前是哪个界面，方便失败时定位。 */
    private void assertStatus(Activity activity,String note) {
        waitForIdleSync();
        check(activity!=null&&!activity.isFinishing(),"activity is alive while "+note);
    }
    private void testReminder() throws Exception {
        CalendarEvent e=event("Reminder integration test",LocalDate.now()); e.reminderMinutes=0; e.reminderArt=7; e.start=System.currentTimeMillis()+1200; e.end=e.start+3600000; localId=repository.save(e); e.id=localId;
        Reminders.reschedule(context);
        check(context.getSharedPreferences("reminders",0).getStringSet("ids",Collections.emptySet()).contains("l:"+localId),"local alarm scheduled");
        long timeout=System.currentTimeMillis()+20000;
        boolean delivered=false;
        while(System.currentTimeMillis()<timeout) {
            for(android.service.notification.StatusBarNotification n:context.getSystemService(NotificationManager.class).getActiveNotifications()) if(n.getTag()!=null&&n.getTag().equals("local:"+localId))delivered=true;
            if(delivered)break; SystemClock.sleep(200);
        }
        check(delivered,"AlarmManager receiver posted notification");
        for(android.service.notification.StatusBarNotification n:context.getSystemService(NotificationManager.class).getActiveNotifications())
            if(n.getTag()!=null&&n.getTag().equals("local:"+localId)) check(n.getNotification().getLargeIcon()!=null,"notification carries the selected character artwork");
        context.getSystemService(NotificationManager.class).cancel("local:"+localId,0); repository.delete(e); Reminders.reschedule(context);
        check(!context.getSharedPreferences("reminders",0).getStringSet("ids",Collections.emptySet()).contains("l:"+localId),"deleted event alarm removed"); localId=0;
        log("PASS actual AlarmManager -> BroadcastReceiver -> notification and cancellation");
    }
}
