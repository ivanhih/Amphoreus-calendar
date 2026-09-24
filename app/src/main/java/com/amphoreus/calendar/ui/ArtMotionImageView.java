package com.amphoreus.calendar.ui;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/**
 * 在静态月历插画上叠加很轻的、按月份变化的动态层。
 *
 * 原图仍由 ImageView 按原来的尺寸绘制，不做缩放、裁切或变形；动效只是裁剪区内的透明
 * 光晕和少量粒子，所以海报本身的构图不会因为动画而跳动。桌面小组件继续使用静态图，
 * 避免 RemoteViews 持续刷新带来的耗电。
 */
public final class ArtMotionImageView extends ImageView {
    private static final float TAU=(float)(Math.PI*2.0);
    private static final int STYLE_EMBER=0,STYLE_ORBIT=1,STYLE_NIGHT=2,STYLE_SEED=3,
        STYLE_PETAL=4,STYLE_SUN=5,STYLE_WIND=6,STYLE_LEAF=7,STYLE_THREAD=8,STYLE_MIST=9;
    private static final float[] SEED_X={.08f,.19f,.31f,.44f,.57f,.69f,.82f,.14f,.38f,.63f,.76f,.91f};
    private static final float[] SEED_Y={.16f,.27f,.42f,.57f,.73f,.84f,.23f,.66f,.35f,.78f,.49f,.09f};
    private static final float[] SEED_SIZE={.7f,1.1f,.8f,1.4f,.9f,1.2f,.7f,1.0f,1.5f,.8f,1.2f,.9f};
    private static final long MOTION_PERIOD_MS=14000L,FRAME_DELAY_MS=16L;

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path path=new Path();
    private boolean framePosted;
    private long motionStartMs;
    private float phase;
    private int month=1;
    private boolean motionEnabled=true;
    private final Runnable motionFrame=new Runnable() {
        @Override public void run() {
            if(!motionEnabled||!isAttachedToWindow()||getVisibility()!=View.VISIBLE||getWindowVisibility()!=View.VISIBLE) {
                framePosted=false; return;
            }
            long elapsed=SystemClock.uptimeMillis()-motionStartMs;
            phase=(elapsed%MOTION_PERIOD_MS)/(float)MOTION_PERIOD_MS;
            postInvalidateOnAnimation(); postDelayed(this,FRAME_DELAY_MS);
        }
    };

    public ArtMotionImageView(Context context) { super(context); initialize(); }
    public ArtMotionImageView(Context context,AttributeSet attrs) { super(context,attrs); initialize(); }
    public ArtMotionImageView(Context context,AttributeSet attrs,int style) { super(context,attrs,style); initialize(); }

    private void initialize() {
        setWillNotDraw(false);
    }

    /** 设置当月图像，并从该月动效的初始相位开始。 */
    public void setArtwork(Bitmap bitmap,int month) {
        this.month=Math.max(1,Math.min(12,month));
        phase=0f;
        motionStartMs=SystemClock.uptimeMillis();
        setImageBitmap(bitmap);
        invalidate();
    }

