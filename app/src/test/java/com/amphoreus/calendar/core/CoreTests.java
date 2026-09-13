package com.amphoreus.calendar.core;

import java.time.*;
import java.util.*;

/** Dependency-free regression suite, run by scripts/test.ps1 or a Java IDE. */
public final class CoreTests {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++; if(!condition)throw new AssertionError(message); }
    private static CalendarEvent timed(String start,String end,String zone,String recurrence) {
        CalendarEvent e=new CalendarEvent(); e.title="Test"; e.zone=zone; e.start=LocalDateTime.parse(start).atZone(ZoneId.of(zone)).toInstant().toEpochMilli(); e.end=LocalDateTime.parse(end).atZone(ZoneId.of(zone)).toInstant().toEpochMilli(); e.recurrence=recurrence; return e;
    }
    private static List<Occurrence> expand(CalendarEvent e,String from,String until,String zone) { return Recurrence.expand(e,LocalDate.parse(from),LocalDate.parse(until),ZoneId.of(zone)); }
    public static void main(String[] args) {
        int[] starts={4,7,7,3,5,1,3,6,2,4,7,2};
        for(int i=1;i<=12;i++) {
            YearMonth month=YearMonth.of(2026,i); check(month.atDay(1).getDayOfWeek().getValue()==starts[i-1],"2026 month start "+i);
            for(boolean monday:new boolean[]{true,false}) {
                List<LocalDate> grid=CalendarMath.grid(month,monday); check(grid.size()==42,"42 cells"); check(grid.get(0).getDayOfWeek()==(monday?DayOfWeek.MONDAY:DayOfWeek.SUNDAY),"week start");
                check(grid.contains(month.atDay(1))&&grid.contains(month.atEndOfMonth()),"all month dates present");
                for(int d=1;d<42;d++)check(grid.get(d).equals(grid.get(d-1).plusDays(1)),"contiguous cells");
            }
        }
        check(CalendarMath.grid(YearMonth.of(2028,2),true).contains(LocalDate.of(2028,2,29)),"leap grid");
        CalendarEvent e=timed("2026-01-31T10:00","2026-01-31T11:00","Asia/Shanghai","FREQ=MONTHLY");
        List<Occurrence> months=expand(e,"2026-01-01","2026-05-01","Asia/Shanghai"); check(months.size()==2,"skip nonexistent month days"); check(months.get(1).firstDate(ZoneId.of("Asia/Shanghai")).equals(LocalDate.of(2026,3,31)),"March 31 instead of clamped February 28");
        e=timed("2096-02-29T10:00","2096-02-29T11:00","UTC","FREQ=YEARLY"); check(expand(e,"2097-01-01","2105-01-01","UTC").size()==1,"skip century non-leap year");
        e=timed("2026-03-07T09:00","2026-03-07T10:00","America/New_York","FREQ=DAILY"); List<Occurrence> dst=expand(e,"2026-03-07","2026-03-10","America/New_York");
        check(dst.size()==3,"DST occurrences"); check(dst.get(1).start-dst.get(0).start==23*3600000L,"daily wall time across DST");
        for(Occurrence o:dst) check(Instant.ofEpochMilli(o.start).atZone(ZoneId.of("America/New_York")).getHour()==9,"9 AM retained");
        e=timed("2026-09-10T23:30","2026-09-11T01:00","Asia/Shanghai",""); Occurrence overnight=new Occurrence(e,e.start,e.end);
        check(overnight.onDate(LocalDate.of(2026,9,10),ZoneId.of("Asia/Shanghai")),"overnight first day"); check(overnight.onDate(LocalDate.of(2026,9,11),ZoneId.of("Asia/Shanghai")),"overnight next day");
        e=timed("2026-09-10T23:00","2026-09-11T00:00","UTC",""); check(!new Occurrence(e,e.start,e.end).onDate(LocalDate.of(2026,9,11),ZoneOffset.UTC),"exclusive midnight end");
        e=new CalendarEvent(); e.title="All day"; e.allDay=true; e.start=CalendarMath.midnightUtc(LocalDate.of(2026,9,10)); e.end=CalendarMath.midnightUtc(LocalDate.of(2026,9,12)); e.reminderMinutes=0; e.validate();
        Occurrence allDay=new Occurrence(e,e.start,e.end);
        for(String zone:new String[]{"America/Los_Angeles","Pacific/Kiritimati","Asia/Shanghai"}) {
            check(allDay.firstDate(ZoneId.of(zone)).equals(LocalDate.of(2026,9,10)),"all-day date stable "+zone);
            check(allDay.lastDate(ZoneId.of(zone)).equals(LocalDate.of(2026,9,11)),"all-day inclusive final display date "+zone);
            check(Instant.ofEpochMilli(ReminderTime.trigger(allDay,ZoneId.of(zone))).atZone(ZoneId.of(zone)).getHour()==9,"all-day local nine AM "+zone);
        }
        check(expand(e,"2026-09-12","2026-09-13","UTC").isEmpty(),"exclusive all-day boundary");
        e=timed("2000-01-03T10:00","2000-01-03T11:00","UTC","FREQ=WEEKLY"); check(expand(e,"2026-09-07","2026-09-14","UTC").size()==1,"old weekly series seeks correct range");
        e=timed("2026-09-01T00:00","2026-09-20T00:00","UTC",""); check(expand(e,"2026-09-10","2026-09-11","UTC").size()==1,"long spanning event");
        check(expand(e,"2026-09-10","2026-09-10","UTC").isEmpty(),"empty query range");
        boolean invalid=false; e.title="  "; try { e.validate(); } catch(IllegalArgumentException ex) { invalid=true; } check(invalid,"blank title rejected");
        e.title="Test"; e.end=e.start; invalid=false; try { e.validate(); } catch(IllegalArgumentException ex) { invalid=true; } check(invalid,"zero duration rejected");
        check(!Recurrence.supported("FREQ=WEEKLY;BYDAY=MO,WE"),"advanced RRULE not silently reduced");
        check(CalendarMath.defaultStart(LocalDate.of(2026,9,10),LocalDateTime.of(2026,9,10,23,30)).equals(LocalDateTime.of(2026,9,11,0,0)),"late-night default advances date");
        check(CalendarMath.defaultStart(LocalDate.of(2026,9,20),LocalDateTime.of(2026,9,10,23,30)).equals(LocalDateTime.of(2026,9,20,9,0)),"explicit future date remains selected date");
        ZoneId shanghai=ZoneId.of("Asia/Shanghai");
        LocalDate selectedDate=LocalDate.of(2026,9,13);
        long selectedMillis=EventTime.toMillis(selectedDate,LocalTime.of(5,49),shanghai);
        check(EventTime.date(selectedMillis,shanghai).equals(selectedDate),"event date survives wall-time round trip");
        check(EventTime.time(selectedMillis,shanghai).equals(LocalTime.of(5,49)),"05:49 survives wall-time round trip");
        check(EventTime.endAfterHour(selectedDate,LocalTime.of(23,49)).equals(LocalDateTime.of(2026,9,14,0,49)),"default end crosses midnight by one hour");
        e=new CalendarEvent(); e.system=true; e.reminderMinutes=-1; e.appReminderMinutes=5; e.reminderArt=7; e.start=selectedMillis; e.end=selectedMillis+3600000; Occurrence systemOccurrence=new Occurrence(e,e.start,e.end);
        check(ReminderTime.trigger(systemOccurrence,shanghai,e.appReminderMinutes)==selectedMillis-5*60000L,"system event uses the independent app reminder time");
        check(e.copy().appReminderMinutes==5&&e.copy().reminderArt==7,"independent reminder settings survive event copy");
        Random sequence=new Random() { private int value; @Override public int nextInt(int bound) { return value++; } };
        check(ReminderArtwork.randomIndex(sequence)==0&&ReminderArtwork.randomIndex(sequence)==1,"random artwork mode chooses a fresh image for each build");
        long now=selectedMillis+12*3600000L;
        check(!ReminderSchedule.stale(now+5*60000L,now),"future event is not stale when its reminder point has passed");
        check(!ReminderSchedule.stale(now-23*3600000L,now),"recently started event can still deliver a missed reminder");
        check(ReminderSchedule.stale(now-25*3600000L,now),"event older than one day is stale");
        budget();
        monthRows();
        widgetBudget();
        palette();
        System.out.println("PASS: "+assertions+" assertions (2026 grids, leap years, recurrence, DST, all-day dates, boundaries, validation, monthly accent palette, first-screen budget, widget budget)");
    }
    /**
     * 逐月主题色检查：直接从 Ui.java 读 ACCENTS，确保 1) 恰好 13 个取值；2) 13 个取值互不相同；
     * 3) 每个取值对深色底色的对比度都达到 4.5:1（WCAG AA 正文标准），深色文字压在主题色上同样达标。
     */
    /**
     * 首屏高度预算。三件事必须同时成立，否则首页又会退化成"一张被裁的图加半屏格子"：
     * 1) 主流机型上插画零裁切；2) 不用滚动就能看到整月 6 行格子；3) 插画只在屏不够高时才被裁，
     * 且永远保留下限高度。
     *
     * 传进来的高度是**中段可用高度**（屏幕扣掉顶条与底部导航，插画面板不在其中，它是钉在
     * 中段上方的一整块）；chrome 是星期行 20dp 加"日期 · 状态 · 新建"合并行 24dp。
     * 所以不变量是 art + chrome + 6×cell + agenda == 中段可用高度，一个精确的等式而不是
     * "别溢出"——等式一旦破了，就说明有一行被挤到屏幕外面去了。
     */
    private static void budget() {
        float aspect=0.789f, tolerance=0.001f;
        int chrome=44;

        FitBudget.Plan normal=FitBudget.plan(411,772,aspect,chrome);
        check(normal.artHeight==521,"1080x2400 art panel is the zero-crop height");
        check(normal.artHeight==FitBudget.zeroCropHeight(411,aspect),"zero-crop helper agrees with the plan");
        check(Math.abs(normal.artVisible-1f)<tolerance,"1080x2400 illustration loses nothing");
        check(normal.cellHeight==34,"1080x2400 date cell height");
        check(normal.agendaHeight==3,"1080x2400 leaves the agenda the tail of the first screen");
        check(!normal.needsScroll,"1080x2400 whole month visible without scrolling");
        check(normal.artHeight+chrome+normal.cellHeight*FitBudget.ROWS+normal.agendaHeight==772,"1080x2400 budget adds up exactly");

        FitBudget.Plan shortScreen=FitBudget.plan(411,589,aspect,chrome);
        check(shortScreen.artHeight==353,"1080x1920 illustration yields to the grid");
        check(Math.abs(shortScreen.artVisible-0.6775f)<tolerance,"1080x1920 keeps about 68% of the illustration");
        check(shortScreen.cellHeight==FitBudget.MIN_CELL,"1080x1920 grid shrinks to the floor, not below");
        check(!shortScreen.needsScroll,"1080x1920 still shows the whole month without scrolling");
        check(shortScreen.agendaHeight==0,"1080x1920 agenda waits below the fold");

        FitBudget.Plan tall=FitBudget.plan(411,797,aspect,chrome);
        check(tall.artHeight==521&&Math.abs(tall.artVisible-1f)<tolerance,"1440x3120 also zero-crop");
        check(tall.cellHeight==38,"1440x3120 spends spare height on the grid");

        FitBudget.Plan tablet=FitBudget.plan(800,1186,aspect,chrome);
        check(tablet.artHeight==950&&tablet.cellHeight==FitBudget.MIN_CELL,"tablet stops cropping once the grid is at its floor");
        check(Math.abs(tablet.artVisible-0.9369f)<tolerance,"tablet keeps about 94%");

        FitBudget.Plan narrow=FitBudget.plan(320,772,aspect,chrome);
        check(narrow.artHeight==406&&Math.abs(narrow.artVisible-1f)<tolerance,"narrow screen gets a full illustration");
        check(narrow.cellHeight==FitBudget.MAX_CELL,"narrow screen can afford the tallest cells");
        check(narrow.agendaHeight>0,"narrow screen shows part of the agenda on the first screen");

        FitBudget.Plan cramped=FitBudget.plan(411,400,aspect,chrome);
        check(cramped.artHeight==FitBudget.MIN_ART,"cramped screen keeps the illustration floor");
        check(cramped.needsScroll,"cramped screen is the only case allowed to scroll");
        check(cramped.cellHeight==FitBudget.MIN_CELL,"cramped screen keeps tappable cells");

        for(int height:new int[]{0,-1,1}) check(FitBudget.plan(411,height,aspect,chrome).needsScroll,"degenerate height "+height+" degrades safely");
        for(int width:new int[]{0,-5}) check(FitBudget.plan(width,772,aspect,chrome).needsScroll,"degenerate width "+width+" degrades safely");
        for(float bad:new float[]{0f,-1f}) check(FitBudget.plan(411,772,bad,chrome).needsScroll,"degenerate aspect "+bad+" degrades safely");
        check(FitBudget.plan(411,772,aspect,2000).needsScroll,"chrome taller than the screen degrades safely");

        // 高度区间上的不变量：任何一档都不许出现"格子高到没法点""插画被裁没""预算加起来超一行"。
        for(int height=300;height<=1500;height+=5) {
            FitBudget.Plan plan=FitBudget.plan(411,height,aspect,chrome);
            check(plan.cellHeight>=FitBudget.MIN_CELL&&plan.cellHeight<=FitBudget.MAX_CELL,"cell height stays tappable at height "+height);
            check(plan.artVisible>0f&&plan.artVisible<=1f,"illustration visible ratio stays in (0,1] at height "+height);
            check(plan.artHeight>=Math.min(FitBudget.MIN_ART,Math.max(0,height-chrome)),"illustration floor honoured at height "+height);
            check(plan.needsScroll||plan.artHeight+chrome+plan.cellHeight*FitBudget.ROWS+plan.agendaHeight==height,"budget closes exactly at height "+height);
        }
    }
    /**
     * 渲染行数：这个月要画几行格子。核心要求是"本月的每一天都在网格里"——少一行就意味着
     * 月末那一周在首页上看不到也点不到。
     *
     * 这个 bug 真实发生过：行数按"grid 第一格的列号"算（恒为 0，等于只看月份天数），于是
     * 2026 年 2 月只画到 22 日、3 月到 29 日、8 月到 30 日、11 月到 29 日。旧版一直只用
     * 9 月做验证，而 9 月恰好不受影响，所以一直没暴露。下面除了点名这四个月，还做一遍
     * 1900-2400 的全量核对，避免只在"当前这个月"上碰巧正确。
     */
    private static void monthRows() {
        for(int year:new int[]{2026}) {
            check(CalendarMath.rows(YearMonth.of(year,2),true)==5,"2026-02 needs five rows (was four: the 28th was cut off)");
            check(CalendarMath.rows(YearMonth.of(year,3),true)==6,"2026-03 needs six rows");
            check(CalendarMath.rows(YearMonth.of(year,8),true)==6,"2026-08 needs six rows");
            check(CalendarMath.rows(YearMonth.of(year,11),true)==6,"2026-11 needs six rows (was five: the 30th was cut off)");
            check(CalendarMath.rows(YearMonth.of(year,9),true)==5,"2026-09 needs five rows");
        }
        check(CalendarMath.rows(YearMonth.of(2026,2),false)!=CalendarMath.rows(YearMonth.of(2026,2),true),"week start changes the row count in February 2026");
        int broken=0,outOfRange=0;
        for(int year=1900;year<=2400;year++) for(int month=1;month<=12;month++) for(boolean monday:new boolean[]{true,false}) {
            YearMonth target=YearMonth.of(year,month);
            int rows=CalendarMath.rows(target,monday);
            List<LocalDate> grid=CalendarMath.grid(target,monday);
            int cells=rows*7;
            if(cells<1||cells>42) outOfRange++;
            // 条件一：画下的最后一格不能早于本月最后一天（否则月末被漏掉）。
            if(grid.get(cells-1).isBefore(target.atEndOfMonth())) broken++;
            // 条件二：再少一行就装不下本月了（否则说明多画了一行，也不是最小行数）。
            if(rows>1&&!grid.get((rows-1)*7-1).isBefore(target.atEndOfMonth())) broken++;
        }
        check(broken==0,"1900-2400 全量核对：没有任何一个月被少画一行（越界 "+outOfRange+" 处，漏画 "+broken+" 处）");
        check(outOfRange==0,"1900-2400 的行数都在 1-6 之间");
    }
    /**
     * 桌面卡片预算：插画块拿多少、日期区留多少、插画能露出自己高度的几成。
     *
     * 这组数字决定桌面上看到的是角色还是一条横带，所以除了点值断言，还做两件事：
     * 1) 属性循环（宽高两段区间）：不溢出、可见比例在 [0,1]、更高的卡片不会露更少插画、
     *    竖条的宽高比一旦达到插画宽高比就必然零裁切；
     * 2) 与布局 XML 的一致性：MONTH_DATE_BLOCK_DP 必须等于 widget_month.xml 里各段高度之和，
     *    声明尺寸必须真的是 6 格高——这两个数一旦各改各的，今日安排就会被挤出卡片。
     */
    private static void widgetBudget() {
        float aspect=0.789f, tolerance=0.001f;

        // 点值：四档卡片（内容区已扣 20dp padding）+ 4x6 宽卡。
        WidgetBudget.Plan small=WidgetBudget.monthCard(320,330,aspect);
        check(small.zeroCropSize==406,"4x4 zero-crop needs 406dp at 320dp wide");
        check(small.artHeight==106,"4x4 art block leaves room for today's row");
        check(Math.abs(small.artVisible-0.261f)<tolerance,"4x4 shows about 26% of the illustration");
        check(!small.degraded,"4x4 still fits the date block");

        WidgetBudget.Plan five=WidgetBudget.monthCard(320,460,aspect);
        check(five.artHeight==236&&Math.abs(five.artVisible-0.582f)<tolerance,"4x5 keeps the original calendar above today's row");

        WidgetBudget.Plan six=WidgetBudget.monthCard(320,540,aspect);
        check(six.artHeight==316&&Math.abs(six.artVisible-0.779f)<tolerance,"4x6 keeps a dedicated today's row");

        // 零裁切只在一个高度上成立（剩余高度恰好等于零裁切高度）；再高就转为裁两侧。
        // 零裁切的真实高度是 405.576dp，整数 dp 落不准；最接近的 406dp 上块只差千分之一，视为未裁。
        WidgetBudget.Plan exact=WidgetBudget.monthCard(320,630,aspect);
        check(exact.artHeight==406&&Math.abs(exact.artVisible-1f)<0.002,"4x6.5 is within half a dp of zero-crop");
        WidgetBudget.Plan beyond=WidgetBudget.monthCard(320,644,aspect);
        check(beyond.artHeight==420&&Math.abs(beyond.artVisible-0.966f)<tolerance,"past zero-crop the sides crop instead (97%)");

        WidgetBudget.Plan wide=WidgetBudget.monthCard(400,460,aspect);
        check(wide.zeroCropSize==507&&wide.artHeight==236&&Math.abs(wide.artVisible-0.465f)<tolerance,
                "a wider card needs more height for the same visible fraction");

        // 今日安排卡片：竖条 40% 宽、整条高；比旧版更宽，避免角色贴着分界线被裁掉。
        WidgetBudget.Plan strip=WidgetBudget.agendaCard(316,146,aspect);
        check(strip.artWidth==126&&strip.artHeight==146,"agenda art strip is 126x146dp at a 340x170 card");
        check(Math.abs(strip.artVisible-0.915f)<tolerance,"agenda strip shows about 92% of the artwork width");
        WidgetBudget.Plan tallStrip=WidgetBudget.agendaCard(316,226,aspect);
        check(Math.abs(tallStrip.artVisible-0.707f)<tolerance,"a taller agenda card still keeps more of the artwork width");
        WidgetBudget.Plan wideStrip=WidgetBudget.agendaCard(400,150,aspect);
        check(Math.abs(wideStrip.artVisible-aspect/(wideStrip.artWidth/(double)wideStrip.artHeight))<tolerance,
                "a wide agenda strip follows the same crop geometry");

        // 退化输入。
        check(WidgetBudget.monthCard(320,170,aspect).degraded,"a card shorter than the date block is degraded");
        check(WidgetBudget.monthCard(320,170,aspect).artHeight==0,"degraded card gives the art block nothing");
        for(int bad:new int[]{0,-40}) {
            WidgetBudget.Plan degenerate=WidgetBudget.monthCard(bad,460,aspect);
            check(degenerate.artWidth==0&&degenerate.zeroCropSize==0,"degenerate width "+bad+" yields a zero-width block");
            check(degenerate.artVisible==1f,"degenerate width "+bad+" treats the block as uncropped");
            check(WidgetBudget.agendaCard(bad,146,aspect).degraded,"degenerate width "+bad+" degrades the agenda card");
        }
        for(float bad:new float[]{0f,-1f}) check(WidgetBudget.monthCard(320,460,bad).zeroCropSize==0,"degenerate aspect "+bad+" yields no zero-crop reference");
        check(WidgetBudget.monthCard(320,460,0f).artVisible==1f&&WidgetBudget.monthCard(320,460,-1f).artVisible==1f,"degenerate aspect treats the block as uncropped");
        check(WidgetBudget.zeroCropHeight(0,aspect)==0&&WidgetBudget.zeroCropHeight(-1,aspect)==0,"zeroCropHeight guards its inputs");

        // 属性循环：任何尺寸都不许溢出、不许倒退。
        for(int width=40;width<=500;width+=20) {
            for(int height=40;height<=700;height+=20) {
                WidgetBudget.Plan plan=WidgetBudget.monthCard(width,height,aspect);
                check(plan.artHeight==Math.max(0,height-WidgetBudget.MONTH_DATE_BLOCK_DP),"month art block equals the leftover at "+width+"x"+height);
                check(plan.artVisible>=0f&&plan.artVisible<=1f,"month visible ratio in [0,1] at "+width+"x"+height);
                check(plan.degraded==(height<WidgetBudget.MONTH_DATE_BLOCK_DP),"degraded iff the date block does not fit at "+width+"x"+height);
                // 可见比例按被裁的轴算，对照实数零裁切高度（width/aspect），避免 zeroCropSize 取整带来的假差异。
                double exactZeroCrop=width/(double)aspect;
                if(plan.artHeight>exactZeroCrop) check(Math.abs(plan.artVisible-exactZeroCrop/plan.artHeight)<tolerance,"narrower-than-artwork block crops the sides at "+width+"x"+height);
                else if(plan.artHeight<exactZeroCrop&&plan.artHeight>0) check(Math.abs(plan.artVisible-aspect*plan.artHeight/width)<tolerance,"wider-than-artwork block crops the height at "+width+"x"+height);
                else if(plan.artHeight>0) check(Math.abs(plan.artVisible-1f)<tolerance,"the exact zero-crop block is uncropped at "+width+"x"+height);
                WidgetBudget.Plan taller=WidgetBudget.monthCard(width,height+20,aspect);
                check(taller.artHeight>=plan.artHeight,"a taller card never gets a smaller block at "+width+"x"+height);
                WidgetBudget.Plan loopStrip=WidgetBudget.agendaCard(width,height,aspect);
                check(loopStrip.artWidth==Math.round(width*WidgetBudget.AGENDA_ART_SHARE)&&loopStrip.artHeight==height,"agenda strip size at "+width+"x"+height);
                check(loopStrip.artVisible>=0f&&loopStrip.artVisible<=1f,"agenda visible ratio in [0,1] at "+width+"x"+height);
                // 竖条与月历卡同一几何：块比插画宽时裁高度（a/b），窄时裁宽度（b/a），恰等时未裁。
                double stripAspect=loopStrip.artWidth/(double)Math.max(1,loopStrip.artHeight);
                if(stripAspect>aspect) check(Math.abs(loopStrip.artVisible-aspect/stripAspect)<tolerance,"wider strip crops its height at "+width+"x"+height);
                else if(stripAspect<aspect) check(Math.abs(loopStrip.artVisible-stripAspect/aspect)<tolerance,"narrower strip crops its width at "+width+"x"+height);
                else check(Math.abs(loopStrip.artVisible-1f)<tolerance,"the exact strip is uncropped at "+width+"x"+height);
            }
        }

        // 与布局 XML 的一致性（同 palette() 读源码文本的做法）。
        String monthLayout=readText("app/src/main/res/layout/widget_month.xml");
        int titleRow=28,weekdayRow=16,cell=20,footer=16,today=WidgetBudget.MONTH_TODAY_DP,rows=6;
        check(monthLayout.contains("android:layout_height=\""+titleRow+"dp\""),"month card title row is "+titleRow+"dp");
        check(monthLayout.contains("android:layout_height=\""+weekdayRow+"dp\""),"month card weekday row is "+weekdayRow+"dp");
        check(monthLayout.contains("android:layout_height=\""+(cell*rows)+"dp\""),"month card grid is "+(cell*rows)+"dp ("+rows+" x "+cell+"dp)");
        check(monthLayout.contains("android:layout_height=\""+footer+"dp\""),"month card footer is "+footer+"dp");
        check(monthLayout.contains("android:id=\"@+id/widget_today\"")&&monthLayout.contains("android:layout_height=\""+today+"dp\""),"month card reserves a "+today+"dp today's row");
        check(monthLayout.contains("android:id=\"@+id/widget_today_events\""),"month card has a today's events container");
        check(monthLayout.contains("android:padding=\"10dp\""),"month card padding is 10dp");
        check(monthLayout.contains("android:layout_height=\"0dp\"")&&monthLayout.contains("android:layout_weight=\"1\""),
                "the art block takes the leftover height by weight");
        check(WidgetBudget.MONTH_DATE_BLOCK_DP==titleRow+weekdayRow+cell*rows+footer+today,
                "MONTH_DATE_BLOCK_DP equals the sum of the fixed rows in widget_month.xml");
        String provider=readText("app/src/main/res/xml/widget_month.xml");
        check(provider.contains("android:minHeight=\"560dp\""),"the month widget declares a 560dp minimum height");
        check(provider.contains("android:targetCellHeight=\"6\""),"the month widget asks for six cells of height");
        String widgetsSource=readText("app/src/main/java/com/amphoreus/calendar/widget/Widgets.java");
        check(!widgetsSource.contains("getBoolean(\"events_\"+id,false)"),"all widget types default to showing events");
        String dayLayout=readText("app/src/main/res/layout/widget_day.xml");
        check(dayLayout.contains("android:textSize=\"11sp\"")&&dayLayout.contains("android:layout_height=\"5dp\""),
                "widget_day numbers are 11sp with a 5dp dot (20dp cells)");
        // 月历插画用 centerCrop 保持铺满且不变形；今日安排插画用 fitCenter，启动器上报尺寸
        // 与实际卡片尺寸不一致时保留整张焦点图，不能再做第二次横向裁切。
        for(String layout:new String[]{"widget_month","widget_agenda"}) {
            String text=readText("app/src/main/res/layout/"+layout+".xml");
            int art=text.indexOf("android:id=\"@+id/widget_art\"");
            check(art>0,"widget_art exists in "+layout);
            String line=text.substring(art,Math.min(text.length(),art+400));
            String scaleType=layout.equals("widget_agenda")?"fitCenter":"centerCrop";
            check(line.contains("scaleType=\""+scaleType+"\""),"widget_art uses "+scaleType+" in "+layout);
        }
        String agendaLayout=readText("app/src/main/res/layout/widget_agenda.xml");
        check(agendaLayout.contains("android:layout_weight=\"4\"")&&agendaLayout.contains("android:layout_weight=\"6\""),
                "agenda art lane keeps the wider 4:6 split");
    }
    /** 读项目里的一个文本文件，供与源码/布局做一致性断言。 */
    private static String readText(String relative) {
        java.io.File file=new java.io.File("../"+relative);
        if(!file.isFile()) file=new java.io.File(relative);
        try { return new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8); }
        catch(java.io.IOException ex) { throw new AssertionError("cannot read "+relative+": "+ex.getMessage()); }
    }
    private static void palette() {
        java.io.File source=new java.io.File("../app/src/main/java/com/amphoreus/calendar/ui/Ui.java");
        if(!source.isFile()) source=new java.io.File("app/src/main/java/com/amphoreus/calendar/ui/Ui.java");
        String text;
        try { text=new String(java.nio.file.Files.readAllBytes(source.toPath()),java.nio.charset.StandardCharsets.UTF_8); }
        catch(java.io.IOException ex) { throw new AssertionError("cannot read Ui.java: "+ex.getMessage()); }
        int from=text.indexOf("ACCENTS={"),to=text.indexOf("};",from);
        check(from>0&&to>from,"Ui.ACCENTS block found");
        List<Integer> accents=new ArrayList<>();
        for(String literal:text.substring(from+"ACCENTS={".length(),to).replace("\n","").split(",")) {
            String value=literal.trim();
            if(!value.isEmpty()) accents.add((int)Long.parseLong(value.replace("0x","").replace("L",""),16));
        }
        check(accents.size()==13,"13 monthly accents, got "+accents.size());
        int background=0xff10111c;
        for(int month=1;month<=12;month++) {
            int accent=accents.get(month);
            check(contrast(accent,background)>=4.5,"month "+month+" accent readable on the dark background");
            check(contrast(accent,background)>=4.5,"dark text stays readable on month "+month+" accent");
            for(int other=0;other<13;other++) check(other==month||accent!=accents.get(other),"entries "+month+" and "+other+" share an accent");
        }
    }
    private static double contrast(int first,int second) {
        double a=luminance(first),b=luminance(second),hi=Math.max(a,b),lo=Math.min(a,b);
        return (hi+0.05)/(lo+0.05);
    }
    private static double luminance(int color) {
        return 0.2126*channel((color>>16)&0xff)+0.7152*channel((color>>8)&0xff)+0.0722*channel(color&0xff);
    }
    private static double channel(int value) {
        double v=value/255.0;
        return v<=0.03928? v/12.92 : Math.pow((v+0.055)/1.055,2.4);
    }
}
