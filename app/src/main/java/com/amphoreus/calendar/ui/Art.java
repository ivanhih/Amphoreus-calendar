package com.amphoreus.calendar.ui;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import java.io.*;

/**
 * 月历素材：把海报上方的插画区取出来给首页和桌面卡片用。
 *
 * 海报原图 1125×2436，上半是插画，下半是**印刷好的 2026 日期表**。应用只使用插画区，
 * 印刷日期表一律不显示——真实日期全部由应用按公历计算，屏幕上永远只有一套日期。
 *
 * 各月的插画区底边由 tools/calibrate_art.py 从图里量出（12 张实测都落在 0.583-0.587），
 * 连同插画底边色一起写进下面的 CALIBRATED 标记块。要改这些数就重跑那个脚本，
 * 不要手改：手改的数字无法复核，而且换素材后会静默失效。
 */
public final class Art {
    public static final String[] NAMES={"年历封面","门关月","平衡月","长夜月","耕耘月","欢喜月","长昼月","自由月","收获月","拾线月","纷争月","哀悼月","机缘月"};
    /** 开屏池的显示名：索引 0 是粉发角色昔涟，其余为对应翁法罗斯月名。 */
    public static final String[] SPLASH_NAMES={"昔涟","门关月","平衡月","长夜月","耕耘月","欢喜月","长昼月","自由月","收获月","拾线月","纷争月","哀悼月","机缘月"};
    public static final String[] ENGLISH={"AMPHOREUS","JANUARY","FEBRUARY","MARCH","APRIL","MAY","JUNE","JULY","AUGUST","SEPTEMBER","OCTOBER","NOVEMBER","DECEMBER"};
    /** 海报中每个月角色的寄语；索引 0 是年历封面，1-12 对应十二个月。换素材时同步核对原图文案。 */
    public static final String[] QUOTES={
        "「那些未来的风景，就由你替我看看吧~」",
        "「不论晴天还是雨天，进入梦乡以前，\n记得和自己说一声：「明天见」！」",
        "「无我，即无世界。\n去思，去见，去征服。」",
        "「如果岁月是个无穷的轮回——那就忘掉不断\n重复的苦涩，单独记住每个快乐的瞬间吧！」",
        "「笔终会折断，墨终会耗尽，\n生命终会逝去，但思想将在史诗中永垂不朽。」",
        "「世事如沧海，潮起潮落，一切终会在浪中消逝。\n所以，请在生命的每一刻都纵情高歌吧。」",
        "「多望望天，多笑一笑；\n好多缠人的病痛，最怕乐观这剂良药！」",
        "「每个人的心中都住着一个英雄。\n拥抱它，然后去追逐太阳吧。」",
        "「『真理是溶解世界万物的溶剂，因而绝无法在客观\n上存在』——对此，本人心中已有绝妙的证明。」",
        "「信任与无私，是世上最美丽的两件华服；\n一件赠予他人，一件装扮自己。」",
        "「去杀死玩耍，游戏便会诞生。\n去杀死纷争，荣光必然降临。」",
        "「若心灵仍有余裕，请稍许分出些温柔，\n轻抚身边美好的一切吧。」",
        "「别怕摔，跑起来！命运就是只迟缓的若虫，\n它压根抓不住你！」"
    };
    public static final int SOURCE_WIDTH=1125,SOURCE_HEIGHT=2436;
    /**
     * 目标框比插画本身更宽（更矮）时，纵向裁切保留哪一段：0 是从顶部开始，1 是贴到底部。
     * 插画的主体大多在画面中部偏上，所以取 0.35 而不是 0.5——宁可多留天空、少留前景的装饰边。
     * 只有屏幕高度不够、不得不裁时才会用到（见 core.FitBudget）。
     */
    private static final float CROP_BIAS=0.35f;
    /** 插画面板底部渐隐到日期区底色的比例。只走这么短一段，是为了接缝不硬切又不洗掉画面。 */
    private static final float SEAM_FADE=0.08f;
    /**
     * 桌面卡片单张位图的像素上限，约 90 万像素（RGB_565 下 1.8MB）。
     *
     * 这不是 Binder 的 1MB 事务预算——实测位图走 ashmem，整份 RemoteViews 序列化后只有 17KB。
     * 这条上限管的是启动器的内存与解码开销：常规卡片（<=340dp 宽）根本触不到它，所以画面是
     * 原分辨率；特别宽的卡片会等比降采样，只影响清晰度，不影响比例。
     */
    private static final int POSTER_MAX_PIXELS=900_000;