    public void setMotionEnabled(boolean enabled) {
        if(motionEnabled==enabled)return;
        motionEnabled=enabled;
        if(enabled) startMotion(); else pauseMotion();
        invalidate();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startMotion(); }
    @Override protected void onDetachedFromWindow() { pauseMotion(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changedView,int visibility) {
        super.onVisibilityChanged(changedView,visibility);
        if(changedView==this) { if(visibility==View.VISIBLE) startMotion(); else pauseMotion(); }
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if(visibility==View.VISIBLE) startMotion(); else pauseMotion();
    }

    private void startMotion() {
        if(!motionEnabled||getVisibility()!=View.VISIBLE||getWindowVisibility()!=View.VISIBLE)return;
        if(motionStartMs==0) motionStartMs=SystemClock.uptimeMillis()-Math.round(phase*MOTION_PERIOD_MS);
        if(!framePosted) { framePosted=true; post(motionFrame); }
    }
    private void pauseMotion() {
        if(framePosted) {
            long elapsed=SystemClock.uptimeMillis()-motionStartMs;
            phase=(elapsed%MOTION_PERIOD_MS)/(float)MOTION_PERIOD_MS;
            removeCallbacks(motionFrame); framePosted=false;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!motionEnabled||getDrawable()==null||getWidth()<=0||getHeight()<=0)return;
        int width=getWidth(),height=getHeight();
        canvas.save();
        // 插画底部的接缝渐隐保持干净，不让粒子盖住日期区的过渡色。
        canvas.clipRect(0,0,width,Math.round(height*.94f));
        float time=phase*TAU;
        // 自由月与哀悼月的角色脸部留白优先：这两张图的主体正好在中上部，
        // 不使用贯穿画面的呼吸光，改成只贴边的低亮度光晕。
        if(month==7||month==11) drawEdgeGlow(canvas,time,width,height); else drawBreathingLight(canvas,time,width,height);
        switch(styleForMonth(month)) {
            case STYLE_EMBER: drawEmbers(canvas,time,width,height,false); break;
            case STYLE_ORBIT: drawOrbit(canvas,time,width,height); break;
            case STYLE_NIGHT: drawStars(canvas,time,width,height); break;
            case STYLE_SEED: drawSeeds(canvas,time,width,height); break;
            case STYLE_PETAL: drawPetals(canvas,time,width,height); break;
            case STYLE_SUN: drawSunRays(canvas,time,width,height); break;
            case STYLE_WIND: drawWind(canvas,time,width,height); break;
            case STYLE_LEAF: drawLeaves(canvas,time,width,height); break;
            case STYLE_THREAD: drawThreads(canvas,time,width,height); break;
            case STYLE_MIST: drawMist(canvas,time,width,height); break;
            default: drawStars(canvas,time,width,height); break;
        }
        canvas.restore();
    }

    private void drawBreathingLight(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month);
        float x=width*(.5f+.22f*(float)Math.sin(time*.55f+month));
        float y=height*(.30f+.12f*(float)Math.cos(time*.41f+month*.7f));
        float radius=Math.max(width,height)*.58f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x,y,radius,new int[]{withAlpha(color,0),withAlpha(color,52),withAlpha(color,0)},new float[]{0f,.58f,1f},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,width,height,paint);
        paint.setShader(null);
    }

    private void drawEdgeGlow(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month); float wobble=(float)Math.sin(time*.35f)*height*.025f;
        paint.setStyle(Paint.Style.FILL);
        canvas.save(); canvas.clipRect(0,0,width*.30f,height);
        paint.setShader(new RadialGradient(-width*.03f,height*.72f,width*.46f,
            new int[]{withAlpha(color,24),withAlpha(color,9),withAlpha(color,0)},new float[]{0f,.52f,1f},Shader.TileMode.CLAMP));
        canvas.drawRect(0,wobble,width*.34f,height,paint); canvas.restore();
        canvas.save(); canvas.clipRect(width*.70f,0,width,height);
        paint.setShader(new RadialGradient(width*1.03f,height*.68f,width*.46f,
            new int[]{withAlpha(color,21),withAlpha(color,8),withAlpha(color,0)},new float[]{0f,.52f,1f},Shader.TileMode.CLAMP));
        canvas.drawRect(width*.66f,wobble,width,height,paint); canvas.restore();
        paint.setShader(null);
    }

    private void drawEmbers(Canvas canvas,float time,int width,int height,boolean fast) {
        int color=Ui.accent(month);
        for(int i=0;i<10;i++) {
            float progress=positive(SEED_Y[i]+time*(fast?.075f:.045f)+i*.013f);
            float x=width*(SEED_X[i]+.025f*(float)Math.sin(time*1.5f+i));
            float y=height*(.08f+progress*.80f);
            float radius=dp(1.1f+SEED_SIZE[i]);
            int alpha=55+Math.round(95f*(.5f+.5f*(float)Math.sin(time*2.2f+i)));
            paint.setStyle(Paint.Style.FILL); paint.setColor(withAlpha(color,alpha)); canvas.drawCircle(x,y,radius,paint);
            if(i%3==0) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(.9f)); paint.setColor(withAlpha(color,alpha/2)); canvas.drawLine(x,y+radius*2,x-dp(3),y+dp(9),paint); }
        }
    }

    private void drawOrbit(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month); float cx=width*.56f,cy=height*.42f;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.4f)); paint.setColor(withAlpha(color,86));
        canvas.drawOval(new RectF(cx-width*.24f,cy-height*.17f,cx+width*.24f,cy+height*.17f),paint);
        paint.setStrokeWidth(dp(.8f)); paint.setColor(withAlpha(color,54));
        canvas.drawOval(new RectF(cx-width*.18f,cy-height*.28f,cx+width*.18f,cy+height*.28f),paint);
        for(int i=0;i<4;i++) {
            float angle=time*(.7f+i*.08f)+i*TAU/4f;
            float x=cx+(float)Math.cos(angle)*width*.24f;
            float y=cy+(float)Math.sin(angle)*height*.17f;
            drawTwinkle(canvas,x,y,dp(1.5f),color,105);
        }
    }

    private void drawStars(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month);
        for(int i=0;i<12;i++) {
            float x=width*SEED_X[i],y=height*(.10f+SEED_Y[i]*.74f);
            float twinkle=.5f+.5f*(float)Math.sin(time*(.8f+i*.07f)+i*1.9f);
            drawTwinkle(canvas,x,y,dp(.9f+SEED_SIZE[i]*twinkle),color,65+Math.round(90*twinkle));
        }
    }

    private void drawSeeds(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month);
        for(int i=0;i<9;i++) {
            float progress=positive(SEED_Y[i]+time*.035f+i*.021f),x=width*(SEED_X[i]+.04f*(float)Math.sin(time+i));
            float y=height*(.08f+progress*.80f);
            canvas.save(); canvas.rotate(35f*(float)Math.sin(time+i),x,y);
            paint.setStyle(Paint.Style.FILL); paint.setColor(withAlpha(color,55+Math.round(SEED_SIZE[i]*30)));
            canvas.drawOval(new RectF(x-dp(1.4f),y-dp(3.5f),x+dp(1.4f),y+dp(3.5f)),paint); canvas.restore();
        }
    }

    private void drawPetals(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month);
        for(int i=0;i<8;i++) {
            float progress=positive(SEED_Y[i]+time*.028f+i*.017f),x=width*(SEED_X[i]+.035f*(float)Math.sin(time*.8f+i));
            float y=height*(.08f+progress*.78f),size=dp(3.5f+SEED_SIZE[i]*2f);
            canvas.save(); canvas.rotate(55f*(float)Math.sin(time+i*1.7f),x,y);
            paint.setStyle(Paint.Style.FILL); paint.setColor(withAlpha(color,45+Math.round(60*(.5f+.5f*(float)Math.sin(time+i)))));
            canvas.drawOval(new RectF(x-size*.45f,y-size,x+size*.45f,y+size),paint); canvas.restore();
        }
    }

    private void drawSunRays(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month); float cx=width*(.62f+.05f*(float)Math.sin(time)),cy=height*.27f;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.1f));
        for(int i=0;i<8;i++) {
            float angle=i*TAU/8f+time*.12f,inner=width*.10f,outer=width*(.13f+.018f*(float)Math.sin(time+i));
            paint.setColor(withAlpha(color,55+Math.round(45*(.5f+.5f*(float)Math.sin(time+i)))));
            canvas.drawLine(cx+(float)Math.cos(angle)*inner,cy+(float)Math.sin(angle)*inner,cx+(float)Math.cos(angle)*outer,cy+(float)Math.sin(angle)*outer,paint);
        }
        drawTwinkle(canvas,cx,cy,dp(2.6f),color,105);
    }

    private void drawWind(Canvas canvas,float time,int width,int height) {
        drawFreedomWind(canvas,time,width,height);
    }

    /** 自由月：风线只在底部两侧掠过，中央人物脸部完全留空。 */
    private void drawFreedomWind(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
        for(int side=0;side<2;side++) {
            canvas.save(); canvas.clipRect(side==0?0:width*.66f,height*.81f,side==0?width*.34f:width,height*.95f);
            for(int i=0;i<2;i++) {
                float y=height*(.84f+i*.07f)+height*.010f*(float)Math.sin(time+i+side);
                path.reset();
                if(side==0) {
                    path.moveTo(-width*.06f,y); path.cubicTo(width*.08f,y-height*.035f,width*.21f,y+height*.035f,width*.38f,y-height*.015f);
                } else {
                    path.moveTo(width*.62f,y-height*.015f); path.cubicTo(width*.79f,y+height*.035f,width*.92f,y-height*.035f,width*1.06f,y);
                }
                paint.setStrokeWidth(dp(1.0f+i*.35f)); paint.setColor(withAlpha(color,24+i*10)); canvas.drawPath(path,paint);
            }
            canvas.restore();
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawLeaves(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month);
        for(int i=0;i<7;i++) {
            float progress=positive(SEED_Y[i]+time*.032f+i*.025f),x=width*(SEED_X[i]+.045f*(float)Math.sin(time*.9f+i));
            float y=height*(.10f+progress*.76f),size=dp(4f+SEED_SIZE[i]*2f);
            canvas.save(); canvas.rotate(45f*(float)Math.sin(time+i),x,y); paint.setStyle(Paint.Style.FILL); paint.setColor(withAlpha(color,24+Math.round(SEED_SIZE[i]*18)));
            path.reset(); path.moveTo(x,y-size); path.quadTo(x+size*.9f,y-size*.2f,x,y+size); path.quadTo(x-size*.9f,y-size*.2f,x,y-size); canvas.drawPath(path,paint); canvas.restore();
        }
    }

    private void drawThreads(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month); paint.setStyle(Paint.Style.STROKE);
        for(int i=0;i<3;i++) {
            float offset=(float)Math.sin(time*.7f+i)*width*.025f;
            path.reset(); path.moveTo(width*(.08f+i*.18f),height*.10f);
            path.cubicTo(width*(.26f+i*.12f)+offset,height*.36f,width*(.68f-i*.10f)-offset,height*.55f,width*(.92f-i*.08f),height*.84f);
            paint.setStrokeWidth(dp(1.3f+i*.5f)); paint.setColor(withAlpha(color,55+i*22)); canvas.drawPath(path,paint);
        }
        for(int i=0;i<4;i++) { float x=width*(.20f+i*.21f)+width*.03f*(float)Math.sin(time+i); drawTwinkle(canvas,x,height*(.27f+i*.13f),dp(1.4f),color,105); }
    }

    private void drawMist(Canvas canvas,float time,int width,int height) {
        drawMourningMist(canvas,time,width,height);
    }

    /** 哀悼月：把雾压到画面底部和两侧，避免横向雾带穿过角色面部。 */
    private void drawMourningMist(Canvas canvas,float time,int width,int height) {
        int color=Ui.accent(month); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
        for(int side=0;side<2;side++) {
            canvas.save(); canvas.clipRect(side==0?0:width*.67f,height*.81f,side==0?width*.33f:width,height*.96f);
            for(int i=0;i<2;i++) {
                float y=height*(.85f+i*.065f)+height*.012f*(float)Math.sin(time*.55f+i+side);
                path.reset();
                if(side==0) {
                    path.moveTo(-width*.10f,y); path.cubicTo(width*.08f,y-height*.045f,width*.18f,y+height*.045f,width*.37f,y-height*.018f);
                } else {
                    path.moveTo(width*.63f,y-height*.018f); path.cubicTo(width*.82f,y+height*.045f,width*.92f,y-height*.045f,width*1.10f,y);
                }
                paint.setStrokeWidth(dp(3f+i*1.5f)); paint.setColor(withAlpha(color,12+i*6)); canvas.drawPath(path,paint);
            }
            canvas.restore();
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawTwinkle(Canvas canvas,float x,float y,float radius,int color,int alpha) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(withAlpha(color,alpha)); canvas.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(dp(.5f),radius*.35f)); paint.setColor(withAlpha(color,Math.max(10,alpha/2)));
        canvas.drawLine(x-radius*2.2f,y,x+radius*2.2f,y,paint); canvas.drawLine(x,y-radius*2.2f,x,y+radius*2.2f,paint);
    }

    private int styleForMonth(int value) {
        switch(Math.floorMod(value-1,12)) {
            case 0: return STYLE_EMBER; case 1: return STYLE_ORBIT; case 2: return STYLE_NIGHT; case 3: return STYLE_SEED;
            case 4: return STYLE_PETAL; case 5: return STYLE_SUN; case 6: return STYLE_WIND; case 7: return STYLE_LEAF;
            case 8: return STYLE_THREAD; case 9: return STYLE_EMBER; case 10: return STYLE_MIST; default: return STYLE_NIGHT;
        }
    }
    private float positive(float value) { value=value-(float)Math.floor(value); return value<0?value+1f:value; }
    private float dp(float value) { return value*getResources().getDisplayMetrics().density; }
    private static int withAlpha(int color,int alpha) { return (Math.max(0,Math.min(255,alpha))<<24)|(color&0x00ffffff); }
}
