package com.amphoreus.calendar.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 应用开屏：从年历封面昔涟和十二个月角色插画共十三张中随机选一张，
 * 只展示角色和独立绘制的寄语。
 *
 * 动画刻意使用 uptimeMillis + Handler，而不是 ValueAnimator。这样即使设备的全局
 * Animator duration scale 被设成 0，开屏仍然会按真实时间结束并进入主页。
 */
public final class SplashActivity extends Activity {
    public static final String SETTINGS_NAME="settings";
    /** 新版 13 张素材池；旧版只存 1-12 月的 key，读取时会自动补回封面。 */
    public static final String MONTH_POOL_KEY="splashMonths13";
    private static final String LEGACY_MONTH_POOL_KEY="splashMonths";

    private SplashMotionView splash;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.applyTheme(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Ui.BG);
        int systemUi=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if(AppSettings.isLight(this))systemUi|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
        int month=randomMonth(getSharedPreferences(SETTINGS_NAME,0));
        splash=new SplashMotionView(this,month);
        setContentView(splash);
        splash.startMotion();
    }

    /** 当前设置中允许参与随机的素材；未设置或被写成空集合时默认全部十三张。 */
    public static Set<String> selectedMonthKeys(SharedPreferences preferences) {
        Set<String> all=new HashSet<>();
        for(int i=0;i<=12;i++)all.add(String.valueOf(i));
        Set<String> saved=preferences.getStringSet(MONTH_POOL_KEY,null);
        boolean legacy=false;
        if(saved==null) { saved=preferences.getStringSet(LEGACY_MONTH_POOL_KEY,null); legacy=saved!=null; }
        if(saved==null)return all;
        Set<String> valid=new HashSet<>();
        for(String value:saved) {
            try { int month=Integer.parseInt(value); if(month>=0&&month<=12)valid.add(String.valueOf(month)); }
            catch(RuntimeException ignored) { }
        }
        // 旧版本的设置只能保存 1-12 月；迁移时把最初的昔涟封面加回来一次，
        // 后续用户在新设置页取消 0 后不会被再次强行选中。
        if(legacy) {
            valid.add("0");
            preferences.edit().putStringSet(MONTH_POOL_KEY,valid).apply();
        }
        return valid.isEmpty()?all:valid;
    }

    private static int randomMonth(SharedPreferences preferences) {
        List<Integer> pool=new ArrayList<>(); Set<String> selected=selectedMonthKeys(preferences);
        for(int i=0;i<=12;i++)if(selected.contains(String.valueOf(i)))pool.add(i);
        return pool.get(new Random().nextInt(pool.size()));
    }

    void openMain() {
        if(splash!=null) splash.stopMotion();
        startActivity(new Intent(this,MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
        finish();
    }

    @Override protected void onDestroy() {
        if(splash!=null) splash.stopMotion();
        super.onDestroy();
    }

    /** 开屏画面与动效都收在一个 View 中，避免为一次性动画引入额外依赖。 */
    private static final class SplashMotionView extends View {
        private static final long DURATION_MS=1450L;
        private static final long FRAME_DELAY_MS=16L;
        private static final float[] PARTICLE_X={.10f,.18f,.29f,.41f,.56f,.68f,.79f,.90f,.23f,.73f,.48f,.85f};
        private static final float[] PARTICLE_Y={.76f,.62f,.48f,.82f,.32f,.70f,.54f,.23f,.37f,.88f,.16f,.43f};
        private static final float[] PARTICLE_SIZE={1.0f,.7f,1.4f,.8f,1.1f,.65f,1.5f,.8f,1.0f,.7f,1.3f,.9f};

        private final SplashActivity activity;
        private final int month;
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final TextPaint quotePaint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF destination=new RectF();
        private final Rect source=new Rect();
        private Bitmap artwork;
        private StaticLayout quoteLayout;
        private int quoteWidth;
        private long startMs;
        private boolean framePosted;
        private boolean opening;
        private float progress;

        private final Runnable frame=new Runnable() {
            @Override public void run() {
                if(!opening||!isAttachedToWindow()||getVisibility()!=View.VISIBLE||getWindowVisibility()!=View.VISIBLE) {
                    framePosted=false;
                    return;
                }
                long elapsed=SystemClock.uptimeMillis()-startMs;
                progress=clamp(elapsed/(float)DURATION_MS,0f,1f);
                invalidate();
                if(elapsed>=DURATION_MS) {
                    framePosted=false;
                    opening=false;
                    activity.openMain();
                } else {
                    postDelayed(this,FRAME_DELAY_MS);
                }
            }
        };

        SplashMotionView(SplashActivity activity,int month) {
            super(activity);
            this.activity=activity;
            this.month=month;
            setFocusable(true);
            setContentDescription("翁法罗斯月历开屏动画："+Art.SPLASH_NAMES[month]+"。"+
                Art.QUOTES[month].replace('\n',' '));
            setBackgroundColor(Ui.BG);
            quotePaint.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));
        }

        void startMotion() {
            opening=true;
            startMs=SystemClock.uptimeMillis();
            progress=0f;
            if(!framePosted) {
                framePosted=true;
                post(frame);
            }
            invalidate();
        }

        void stopMotion() {
            opening=false;
            handler.removeCallbacks(frame);
            removeCallbacks(frame);
            framePosted=false;
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if(!opening) startMotion();
        }

        @Override protected void onDetachedFromWindow() {
            stopMotion();
            if(artwork!=null&&!artwork.isRecycled()) { artwork.recycle(); artwork=null; }
            super.onDetachedFromWindow();
        }

        @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
            super.onSizeChanged(width,height,oldWidth,oldHeight);
            if(width>0&&height>0&&artwork==null) {
                try { artwork=Art.splashArtwork(getContext(),month); }
                catch(RuntimeException ignored) { artwork=null; }
            }
            quoteLayout=null;
            quoteWidth=0;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            // 轻触可提前进入主页，长按/滑动不打断开屏，避免误触造成跳转。
            if(opening&&event.getActionMasked()==MotionEvent.ACTION_UP&&
                SystemClock.uptimeMillis()-startMs>220L) {
                opening=false;
                activity.openMain();
            }
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width=getWidth(),height=getHeight();
            if(width<=0||height<=0)return;

            float eased=easeOutCubic(progress);
            canvas.drawColor(Ui.BG);
            drawArtwork(canvas,width,height,eased);
            drawLight(canvas,width,height,eased);
            drawOrbit(canvas,width,height,eased);
            drawParticles(canvas,width,height,eased);
            drawVignette(canvas,width,height);
            drawQuote(canvas,width,height);
        }

        private void drawArtwork(Canvas canvas,int width,int height,float eased) {
            if(artwork==null)return;
            // 以最初的昔涟封面为构图参考：插画覆盖整个开屏，再按主体焦点横向取景，
            // 只裁掉边缘，不把角色缩成上半张小图。
            float targetAspect=width/(float)height;
            float sourceWidth=artwork.getWidth(),sourceHeight=artwork.getHeight();
            float sourceAspect=sourceWidth/sourceHeight;
            if(targetAspect<sourceAspect) sourceWidth=sourceHeight*targetAspect;
            else if(targetAspect>sourceAspect) sourceHeight=sourceWidth/targetAspect;
            float left=clamp(artwork.getWidth()*Art.focusX(month)-sourceWidth/2f,0f,artwork.getWidth()-sourceWidth);
            float top=clamp(artwork.getHeight()*Art.focusY(month)-sourceHeight/2f,0f,artwork.getHeight()-sourceHeight);
            source.set(Math.round(left),Math.round(top),Math.round(left+sourceWidth),Math.round(top+sourceHeight));
            float scale=1.075f-.075f*eased;
            float drawWidth=width*scale,drawHeight=height*scale;
            float drift=(1f-eased)*Ui.dp(getContext(),10);
            destination.set((width-drawWidth)/2f+drift,(height-drawHeight)/2f,
                (width+drawWidth)/2f+drift,(height+drawHeight)/2f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(Math.round(255f*clamp(progress*5.5f,0f,1f)));
            canvas.drawBitmap(artwork,source,destination,paint);
            paint.setAlpha(255);
        }

        private void drawLight(Canvas canvas,int width,int height,float eased) {
            int accent=Ui.accent(month);
            float x=width*(.50f+.12f*(float)Math.sin(eased*Math.PI*1.4));
            float y=height*(.31f-.04f*eased);
            float radius=Math.max(width,height)*(.40f+.14f*eased);
            int alpha=Math.round(48f*clamp(progress*2.2f,0f,1f));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x,y,radius,
                new int[]{withAlpha(accent,alpha),withAlpha(accent,Math.round(alpha*.24f)),withAlpha(accent,0)},
                new float[]{0f,.48f,1f},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,paint);
            paint.setShader(null);
        }

        private void drawOrbit(Canvas canvas,int width,int height,float eased) {
            if(progress<.18f)return;
            int accent=Ui.accent(month);
            float alpha=clamp((progress-.18f)/.5f,0f,1f)*.34f;
            float radius=Math.min(width,height)*.29f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(),1));
            paint.setColor(withAlpha(accent,Math.round(255f*alpha)));
            RectF ring=new RectF(width*.5f-radius,height*.31f-radius,width*.5f+radius,height*.31f+radius);
            canvas.drawArc(ring,-55f+eased*80f,145f,false,paint);
            paint.setStrokeWidth(Ui.dp(getContext(),.55f));
            paint.setColor(withAlpha(Ui.GOLD,Math.round(190f*alpha)));
            canvas.drawArc(ring,130f+eased*80f,82f,false,paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawParticles(Canvas canvas,int width,int height,float eased) {
            if(progress<.15f)return;
            int accent=Ui.accent(month);
            float reveal=clamp((progress-.15f)/.7f,0f,1f);
            canvas.save();
            canvas.clipRect(0,0,width,height*.78f);
            for(int i=0;i<PARTICLE_X.length;i++) {
                float drift=(float)Math.sin(eased*Math.PI*2.0+i*1.7)*Ui.dp(getContext(),8);
                float rise=eased*Ui.dp(getContext(),34+i%3*8);
                float x=width*PARTICLE_X[i]+drift;
                float y=height*PARTICLE_Y[i]-rise;
                float radius=Ui.dp(getContext(),PARTICLE_SIZE[i])*(.6f+.4f*(float)Math.sin(eased*Math.PI+i));
                int alpha=Math.round(180f*reveal*(.65f+.35f*(float)Math.sin(eased*Math.PI*2+i)));
                if(alpha<=0)continue;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(i%3==0?Ui.GOLD:accent,alpha));
                canvas.drawCircle(x,y,Math.max(Ui.dp(getContext(),.45f),radius),paint);
                if(i%4==0) {
                    paint.setColor(withAlpha(Color.WHITE,Math.round(alpha*.65f)));
                    canvas.drawRect(x-Ui.dp(getContext(),.45f),y-Ui.dp(getContext(),3),
                        x+Ui.dp(getContext(),.45f),y+Ui.dp(getContext(),3),paint);
                }
            }
            canvas.restore();
        }

        private void drawVignette(Canvas canvas,int width,int height) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0,0,0,height,
                new int[]{withAlpha(Ui.BG,77),withAlpha(Ui.BG,0),withAlpha(Ui.BG,0),withAlpha(Ui.BG,166)},
                new float[]{0f,.16f,.67f,1f},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,paint);
            paint.setShader(null);
        }

        private void drawQuote(Canvas canvas,int width,int height) {
            if(progress<.30f)return;
            float alpha=clamp((progress-.30f)/.45f,0f,1f);
            ensureQuoteLayout(width);
            float quoteY=height*.72f;
            int maxBottom=height-Ui.dp(getContext(),48);
            if(quoteLayout!=null&&quoteY+quoteLayout.getHeight()>maxBottom) quoteY=maxBottom-quoteLayout.getHeight();
            if(quoteLayout!=null) {
                quotePaint.setColor(withAlpha(Ui.quoteColor(),Math.round(255f*alpha)));
                quotePaint.setShadowLayer(Ui.dp(getContext(),2),0,Ui.dp(getContext(),1),0xcc000000);
                canvas.save();
                canvas.translate((width-quoteWidth)/2f,quoteY);
                quoteLayout.draw(canvas);
                canvas.restore();
            }
            quotePaint.clearShadowLayer();
        }

        private void ensureQuoteLayout(int width) {
            int wanted=Math.max(1,Math.round(width*.86f));
            if(quoteLayout!=null&&quoteWidth==wanted)return;
            quoteWidth=wanted;
            quotePaint.setTextSize(sp(16.5f));
            quotePaint.setColor(Ui.quoteColor());
            quotePaint.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));
            quoteLayout=StaticLayout.Builder.obtain(Art.QUOTES[month],0,Art.QUOTES[month].length(),quotePaint,quoteWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(Ui.dp(getContext(),3),1f)
                .build();
        }

        private float sp(float value) { return value*getResources().getDisplayMetrics().scaledDensity; }
        private static float clamp(float value,float low,float high) { return value<low?low:value>high?high:value; }
        private static float easeOutCubic(float value) { float inverse=1f-value; return 1f-inverse*inverse*inverse; }
        private static int withAlpha(int color,int alpha) { return (Math.max(0,Math.min(255,alpha))<<24)|(color&0x00ffffff); }
    }
}
