package com.amphoreus.calendar.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/**
 * 日期格的画笔：用海报下方那张印刷日期表的语言，而不是一张张圆角小卡片。
 *
 * 从原图上量到的印刷表是这样的：有左右外框和列间的竖向分隔线，**没有横向格线**，
 * 数字是小号衬线体、对比度压得很低，整张表几乎是"一张纸上的网格"。旧的实现画的是
 * 9dp 圆角、四边描边的小方块，和海报本身没有任何关系，所以上下拼在一起不像同一张纸
 * ——这一版把圆角和四边框去掉，只留竖分隔线。
 *
 * 选中与今天仍然要能被一眼认出来（那是交互，不是印刷），所以：选中＝主题色直角实心块，
 * 今天＝主题色直角描边。两者都保持直角，不破坏印刷感。
 */
final class GridCellDrawable extends Drawable {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline=new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 分隔线刻意不开抗锯齿：一条 1px 的线开了抗锯齿会被摊成两列半透明像素，看着发虚也数不清。 */
    private final Paint rule=new Paint();
    private final int accent;
    private final float ruleWidth,outlineWidth;
    private boolean selected,today,outside,leftRule,rightRule;

    GridCellDrawable(int accent,float density) {
        this.accent=accent;
        ruleWidth=Math.max(1f,density*1f);
        outlineWidth=Math.max(1f,density*1.5f);
        fill.setStyle(Paint.Style.FILL); fill.setColor(accent);
        outline.setStyle(Paint.Style.STROKE); outline.setColor(accent); outline.setStrokeWidth(outlineWidth);
        rule.setStyle(Paint.Style.STROKE); rule.setColor(accent); rule.setStrokeWidth(ruleWidth);
    }
    GridCellDrawable selected(boolean value) { selected=value; return this; }
    GridCellDrawable today(boolean value) { today=value; return this; }
    GridCellDrawable outside(boolean value) { outside=value; return this; }
    GridCellDrawable leftRule(boolean value) { leftRule=value; return this; }
    GridCellDrawable rightRule(boolean value) { rightRule=value; return this; }

    @Override public void draw(Canvas canvas) {
        Rect b=getBounds(); if(b.isEmpty()) return;
        if(selected) {
            // 整格填满主题色，压掉分隔线：选中是交互状态，视觉上必须比印刷线更强。
            canvas.drawRect(b.left,b.top,b.right,b.bottom,fill);
            return;
        }
        if(!outside) {
            // 上下月的日期不参与"表格"：它们淡化显示，也不该有分隔线把表格画到本月之外。
            rule.setAlpha(70);
            if(leftRule) canvas.drawLine(b.left+ruleWidth/2f,b.top,b.left+ruleWidth/2f,b.bottom,rule);
            if(rightRule) canvas.drawLine(b.right-ruleWidth/2f,b.top,b.right-ruleWidth/2f,b.bottom,rule);
        }
        if(today) {
            float inset=outlineWidth/2f;
            canvas.drawRect(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset,outline);
        }
    }

    @Override public void setAlpha(int value) { fill.setAlpha(value); outline.setAlpha(value); rule.setAlpha(value); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); outline.setColorFilter(filter); rule.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