    // BEGIN CALIBRATED (tools/calibrate_art.py --write-java)
    /** 插画区底边占原图高度的比例，逐月量出。索引 0 是年历封面。 */
    private static final float[] ARTWORK_BOTTOM={0.585f,0.5862f,0.5870f,0.5862f,0.5862f,0.5842f,0.5829f,0.5842f,0.5833f,0.5854f,0.5874f,0.5874f,0.5866f};
    /** 插画底边往上 12 行的平均色，用作插画与日期格之间的接缝色。索引 0 是年历封面。 */
    private static final int[] EDGE_COLORS={0xffa47372,0xffa47372,0xff627295,0xff8a626d,0xff676649,0xff65668d,0xff9d6072,0xff576573,0xff687f7c,0xffaf986c,0xff99674e,0xff8b7890,0xff726487};
    /** 插画区段内细节质心的位置（x/y 各占区段宽高的比例）。桌面卡片的裁切窗口以它为中心放置。
     *  逐月量出；索引 0 是年历封面。 */
    private static final float[] FOCUS_X={0.4961f,0.4961f,0.5190f,0.5194f,0.5073f,0.4898f,0.5105f,0.5209f,0.4957f,0.5769f,0.5060f,0.5231f,0.5483f};
    private static final float[] FOCUS_Y={0.5349f,0.5349f,0.5511f,0.5634f,0.5328f,0.5578f,0.5508f,0.5395f,0.5652f,0.5736f,0.5500f,0.5394f,0.5774f};
    // END CALIBRATED

    private static final LruCache<Integer,Bitmap> cache=new LruCache<Integer,Bitmap>(48000) {
        @Override protected int sizeOf(Integer key,Bitmap value) { return value.getByteCount()/1024; }
    };

    /** 某月插画区的宽高比（宽 ÷ 高）。首页据此把面板高度算成"零裁切"的高度。 */
    public static float aspect(int month) { return SOURCE_WIDTH/(SOURCE_HEIGHT*artworkBottom(month)); }
    /** 某月插画区的底边占原图高度的比例。 */
    private static float artworkBottom(int month) { return ARTWORK_BOTTOM[Math.floorMod(month,ARTWORK_BOTTOM.length)]; }
    /** 某月角色主体的横向焦点，供开屏全屏裁切时尽量保住角色。 */
    public static float focusX(int month) { return FOCUS_X[Math.floorMod(month,FOCUS_X.length)]; }
    /** 某月角色主体的纵向焦点，供非竖屏开屏裁切时使用。 */
    public static float focusY(int month) { return FOCUS_Y[Math.floorMod(month,FOCUS_Y.length)]; }
    /** 某月插画底边的平均色：首页用它把插画和日期格接成一张纸，而不是硬切一条黑边。 */
    public static int edgeColor(int month) { return EDGE_COLORS[Math.floorMod(month,EDGE_COLORS.length)]; }
    /** 面向普通用户的月份标题：同时显示公历数字和翁法罗斯月名。 */
    public static String monthLabel(int month,int year) {
        int value=Math.max(1,Math.min(12,month));
        return value+"月 · "+NAMES[value]+" · "+year;
    }
    /** Localized widget title; the legacy overload above remains stable for core callers. */
    public static String monthLabel(Context c,int month,int year) {
        int value=Math.max(1,Math.min(12,month));
        if(LocaleText.isEnglish(c))return ENGLISH[value]+" · "+year;
        return value+"月 · "+LocaleText.t(c,NAMES[value])+" · "+year;
    }

