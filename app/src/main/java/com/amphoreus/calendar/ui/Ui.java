package com.amphoreus.calendar.ui;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Ui {
    /** Current palette. Widgets and activities render from the same process-wide values. */
    public static int BG=0xff10111c,CARD=0xff1b1d2b,GOLD=0xffe6c88c,TEXT=0xfff4f0e7,MUTED=0xffb2b0c1,LINE=0xff333344;
    /** 本月以外日期（上/下月的溢出格）的数字颜色。 */
    public static int DIM=0xff626173;
    /**
     * 每个月自己的主题色：从当月海报插画的主色提取，再统一到深色底上可读的亮度区间。
     * 索引 0 是年历封面，1-12 对应十二个月。插画下方的标题、日期格线、选中态都用它。
     * 全部取值对 0xff10111c 的对比度都在 6.7:1 以上。
     */
    private static final int[] DARK_ACCENTS={
        0xffdc8ac5,0xffd49e91,0xff80a9e5,0xffc9a2e0,0xffd3c492,0xffa58cee,0xffee96b2,
        0xff87afe1,0xff90d4c6,0xffd7be8e,0xffe69e7f,0xffcd98e8,0xffb898e8
    };
    private static final int[] LIGHT_ACCENTS={
        0xff9b4c8c,0xff965447,0xff3c6ca8,0xff80529f,0xff776630,0xff624caa,0xffa14360,
        0xff356e9f,0xff297d6e,0xff876529,0xffa54c2c,0xff784c9d,0xff654d9b
    };
    private static boolean lightTheme;
    public static int accent(int month) {
        int[] accents=lightTheme?LIGHT_ACCENTS:DARK_ACCENTS;
        return accents[Math.floorMod(month,accents.length)];
    }
    /** 寄语叠在角色图上，不跟随日期区的主题文字改成黑色。 */
    public static int quoteColor() { return lightTheme?0xffffffff:TEXT; }
    /**
     * 把 overlay 按 amount 混进 base。用来让日期区的底色带一点当月插画底边的颜色——
     * 插画是亮的、日期区是暗的，中间直接切到纯深色时读起来是两张东西；混一点点插画
     * 自己的颜色，整页才像同一张纸。amount 要小（0.2 上下）：大了会冲淡深色主题，
     * 而且正文的对比度会掉下来（CoreTests 里有 4.5:1 的底线）。
     */
    public static int tint(int base,int overlay,float amount) {
        float keep=1f-amount;
        int red=Math.round(((base>>16)&0xff)*keep+((overlay>>16)&0xff)*amount);
        int green=Math.round(((base>>8)&0xff)*keep+((overlay>>8)&0xff)*amount);
        int blue=Math.round((base&0xff)*keep+(overlay&0xff)*amount);
        return 0xff000000|(clamp(red)<<16)|(clamp(green)<<8)|clamp(blue);
    }
    /** 主题色的半透明版本：分隔线、描边这类"轻描"的线条用它，替掉过去写死的金色。 */
    public static int accentSoft(int accent,float alpha) {
        return (Math.round(Math.max(0f,Math.min(1f,alpha))*255f)<<24)|(accent&0x00ffffff);
    }
    private static int clamp(int value) { return value<0?0:value>255?255:value; }
    public static int dp(Context c,float value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable background(int color,int radius,Context c) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    public static TextView text(Context c,String value,float size,int color) {
        TextView v=new TextView(c); v.setText(LocaleText.t(c,value)); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    /** 衬线体：用于标题与日期数字，贴近原版海报的印刷字体。 */
    public static TextView serif(Context c,String value,float size,int color) {
        TextView v=text(c,value,size,color); v.setTypeface(Typeface.SERIF); return v;
    }
    public static View line(Context c,int color,int thickness,int alpha) {
        View v=new View(c); v.setBackgroundColor(color); v.setAlpha(alpha/255f); v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,thickness))); return v;
    }
    public static LinearLayout column(Context c) { LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    public static LinearLayout row(Context c) { LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    public static void pad(View v,int h,int vertical) { v.setPadding(dp(v.getContext(),h),dp(v.getContext(),vertical),dp(v.getContext(),h),dp(v.getContext(),vertical)); }
    public static TextView button(Context c,String label,Runnable action) {
        TextView b=text(c,label,14,GOLD); b.setGravity(Gravity.CENTER); pad(b,14,10); b.setMinHeight(dp(c,48));
        b.setBackground(background(CARD,14,c)); b.setOnClickListener(v->action.run()); b.setFocusable(true); return b;
    }
    public static void space(LinearLayout parent,int height) { View v=new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height))); }
    /** Apply locale, palette, and system-bar contrast before an activity builds its views. */
    public static void applyTheme(Context context) {
        AppSettings.applyLocale(context);
        lightTheme=AppSettings.isLight(context);
        if(lightTheme) {
            BG=0xfff7f4ef; CARD=0xffffffff; GOLD=0xff8a6227; TEXT=0xff252331;
            MUTED=0xff6f6975; LINE=0xffd9d0c5; DIM=0xff99919d;
        } else {
            BG=0xff10111c; CARD=0xff1b1d2b; GOLD=0xffe6c88c; TEXT=0xfff4f0e7;
            MUTED=0xffb2b0c1; LINE=0xff333344; DIM=0xff626173;
        }
        if(context instanceof Activity) {
            Activity activity=(Activity)context;
            Window window=activity.getWindow();
            window.setStatusBarColor(BG); window.setNavigationBarColor(BG);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
            int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if(lightTheme) flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }
    public static LinearLayout root(Activity activity) {
        applyTheme(activity);
        activity.getWindow().setStatusBarColor(BG); activity.getWindow().setNavigationBarColor(BG);
        LinearLayout root=column(activity); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) { android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime()); v.setPadding(i.left,i.top,i.right,i.bottom); }
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if(lightTheme) flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        activity.setContentView(root); root.requestApplyInsets(); return root;
    }
    public static LinearLayout toolbar(Activity a,LinearLayout root,String title) {
        LinearLayout row=row(a); pad(row,16,6); row.addView(button(a,"‹ 返回",a::finish));
        TextView t=text(a,title,18,TEXT); t.setTypeface(null,Typeface.BOLD); pad(t,16,0); row.addView(t,new LinearLayout.LayoutParams(0,dp(a,52),1)); root.addView(row); return row;
    }
    public static void error(Context c,Throwable e) { new AlertDialog.Builder(c).setTitle(LocaleText.t(c,"暂时无法完成")).setMessage(e.getMessage()==null?LocaleText.t(c,"请稍后重试"):e.getMessage()).setPositiveButton(LocaleText.t(c,"知道了"),null).show(); }
    public static void toast(Context c,String message) { Toast.makeText(c,message,Toast.LENGTH_LONG).show(); }
    public static String date(LocalDate date) {
        Locale locale=Locale.getDefault();
        return date.format(DateTimeFormatter.ofPattern(locale.getLanguage().equals("en")?"MMM d, EEEE":"M月d日 EEEE",locale));
    }
    public static String time(long millis,ZoneId zone) { return Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")); }
    private Ui() {}
}
