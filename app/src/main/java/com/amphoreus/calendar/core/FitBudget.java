package com.amphoreus.calendar.core;

/**
 * 首页首屏的高度预算：插画占多高、日期格每行多高、日程区还能露出多少。
 *
 * 放在 core 里而不是 Activity 里，是因为这套算法容易被"看着差不多"糊过去——它决定了
 * 用户第一眼看到的是"上半一张海报、下半一个整月"，还是"一张被裁的图加半屏格子"。
 * 纯函数、不碰 Android，所以能对一整个高度区间做不变量断言（见 CoreTests）。
 *
 * 预算的优先级是明确的，从高到低：
 *   1. 插画零裁切（高度 = 宽度 ÷ 插画宽高比）
 *   2. 日期格至少 MIN_CELL 一行，保证 6 行整月在首屏可见
 *   3. 插画可以被裁，但有 MIN_ART 的下限，不能裁到没有
 *   4. 日程列表排在最后，放不下就落到折线下面（它本来就是滚动区）
 *   5. 只有连"MIN_ART 插画 + 6 行 MIN_CELL 格子"都放不下时，才允许整页滚动
 */
public final class FitBudget {
    /** 日期格每行的高度下限：低于这个值手指点不准，宁可让插画裁一点。 */
    public static final int MIN_CELL=32;
    /** 日期格每行的上限：再高就是浪费，多出来的高度应该给日程列表。 */
    public static final int MAX_CELL=44;
    /** 插画面板的高度下限：再怎么挤也要留住这么高的插画，否则首页就不像"海报"了。 */
    public static final int MIN_ART=200;
    /** 一个月的日期网格固定 6 行（含上下月溢出的日期），与 CalendarMath.grid 的 42 格一致。 */
    public static final int ROWS=6;

    /** 一次预算的结果。字段都是最终值，不做二次修改。 */
    public static final class Plan {
        /** 插画面板应占的高度。 */
        public final int artHeight;
        /** 日期格每行的高度，范围 [MIN_CELL, MAX_CELL]。 */
        public final int cellHeight;
        /** 插画可见比例 0..1；等于 1 表示零裁切。 */
        public final float artVisible;
        /** 首屏内日程列表可见的高度，可能是 0（完全在折线以下）。 */
        public final int agendaHeight;
        /** true 表示首屏装不下"最小插画 + 整月格子"，页面需要滚动。 */
        public final boolean needsScroll;

        Plan(int artHeight,int cellHeight,float artVisible,int agendaHeight,boolean needsScroll) {
            this.artHeight=artHeight; this.cellHeight=cellHeight; this.artVisible=artVisible;
            this.agendaHeight=agendaHeight; this.needsScroll=needsScroll;
        }
    }

    /** 零裁切时插画面板的高度：宽度 ÷ 插画宽高比。调用方按这个值给面板高度即可一像素不丢。 */
    public static int zeroCropHeight(int width,float artworkAspect) {
        if(width<=0||artworkAspect<=0f) return 0;
        return Math.round(width/artworkAspect);
    }

    /**
     * 算一次首页预算。
     *
     * @param width      可用宽度（dp）
     * @param height     可用高度（dp），已扣掉系统栏
     * @param artworkAspect 插画区宽高比（宽 ÷ 高），来自 Art.aspect(month)
     * @param chromeHeight 插画与日期格之外那些固定条的总高：顶条、星期行、底部导航
     */
    public static Plan plan(int width,int height,float artworkAspect,int chromeHeight) {
        if(width<=0||height<=0||artworkAspect<=0f) return new Plan(0,MIN_CELL,1f,0,true);
        int available=height-Math.max(0,chromeHeight);
        int gridFloor=MIN_CELL*ROWS;
        if(available<=0) return new Plan(0,MIN_CELL,1f,0,true);

        int naturalArt=zeroCropHeight(width,artworkAspect);
        int artHeight=naturalArt;
        boolean needsScroll=false;
        if(naturalArt>available-gridFloor) {
            // 零裁切放不下：先让插画退到"刚好留住 MIN_CELL 一行"的位置。
            artHeight=available-gridFloor;
            if(artHeight<MIN_ART) {
                // 连插画下限都快保不住了：保住 MIN_ART，让格子去滚动。
                artHeight=Math.min(MIN_ART,available);
                needsScroll=available-artHeight<gridFloor;
            }
        }
        int leftover=Math.max(0,available-artHeight);
        int cellHeight=Math.min(MAX_CELL,Math.max(MIN_CELL,leftover/ROWS));
        int agendaHeight=Math.max(0,leftover-cellHeight*ROWS);
        float visible=naturalArt<=0?1f:Math.min(1f,artHeight/(float)naturalArt);
        return new Plan(artHeight,cellHeight,visible,agendaHeight,needsScroll);
    }

    private FitBudget() {}
}