    /**
     * 开屏用的月度角色图：只取已经校准过的上方插画区。
     * 这段区域在原海报的寄语、月份标题和日期表之前，因此开屏不会重复显示日历文字。
     * 返回新位图，调用方可以在用完后 recycle；底图仍由本类缓存持有。
     */
    public static Bitmap splashArtwork(Context c,int month) {
        int value=Math.max(0,Math.min(12,month));
        Bitmap original=image(c,value);
        int rows=Math.max(1,Math.round(original.getHeight()*artworkBottom(value)));
        return Bitmap.createBitmap(original,0,0,original.getWidth(),rows);
    }

    /** 整张海报（已按展示尺寸采样）。只给本类内部用，外部请走 illustration()/header()。 */
    private static Bitmap image(Context c,int month) {
        Bitmap found=cache.get(month); if(found!=null) return found;
        try(InputStream in=c.getAssets().open(String.format(java.util.Locale.ROOT,"art/month_%02d.jpg",month))) {
            BitmapFactory.Options o=new BitmapFactory.Options(); o.inSampleSize=2;
            Bitmap b=BitmapFactory.decodeStream(in,null,o); if(b==null) throw new IOException("图片解码失败"); cache.put(month,b); return b;
        } catch(IOException e) { throw new IllegalStateException("无法载入月历图片",e); }
    }

    /**
     * 首页上半的插画面板。
     *
     * 调用方传 height = round(width/aspect(month)) 时**零裁切**：插画整块铺满面板，
     * 一个像素都不丢。height 小于这个值时才会纵向裁切（CROP_BIAS 决定保留哪一段），
     * 那是屏幕高度不够时的降级，不是常态。这个方法只负责生成原图面板；首页的当月寄语由
     * MainActivity 以独立的衬线文字层叠加，避免把原图和日期区一起重新绘制。
     *
     * @param seamColor 面板最下面 SEAM_FADE 那一段渐隐过去的颜色，传日期区的底色，
     *                  插画与日期格之间才不会是一条硬边。传 0 表示不要渐隐。
     */
    public static Bitmap illustration(Context c,int month,int width,int height,int seamColor) {
        Bitmap out=fill(c,month,width,height,CROP_BIAS);
        int w=Math.max(1,width), h=Math.max(1,height);
        if(seamColor!=0&&h>4) {
            Canvas canvas=new Canvas(out); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            float start=h*(1f-SEAM_FADE);
            // 渐隐只走很短一段（SEAM_FADE），而且是同色系的透明度过渡，不是拿黑色往上盖。
            paint.setShader(new LinearGradient(0,start,0,h,new int[]{seamColor&0x00ffffff,seamColor},new float[]{0f,1f},Shader.TileMode.CLAMP));
            canvas.drawRect(0,start,w,h,paint); paint.setShader(null);
        }
        return out;
    }

