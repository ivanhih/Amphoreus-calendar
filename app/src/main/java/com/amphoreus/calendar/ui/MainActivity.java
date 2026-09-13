package com.amphoreus.calendar.ui;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.*;
import android.provider.CalendarContract;
import android.text.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.amphoreus.calendar.CalendarApp;
import com.amphoreus.calendar.core.*;
import com.amphoreus.calendar.data.*;
import com.amphoreus.calendar.widget.Widgets;
import java.time.*;
import java.util.*;

/**
 * 月历首页：上半是当月海报插画，下半是应用自己算出来的真实日期格，两者在同一屏里连成一张纸。
 *
 * 版式由 core.FitBudget 决定，不在这里写死数字：插画面板**钉在**滚动区上方（不跟着日期格滚），
 * 日期格每行的高度按"中段还剩多少"算出来。换任何屏幕尺寸，首屏都是"插画 + 整月 6 行格子"，
 * 不会出现某个机型上末行被挤到屏幕外面去。
 *
 * 三件不能忘的历史教训：
 * 1. `days` 容器必须每次 render 重建。曾经因为它只在渲染插画时创建，从「日程」切回「月历」时
 *    它指向上一轮已被移除的容器，日期格画了也看不见。
 * 2. 插画面板只叠加海报原本的当月寄语，不压大面积渐隐。上一版曾被整块遮罩盖成了"一条横条"。
 * 3. 面板高度不清零也能量出可用高度（见 scheduleBudget），所以不会先闪一帧空面板。
 */
public final class MainActivity extends Activity {
    /** 顶条：品牌、翻月、年月标题、回今天、设置，全部合成一条。旧版是 62dp 品牌块加 76dp 标题条。 */
    private static final int TOP_BAR_DP=44;
    /** 星期行高度。 */
    private static final int WEEKDAY_ROW_DP=20;
    /** 日期格下面那一行：日期、连接状态、新建入口。 */
    private static final int HEADING_ROW_DP=24;
    /** 交给 FitBudget 的 chrome：星期行加上面那行。插画面板不在其中——它钉在中段上方，
     *  所以量到的"中段高度"已经把它排除在外了。 */
    private static final int CHROME_DP=WEEKDAY_ROW_DP+HEADING_ROW_DP;
    /** 日期区底色里混进多少当月插画底边色。只混一点：深色主题要保住，正文对比度要 ≥ 4.5:1。 */
    private static final float EDGE_TINT=0.18f;
    /** 预算还没量出来之前，日期格的临时行高。很快就（同一次布局里）被真实值替换。 */
    private static final int PROVISIONAL_CELL_DP=36;

