package com.amphoreus.calendar.core;

/**
 * 桌面卡片的高度预算：插画块拿多少、日期区留多少、插画能露出自己高度的几成。
 *
 * 放在 core 里是因为这组数字容易被"看着差不多"糊过去——它们直接决定用户在桌面上看到的
 * 是角色还是一条横带。纯函数、不碰 Android，能对一整个尺寸区间做不变量断言（见 CoreTests）。
 *
 * 约束来自 RemoteViews：API 31 以下不能改某个 View 的高度或 margin，但可以让一个块吃权重。
 * 所以月历卡片的布局写成"插画块 weight=1 吃掉全部剩余高度 + 日期区固定高度"——
 * 插画优先不需要任何动态布局参数，卡片越高，多出来的高度全归插画。
 */
public final class WidgetBudget {
    /** 月历卡片末尾的今日安排行高度（dp）。 */
    public static final int MONTH_TODAY_DP=44;
    /**
     * 月历卡片日期区的固定高度（dp）：标题行 28 + 星期行 16 + 6 行 x 20dp 格子 + 页脚 16 + 今日安排 44。
     * 必须与 widget_month.xml 里的尺寸逐项一致——那边改了这边也要改，CoreTests 会读
     * 两边来核对（同 CoreTests.palette() 读 Ui.java 的做法）。
     */
    public static final int MONTH_DATE_BLOCK_DP=224;
    /** 月历卡片根布局的纵向 padding（widget_month.xml 的 10dp x 2）。 */
    public static final int MONTH_PADDING_DP=20;
    /** 今日安排卡片里插画竖条占内容宽度的比例（widget_agenda.xml 里 weight 4 : 6）。 */
    public static final float AGENDA_ART_SHARE=0.40f;
    /** 今日安排卡片根布局的纵向 padding（12dp x 2）。 */
    public static final int AGENDA_PADDING_DP=24;

    /** 一次预算的结果，字段都是最终值。 */
    public static final class Plan {
        /** 插画块应画的尺寸（dp）。位图按它乘 density 生成。 */
        public final int artWidth,artHeight;
        /** 块的宽高比恰好等于插画宽高比时需要的高度（dp）；小于它说明插画被裁了。 */
        public final int zeroCropSize;
        /** 插画可见比例 0..1，按被裁的那个轴算：块更宽时是高度的比，块更窄时是宽度的比。 */
        public final float artVisible;
        /** true 表示卡片矮到日期区都放不下（布局会把日期区截掉一部分）。 */
        public final boolean degraded;

        Plan(int artWidth,int artHeight,int zeroCropSize,float artVisible,boolean degraded) {
            this.artWidth=artWidth; this.artHeight=artHeight; this.zeroCropSize=zeroCropSize;
            this.artVisible=artVisible; this.degraded=degraded;
        }
    }

    /** 插画按宽度铺开、零裁切时需要的高度（dp）。 */
    public static int zeroCropHeight(int width,float artworkAspect) {
        if(width<=0||artworkAspect<=0f) return 0;
        return Math.round(width/artworkAspect);
    }

    /** 插画可见比例：块与插画在宽高比上差多少，就沿被裁的轴保留多少。任意一方为 0 时按全可见处理。 */
    static float visibleFraction(int blockWidth,int blockHeight,float artworkAspect) {
        if(blockWidth<=0||blockHeight<=0||artworkAspect<=0f) return 1f;
        float blockAspect=blockWidth/(float)blockHeight;
        return Math.min(1f,Math.min(blockAspect,artworkAspect)/Math.max(blockAspect,artworkAspect));
    }

    /**
     * 月历卡片：日期区固定，插画拿剩余的全部。
     *
     * 插画块高度**不钳制**：剩余高度小于零裁切高度时块是"更宽"的，裁高度（对准质心）；
     * 剩余高度大于零裁切高度时块反而"更窄"，裁宽度——位图始终等于块尺寸，fitXY 才不会
     * 把画面拉变形。可见比例按被裁的那个轴算，两种情况统一成"块宽高比与插画宽高比里
     * 较小者比较大者"。
     *
     * @param innerWidthDp  内容区宽（卡片宽减去横向 padding）
     * @param innerHeightDp 内容区高（卡片高减去纵向 padding）
     * @param artworkAspect 插画区段宽高比（Art.aspect(month)）
     */
    public static Plan monthCard(int innerWidthDp,int innerHeightDp,float artworkAspect) {
        int width=Math.max(0,innerWidthDp);
        int zeroCrop=zeroCropHeight(width,artworkAspect);
        int artHeight=Math.max(0,innerHeightDp-MONTH_DATE_BLOCK_DP);
        return new Plan(width,artHeight,zeroCrop,visibleFraction(width,artHeight,artworkAspect),innerHeightDp<MONTH_DATE_BLOCK_DP);
    }

    /**
     * 今日安排卡片：插画是左侧一根竖条（宽 = 内容宽的 AGENDA_ART_SHARE，高 = 内容高）。
     * 竖条通常仍比插画窄，窗口是"整条高度 + 一段主体宽度"，可见比例按被裁的轴算。
     */
    public static Plan agendaCard(int innerWidthDp,int innerHeightDp,float artworkAspect) {
        int width=Math.max(0,innerWidthDp),height=Math.max(0,innerHeightDp);
        int artWidth=Math.round(width*AGENDA_ART_SHARE);
        int artHeight=height;
        int zeroCrop=zeroCropHeight(artWidth,artworkAspect);
        return new Plan(artWidth,artHeight,zeroCrop,visibleFraction(artWidth,artHeight,artworkAspect),width<=0||height<=0);
    }

    private WidgetBudget() {}
}