    /**
     * 桌面卡片取图：位图就是"整幅插画按自身宽高比缩放"，宽高比与控件尺寸无关。
     *
     * 为什么不再按上报的块尺寸裁切：启动器上报的尺寸和卡片实际占的像素经常不一致
     * （实测上报 460dp 高、实际 560dp）。旧实现按上报的宽高比生成位图、控件再按真实尺寸缩放，
     * 于是画面被裁两刀（第二刀切在另一个轴上），要么变形（fitXY）、要么只剩中间一小块。
     * 现在位图固定为插画自身比例，控件用 centerCrop：无论实际多大，画面都只是被**居中**裁掉
     * 一部分——比例永远是对的，可见比例由真实卡片尺寸决定（期望值见 core.WidgetBudget）。
     *
     * 对普通 poster() 来说构图固定居中：质心 (FOCUS_X/FOCUS_Y) 不参与裁切。今日安排卡片
     * 另走 agendaPoster()，把窄栏的横向窗口向该月角色焦点平移，避免角色贴在分界线上。
     */
    public static Bitmap poster(Context c,int month,int width) {
        int baseWidth=Math.max(1,width);
        int baseHeight=Math.max(1,Math.round(baseWidth/aspect(month)));
        double factor=Math.min(1.0,Math.sqrt(POSTER_MAX_PIXELS/(double)baseWidth/baseHeight));
        int w=Math.max(1,(int)Math.round(baseWidth*factor)), h=Math.max(1,(int)Math.round(baseHeight*factor));
        Bitmap original=image(c,month),out=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);
        Canvas canvas=new Canvas(out); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        int rows=(int)(original.getHeight()*artworkBottom(month));
        canvas.drawBitmap(original,new Rect(0,0,original.getWidth(),rows),new Rect(0,0,w,h),paint);
        return out;
    }

    /**
     * 今日安排卡片的取图：输出位图本身就等于左侧图片栏的宽高比，并把裁切窗口
     * 对准该月插画的细节质心。ImageView 使用 fitCenter，启动器上报尺寸与实际尺寸不一致时
     * 宁可在上下留出少量背景，也不再做第二次横向裁切把角色的脸推到分界线后面。
     */
    public static Bitmap agendaPoster(Context c,int month,int width,int height) {
        int baseWidth=Math.max(1,width),baseHeight=Math.max(1,height);
        double factor=Math.min(1.0,Math.sqrt(POSTER_MAX_PIXELS/(double)baseWidth/baseHeight));
        int w=Math.max(1,(int)Math.round(baseWidth*factor)),h=Math.max(1,(int)Math.round(baseHeight*factor));
        Bitmap original=image(c,month);
        int rows=Math.max(1,Math.round(original.getHeight()*artworkBottom(month)));
        float targetAspect=w/(float)h;
        float sourceAspect=original.getWidth()/(float)rows;
        float sourceWidth=original.getWidth(),sourceHeight=rows;
        if(targetAspect<sourceAspect) sourceWidth=sourceHeight*targetAspect;
        else if(targetAspect>sourceAspect) sourceHeight=sourceWidth/targetAspect;
        int focusIndex=Math.floorMod(month,FOCUS_X.length);
        float centerX=original.getWidth()*FOCUS_X[focusIndex];
        float centerY=rows*FOCUS_Y[focusIndex];
        float left=clamp(centerX-sourceWidth/2f,0f,original.getWidth()-sourceWidth);
        float top=clamp(centerY-sourceHeight/2f,0f,rows-sourceHeight);
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);
        Canvas canvas=new Canvas(out); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Rect sourceRect=new Rect(Math.round(left),Math.round(top),Math.round(left+sourceWidth),Math.round(top+sourceHeight));
        canvas.drawBitmap(original,sourceRect,new Rect(0,0,w,h),paint);
        return out;
    }

    /** 旧接口：桌面卡片插画带。现在与 poster() 同一实现，保留只是为了调用方名字稳定。 */
    public static Bitmap header(Context c,int month,int width) {
        return poster(c,month,width);
    }

    /** 首页插画面板的取景：把插画区段按 cover 铺满，纵向按 CROP_BIAS 偏上保留（零裁切时不起作用）。 */
    private static Bitmap fill(Context c,int month,int width,int height,float bias) {
        int w=Math.max(1,width), h=Math.max(1,height);      // 桌面组件尺寸由启动器给出，可能是 0
        Bitmap original=image(c,month),out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        int rows=(int)(original.getHeight()*artworkBottom(month));
        float scale=Math.max(w/(float)original.getWidth(),h/(float)rows);
        float sourceWidth=w/scale,sourceHeight=h/scale;
        // 只有源图比目标"更高"时才需要挪动取景窗口；否则按比例居中即可。
        float top=Math.max(0,(rows-sourceHeight)*bias);
        float left=Math.max(0,(original.getWidth()-sourceWidth)/2f);
        canvas.drawBitmap(original,
            new Rect(Math.round(left),Math.round(top),Math.round(left+sourceWidth),Math.round(top+sourceHeight)),
            new Rect(0,0,w,h),paint);
        return out;
    }

    private static float clamp(float value,float low,float high) { return value<low?low:value>high?high:value; }

    private Art() {}
}