    private LocalDate selected=LocalDate.now();
    private YearMonth month=YearMonth.from(selected);
    private int tab=0,range=0,loadGeneration,budgetGeneration;
    private int artHeightPx,cellHeightPx;
    private LinearLayout root,content,sheet,days,agenda,nav;
    private FrameLayout artFrame;
    private ScrollView scroll;
    private ArtMotionImageView artPanel;
    private TextView topTitle,headingDate,status;
    private List<Occurrence> occurrences=new ArrayList<>();
    private boolean calendarObserverRegistered;
    private boolean calendarPermissionRequested;
    private boolean notificationPermissionRequested;
    private boolean swipeTracking,swipeDragging,swipeAnimating,childTouchCancelled;
    private float swipeStartX,swipeStartY;
    private int touchSlopPx;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private EventRepository repository;
    private String query="";
    private final Runnable refresh=this::load;
    private final ContentObserver observer=new ContentObserver(handler) { @Override public void onChange(boolean self) { handler.removeCallbacks(refresh); handler.postDelayed(refresh,350); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository=((CalendarApp)getApplication()).repository();
        touchSlopPx=ViewConfiguration.get(this).getScaledTouchSlop();
        cellHeightPx=Ui.dp(this,PROVISIONAL_CELL_DP);
        // 恢复状态时容错：Bundle 可能来自旧版本或被截断，解析失败就退回设备当前日期。
        if(state!=null) {
            String date=state.getString("date"),savedMonth=state.getString("month");
            try {
                if(date!=null) selected=LocalDate.parse(date);
                if(savedMonth!=null) month=YearMonth.parse(savedMonth);
            } catch(RuntimeException ignored) { selected=LocalDate.now(); month=YearMonth.from(selected); }
            tab=Math.min(Math.max(state.getInt("tab"),0),1); range=state.getInt("range"); query=state.getString("query","");
            artHeightPx=Math.max(0,state.getInt("artHeight"));
            cellHeightPx=Math.max(Ui.dp(this,FitBudget.MIN_CELL),state.getInt("cellHeight",cellHeightPx));
        }
        else acceptIntent(getIntent());
        // 先给首帧一个按当前屏幕宽度估算的插画高度，避免预算回调完成前出现整块空白；
        // 真正布局完成后 scheduleBudget() 仍会按系统栏和可用高度精调。
        if(tab==0&&artHeightPx<=0) {
            DisplayMetrics metrics=getResources().getDisplayMetrics();
            int widthDp=Math.max(1,Math.round(metrics.widthPixels/metrics.density));
            artHeightPx=Ui.dp(this,FitBudget.zeroCropHeight(widthDp,Art.aspect(month.getMonthValue())));
        }
        render();
        // onResume 会重建一次视图树，不能把权限请求挂在可能被移除的旧 root 上。
        handler.postDelayed(this::requestCalendarPermissionIfNeeded,300);
    }
    /**
     * Activity 层处理整页的横向拖动。拖动时整张当前月历跟着手指走，松手后
     * 才决定完成切月还是回弹；竖向滚动与普通点击仍交给原来的 View。
     */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(tab==0&&!swipeAnimating) {
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN) {
                swipeTracking=true; swipeDragging=false; childTouchCancelled=false;
                swipeStartX=event.getX(); swipeStartY=event.getY();
            } else if(swipeTracking&&action==MotionEvent.ACTION_MOVE&&!swipeDragging) {
                float dx=event.getX()-swipeStartX,dy=event.getY()-swipeStartY;
                if(Math.abs(dx)>touchSlopPx&&Math.abs(dx)>Math.abs(dy)*1.18f) {
                    swipeDragging=true; cancelChildTouch(event);
                }
            }
            if(swipeDragging) {
                if(action==MotionEvent.ACTION_MOVE) {
                    float dx=event.getX()-swipeStartX;
                    applySwipeOffset(dx);
                    return true;
                }
                if(action==MotionEvent.ACTION_UP) {
                    float dx=event.getX()-swipeStartX;
                    finishSwipe(dx);
                    return true;
                }
                if(action==MotionEvent.ACTION_CANCEL) { finishSwipe(0); return true; }
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)swipeTracking=false;
        }
        return super.dispatchTouchEvent(event);
    }
    private void cancelChildTouch(MotionEvent event) {
        if(childTouchCancelled)return;
        MotionEvent cancel=MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel); cancel.recycle(); childTouchCancelled=true;
    }
    private void applySwipeOffset(float dx) {
        if(root==null)return;
        float width=Math.max(1,root.getWidth());
        float limited=Math.max(-width,Math.min(width,dx));
        root.setTranslationX(limited);
        root.setScaleX(1f-Math.min(.018f,Math.abs(limited/width)*.018f));
    }
    private void finishSwipe(float dx) {
        if(root==null) { swipeTracking=false; return; }
        float width=Math.max(1,root.getWidth()),threshold=width*.22f;
        boolean complete=Math.abs(dx)>=threshold;
        int delta=dx<0?1:-1;
        float target=complete?(delta>0?-width:width):0f;
        swipeAnimating=true; swipeTracking=false;
        long duration=complete?Math.max(130,Math.min(240,Math.round(210f*(1f-Math.min(1f,Math.abs(dx)/width))))):150;
        root.animate().translationX(target).scaleX(complete?.982f:1f).setDuration(duration)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction(()->{
                root.setTranslationX(0f); root.setScaleX(1f); swipeAnimating=false;
                if(complete) {
                    month=month.plusMonths(delta); selected=month.atDay(Math.min(selected.getDayOfMonth(),month.lengthOfMonth()));
                    render(); load();
                }
            }).start();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent); render(); load(); }
    /** 首次打开就申请系统日历权限；拒绝后仍保留本地日历，不在同一轮生命周期里反复弹窗。 */
    private void requestCalendarPermissionIfNeeded() {
        if(calendarPermissionRequested||repository==null||isFinishing()||isDestroyed()) return;
        ArrayList<String> missing=new ArrayList<>();
        if(!repository.system.canRead()) missing.add(Manifest.permission.READ_CALENDAR);
        if(!repository.system.canWrite()) missing.add(Manifest.permission.WRITE_CALENDAR);
        if(missing.isEmpty()) { requestNotificationPermissionIfNeeded(); return; }
        calendarPermissionRequested=true;
        requestPermissions(missing.toArray(new String[0]),100);
    }
    /** 日历权限通过后顺便申请通知权限，避免外部同步事件已经排程却无法显示。 */
    private void requestNotificationPermissionIfNeeded() {
        if(Build.VERSION.SDK_INT<33||notificationPermissionRequested||isFinishing()||isDestroyed()) return;
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences preferences=getSharedPreferences("settings",0);
        if(preferences.getBoolean("notificationPermissionAsked",false)) return;
        notificationPermissionRequested=true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==101) {
            getSharedPreferences("settings",0).edit().putBoolean("notificationPermissionAsked",true).apply();
            return;
        }
        if(request!=100||repository==null) return;
        CalendarApp.IO.execute(()->{
            try {
                if(repository.system.canWrite()) {
                    SharedPreferences preferences=getSharedPreferences("settings",0);
                    if(!preferences.contains("defaultCalendar")) {
                        for(CalendarSource source:repository.system.sources()) if(source.writable) {
                            preferences.edit().putLong("defaultCalendar",source.id).apply(); break;
                        }
                    }
                }
            } catch(Exception ignored) { }
            runOnUiThread(()->{ if(isDestroyed())return; ((CalendarApp)getApplication()).changed(); load(); requestNotificationPermissionIfNeeded(); });
        });
    }
    private void acceptIntent(Intent intent) {
        String date=intent.getStringExtra("date"); if(date!=null) try { selected=LocalDate.parse(date); month=YearMonth.from(selected); tab=0; } catch(Exception ignored) {}
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("date",selected.toString()); out.putString("month",month.toString());
        out.putInt("tab",tab); out.putInt("range",range); out.putString("query",query);
        // 预算结果也存下来：重建后先按原尺寸画一帧，再重新量，避免闪一下空面板。
        out.putInt("artHeight",artHeightPx); out.putInt("cellHeight",cellHeightPx);
    }
    @Override protected void onResume() {
        super.onResume();
        ((CalendarApp)getApplication()).refreshCalendarObserver();
        if(repository.system.canObserve()) {
            try { getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI,true,observer); calendarObserverRegistered=true; }
            catch(SecurityException ignored) { calendarObserverRegistered=false; }
        }
        render(); load();
        // 启动器可能保留旧 RemoteViews；每次真正回到应用时再主动推送一次，确保旧小组件也换成当前布局。
        CalendarApp.IO.execute(()->Widgets.updateAll(getApplicationContext()));
    }
    @Override protected void onPause() { super.onPause(); if(artPanel!=null) artPanel.setMotionEnabled(false); if(calendarObserverRegistered) { try { getContentResolver().unregisterContentObserver(observer); } catch(Exception ignored) {} calendarObserverRegistered=false; } handler.removeCallbacks(refresh); }
    @Override protected void onDestroy() { loadGeneration++; budgetGeneration++; super.onDestroy(); }
    private boolean monday() { return getSharedPreferences("settings",0).getBoolean("monday",true); }
    private int accent() { return Ui.accent(month.getMonthValue()); }
    /** 日期区的底色：深色底混一点当月插画底边色，插画与日期格之间才不像两张东西。 */
    private int sheetColor() { return Ui.tint(Ui.BG,Art.edgeColor(month.getMonthValue()),EDGE_TINT); }

    private void render() {
        root=Ui.root(this); root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this); Ui.pad(top,14,0);
        String monthlyName=tab==0?Art.NAMES[month.getMonthValue()]:"翁法罗斯";
        TextView brand=Ui.serif(this,monthlyName,16,Ui.TEXT); brand.setTypeface(Typeface.SERIF,Typeface.BOLD); brand.setContentDescription("当前月份："+monthlyName); top.addView(brand,new LinearLayout.LayoutParams(-2,-1));
        if(tab==0) top.addView(barAction("‹",()->move(-1)));
        topTitle=Ui.text(this,"",13,accent()); topTitle.setGravity(Gravity.CENTER); topTitle.setLetterSpacing(.1f);
        top.addView(topTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,TOP_BAR_DP),1));
        if(tab==0) {
            top.addView(barAction("›",()->move(1)));
            top.addView(barAction("今天",()->{ selected=LocalDate.now(); month=YearMonth.from(selected); render(); load(); }));
        }
        top.addView(barAction("设置",()->startActivity(new Intent(this,SettingsActivity.class))));
        root.addView(top,new LinearLayout.LayoutParams(-1,Ui.dp(this,TOP_BAR_DP)));

        // 插画面板钉在滚动区上方：它属于"上半张海报"，不该跟着日期格一起滚掉。
        artPanel=null; artFrame=null;
        if(tab==0) {
            artFrame=new FrameLayout(this);
            artFrame.setClipChildren(true);
            artPanel=new ArtMotionImageView(this);
            artPanel.setScaleType(ImageView.ScaleType.FIT_XY);   // 位图尺寸就是面板尺寸，不留边
            artPanel.setContentDescription(Ui.date(month.atDay(1))+"至"+Ui.date(month.atEndOfMonth())+"月历插画，主题"+Art.NAMES[month.getMonthValue()]);
            artFrame.addView(artPanel,new FrameLayout.LayoutParams(-1,-1));
            String quoteText=Art.QUOTES[month.getMonthValue()];
            TextView quote=Ui.serif(this,quoteText,11.5f,Ui.quoteColor());
            quote.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            quote.setMaxLines(3); quote.setIncludeFontPadding(false); quote.setLineSpacing(Ui.dp(this,2),1f);
            quote.setShadowLayer(Ui.dp(this,2),0,Ui.dp(this,1),0xcc000000);
            quote.setContentDescription("本月寄语："+quoteText.replace('\n',' '));
            FrameLayout.LayoutParams quoteParams=new FrameLayout.LayoutParams(-1,Ui.dp(this,96),Gravity.BOTTOM);
            quoteParams.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,8));
            artFrame.addView(quote,quoteParams);
            root.addView(artFrame,new LinearLayout.LayoutParams(-1,artHeightPx));
        }

        scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        content=Ui.column(this); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        if(tab==0) {
            topTitle.setText(month.getYear()+"  ·  "+Art.ENGLISH[month.getMonthValue()]);
            topTitle.setOnClickListener(v->pickDate()); topTitle.setFocusable(true); topTitle.setContentDescription("跳转到指定日期");
            // 滚动区的底色也跟着走：内容比屏幕短的时候，底下露出来的一块才不会是另一种颜色。
            scroll.setBackgroundColor(sheetColor());
            sheet=Ui.column(this); sheet.setBackgroundColor(sheetColor()); Ui.pad(sheet,16,0); content.addView(sheet);
            addWeekdays(LocaleText.weekdays(this,monday()));
            // 日期格容器必须在每次 render 时重建并挂到当前视图树上：
            // 否则从「日程」切回月历时 days 会指向上一轮已被移除的容器，日期格画了也看不见。
            days=Ui.column(this); sheet.addView(days);
            renderGrid();
            addHeadingRow();
            agenda=Ui.column(this); sheet.addView(agenda);
        } else {
            topTitle.setText("日程"); topTitle.setContentDescription(null); topTitle.setFocusable(false);
            days=null; sheet=null; Ui.pad(content,20,0);
            renderAgendaHeader();
            agenda=Ui.column(this); content.addView(agenda);
        }
        renderAgenda();
        Ui.space(content,20);

        // 底部导航不做纵向内边距：那 12dp 会从预算里悄悄吃掉一格日期的高度
        // （实测就是这样把日期格压到 32dp 下限的），而按钮本身已经有 50dp。
        nav=Ui.row(this); Ui.pad(nav,12,0); String[] labels={"▦  月历","☷  日程"};
        for(int i=0;i<2;i++) { final int index=i; TextView b=Ui.button(this,labels[i],()->switchTab(index)); b.setTextColor(i==tab?accent():Ui.MUTED); b.setBackground(Ui.background(i==tab?Ui.CARD:Ui.BG,14,this)); nav.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1)); }
        root.addView(nav);

        if(tab==0) { refreshArt(); scheduleBudget(); }
    }
    private void switchTab(int index) { if(tab==index) return; tab=index; render(); load(); }
    /** 顶条上的小动作：要与 44dp 顶条等高，所以不能用 Ui.button（它自带 48dp 最小高度）。 */
    private TextView barAction(String label,Runnable action) {
        TextView b=Ui.text(this,label,13,Ui.MUTED); b.setGravity(Gravity.CENTER); Ui.pad(b,9,0);
        b.setMinHeight(Ui.dp(this,TOP_BAR_DP)); b.setOnClickListener(v->action.run()); b.setFocusable(true); return b;
    }
    private void addWeekdays(String[] labels) {
        LinearLayout weekday=Ui.row(this); int accent=accent();
        for(String label:labels) { TextView t=Ui.serif(this,label,11,accent); t.setGravity(Gravity.CENTER); t.setAlpha(.8f); weekday.addView(t,new LinearLayout.LayoutParams(0,Ui.dp(this,WEEKDAY_ROW_DP),1)); }
        sheet.addView(weekday,new LinearLayout.LayoutParams(-1,Ui.dp(this,WEEKDAY_ROW_DP)));
    }
    /** 日期格下面那一行：左边选中日期，右边连接状态与新建入口。旧版把这三样拆成三行、占 84dp。 */
    private void addHeadingRow() {
        LinearLayout heading=Ui.row(this);
        headingDate=Ui.text(this,"",14,Ui.TEXT); headingDate.setTypeface(null,Typeface.BOLD);
        heading.addView(headingDate,new LinearLayout.LayoutParams(-2,-2));
        status=Ui.text(this,"正在读取日程…",10,Ui.MUTED); status.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(0,-2,1); statusParams.setMargins(Ui.dp(this,8),0,Ui.dp(this,8),0); heading.addView(status,statusParams);
        TextView add=Ui.text(this,"＋ 新建",12,accent()); add.setGravity(Gravity.CENTER); Ui.pad(add,6,0);
        add.setOnClickListener(v->EventActivity.create(this,selected)); add.setFocusable(true); add.setContentDescription("在选中日期新建日程");
        heading.addView(add,new LinearLayout.LayoutParams(-2,-2));
        sheet.addView(heading,new LinearLayout.LayoutParams(-1,Ui.dp(this,HEADING_ROW_DP)));
    }
    /** 「日程」页自己的页头：标题、翻月、范围、搜索、状态。 */
    private void renderAgendaHeader() {
        TextView h=Ui.text(this,"把日子，留给重要的事。",21,Ui.TEXT); Ui.pad(h,0,4); content.addView(h);
        LinearLayout bar=Ui.row(this);
        bar.addView(navButton("‹",()->move(-1)));
        TextView title=Ui.text(this,month.getYear()+" 年 "+month.getMonthValue()+" 月",15,Ui.TEXT); title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v->pickDate()); title.setFocusable(true); title.setContentDescription("跳转到指定日期");
        bar.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        bar.addView(navButton("今天",()->{ selected=LocalDate.now(); month=YearMonth.from(selected); render(); load(); }));
        bar.addView(navButton("›",()->move(1))); content.addView(bar);
        LinearLayout modes=Ui.row(this); String[] labels={"当天","本周","未来 30 天"};
        for(int i=0;i<labels.length;i++) { final int index=i; TextView b=Ui.button(this,labels[i],()->{ range=index; render(); load(); }); b.setTextColor(range==i?accent():Ui.MUTED); modes.addView(b,new LinearLayout.LayoutParams(0,-2,1)); } content.addView(modes);
        EditText search=new EditText(this); search.setSingleLine(true); search.setTextColor(Ui.TEXT); search.setHintTextColor(Ui.MUTED); search.setTextSize(14); search.setHint(LocaleText.t(this,"搜索当前范围：标题、地点、备注")); search.setText(query); content.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){ query=s.toString(); renderAgenda(); } public void afterTextChanged(Editable e){} });
        status=Ui.text(this,"正在读取日程…",11,Ui.MUTED); Ui.pad(status,0,6); content.addView(status);
    }
    private TextView navButton(String label,Runnable action) {
        TextView b=Ui.text(this,label,15,accent()); b.setGravity(Gravity.CENTER); Ui.pad(b,12,0); b.setMinHeight(Ui.dp(this,44));
        b.setBackground(Ui.background(Ui.CARD,12,this)); b.setOnClickListener(v->action.run()); b.setFocusable(true); return b;
    }

    /**
     * 按预算把插画面板与日期格的高度定下来。
     *
     * 不需要先把面板高度清零再量：插画面板与滚动区是兄弟，中段高度 = 可用高度 − 面板高度，
     * 所以"可用高度"就是两者当前高度相加，与面板此刻多高无关。于是同一次布局里就能收敛，
     * 不会先画一帧没有插画的空页面。
     */
    private void scheduleBudget() {
        final int generation=++budgetGeneration;
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                try { root.getViewTreeObserver().removeOnGlobalLayoutListener(this); } catch(RuntimeException ignored) {}
                if(generation!=budgetGeneration||isDestroyed()||tab!=0||artPanel==null||artFrame==null||scroll==null) return;
                DisplayMetrics metrics=getResources().getDisplayMetrics();
                int freePx=artPanel.getHeight()+scroll.getHeight();
                if(freePx<=0) return;
                int widthDp=Math.round(metrics.widthPixels/metrics.density);
                FitBudget.Plan plan=FitBudget.plan(widthDp,Math.round(freePx/metrics.density),Art.aspect(month.getMonthValue()),CHROME_DP);
                int artPx=Ui.dp(MainActivity.this,plan.artHeight),cellPx=Ui.dp(MainActivity.this,plan.cellHeight);
                if(artPx==artHeightPx&&cellPx==cellHeightPx) return;
                artHeightPx=artPx; cellHeightPx=cellPx;
                artFrame.getLayoutParams().height=artHeightPx; artFrame.requestLayout();
                refreshArt(); renderGrid();
            }
        });
    }
    /** 生成当月插画的位图。面板高度为 0 时不生成——0 高的位图没有意义。 */
    private void refreshArt() {
        if(artPanel==null||artHeightPx<=0) return;
        int width=artPanel.getWidth()>0?artPanel.getWidth():getResources().getDisplayMetrics().widthPixels;
         try { artPanel.setArtwork(Art.illustration(this,month.getMonthValue(),width,artHeightPx,sheetColor()),month.getMonthValue()); }
        catch(RuntimeException e) { Ui.toast(this,"插画载入失败，本页仍可使用"); }   // 素材缺失时降级，不要让首页崩掉
    }

    private void move(int delta) {
        if(tab==0) { month=month.plusMonths(delta); selected=month.atDay(Math.min(selected.getDayOfMonth(),month.lengthOfMonth())); render(); }
        else { selected=selected.plusDays(delta*(range==0?1:range==1?7:30)); month=YearMonth.from(selected); render(); }
        load();
    }
    private void pickDate() { new DatePickerDialog(this,(view,y,m,d)->{ selected=LocalDate.of(y,m+1,d); month=YearMonth.from(selected); render(); load(); },selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth()).show(); }

    /** 下半部分：当月主题色细竖线 + 衬线数字的真实日期格，日期全部由应用计算，与海报印刷的 2026 数字无关。 */
    private void renderGrid() {
        if(days==null) return;      // 只有月历 tab 会创建 days 容器，避免往已移除的视图里塞格子
        days.removeAllViews();
        int accent=accent(); float density=getResources().getDisplayMetrics().density;
        List<LocalDate> grid=CalendarMath.grid(month,monday());
        // 行数必须按"本月 1 日落在第几列 + 当月天数"算。旧版传的是 grid 第一格的列号（恒为 0），
        // 于是周日开头、31 天的月份会整行少画一行：2026 年 2 月只显示到 22 日、11 月只到 29 日，
        // 那些天在首页上看不到也点不到。行数逻辑收在 CalendarMath.rows 里，CoreTests 全量核对。
        int weeks=CalendarMath.rows(month,monday());
        for(int r=0;r<weeks;r++) {
            LinearLayout row=Ui.row(this);
            for(int c=0;c<7;c++) {
                LocalDate date=grid.get(r*7+c); LinearLayout cell=Ui.column(this);
                boolean inMonth=YearMonth.from(date).equals(month),isSelected=date.equals(selected),today=date.equals(LocalDate.now());
                GridCellDrawable background=new GridCellDrawable(accent,density)
                    .selected(isSelected).today(today).outside(!inMonth)
                    .leftRule(true).rightRule(c==6);      // 每列画左线、最后一列再补右线：整张表被包住
                cell.setBackground(background);
                TextView number=Ui.serif(this,String.valueOf(date.getDayOfMonth()),15,isSelected?Ui.BG:!inMonth?Ui.DIM:today?accent:Ui.TEXT);
                number.setGravity(Gravity.CENTER); cell.addView(number,new LinearLayout.LayoutParams(-1,0,1));
                boolean has=false; for(Occurrence o:occurrences) if(o.onDate(date,ZoneId.systemDefault())) { has=true; break; }
                TextView dot=Ui.text(this,"•",10,isSelected?Ui.BG:accent); dot.setGravity(Gravity.CENTER);
                dot.setAlpha(inMonth?1f:.45f);   // 上下月的日期整体淡一些，圆点也一样
                dot.setVisibility(has?View.VISIBLE:View.INVISIBLE);
                cell.addView(dot,new LinearLayout.LayoutParams(-1,Ui.dp(this,11)));
                cell.setContentDescription(Ui.date(date)+(today?"，今天":"")+(has?"，有日程":"")); cell.setFocusable(true);
                cell.setOnClickListener(v->{ selected=date; if(!YearMonth.from(date).equals(month)) { month=YearMonth.from(date); render(); load(); } else { renderGrid(); renderAgenda(); } });
                cell.setOnLongClickListener(v->{ EventActivity.create(this,date); return true; });
                row.addView(cell,new LinearLayout.LayoutParams(0,cellHeightPx,1));
            } days.addView(row);
        }
    }
    private LocalDate from() { if(tab==0) return CalendarMath.gridStart(month,monday()); return range==1?CalendarMath.weekStart(selected,monday()):selected; }
    private LocalDate until() { return tab==0?from().plusDays(42):from().plusDays(range==0?1:range==1?7:30); }
    private void load() {
        if(repository==null) return;
        int generation=++loadGeneration; LocalDate from=from(),until=until();
        CalendarApp.IO.execute(()->{
            try { List<Occurrence> data=repository.between(from,until); runOnUiThread(()->{ if(isDestroyed()||generation!=loadGeneration)return; occurrences=data; applyStatus(repository.system.canRead()?"系统日历已连接 · 随账号同步":"本地日历 · 点击连接系统日历",()->startActivity(new Intent(this,SettingsActivity.class))); renderGrid(); renderAgenda(); }); }
            catch(Exception e) { runOnUiThread(()->{ if(isDestroyed()||generation!=loadGeneration)return; occurrences=new ArrayList<>(); applyStatus("读取失败 · 点击重试（"+e.getMessage()+"）",this::load); renderGrid(); renderAgenda(); }); }
        });
    }
    /** 连接状态那一小行：点它进设置。月历页它挤在日期行右边，日程页它单独一行。 */
    private void applyStatus(String text,Runnable action) {
        if(status==null) return;
        status.setText(text); status.setOnClickListener(v->action.run()); status.setFocusable(true);
    }
    private void renderAgenda() {
        if(agenda==null)return; agenda.removeAllViews();
        if(tab==0) { if(headingDate!=null) headingDate.setText(Ui.date(selected)); }
        else { TextView heading=Ui.text(this,range==0?Ui.date(selected):Ui.date(from())+" 起",17,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); Ui.pad(heading,0,8); agenda.addView(heading); }
        int count=0;
        for(Occurrence o:occurrences) {
            if(tab==0 && !o.onDate(selected,ZoneId.systemDefault())) continue;
            String all=(o.event.title+" "+o.event.location+" "+o.event.notes).toLowerCase(Locale.ROOT);
            if(tab==1 && !all.contains(query.trim().toLowerCase(Locale.ROOT))) continue;
            count++; LinearLayout card=Ui.row(this); Ui.pad(card,14,12); card.setBackground(Ui.background(Ui.CARD,16,this));
            View line=new View(this); line.setBackground(Ui.background(o.event.color,2,this)); card.addView(line,new LinearLayout.LayoutParams(Ui.dp(this,3),Ui.dp(this,42)));
            LinearLayout text=Ui.column(this); Ui.pad(text,12,0); TextView title=Ui.text(this,o.event.title,16,Ui.TEXT); title.setMaxLines(2); text.addView(title);
            String when=o.event.allDay?"全天":Ui.time(o.start,ZoneId.systemDefault())+"–"+Ui.time(o.end,ZoneId.systemDefault());
            if(tab==1 && range>0) when=o.firstDate(ZoneId.systemDefault()).getMonthValue()+"/"+o.firstDate(ZoneId.systemDefault()).getDayOfMonth()+"  "+when;
            if(!o.firstDate(ZoneId.systemDefault()).equals(o.lastDate(ZoneId.systemDefault()))) when+=" · 跨日";
            text.addView(Ui.text(this,when+"  ·  "+o.event.calendarName,11,Ui.MUTED));
            if(!o.event.location.isEmpty()) text.addView(Ui.text(this,o.event.location,12,Ui.MUTED));
            card.addView(text,new LinearLayout.LayoutParams(0,-2,1)); card.addView(Ui.text(this,"›",22,accent()));
            card.setOnClickListener(v->EventActivity.open(this,o.event.key())); card.setFocusable(true); agenda.addView(card); Ui.space(agenda,8);
        }
        if(count==0) { TextView empty=Ui.text(this,query.isEmpty()?"暂无日程\n给这一天，留一点期待。":"此范围内没有匹配日程",14,Ui.MUTED); empty.setLineSpacing(Ui.dp(this,6),1); Ui.pad(empty,18,18); empty.setBackground(Ui.background(Ui.CARD,16,this)); agenda.addView(empty); }
    }
}
