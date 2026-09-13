package com.amphoreus.calendar.ui;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import com.amphoreus.calendar.CalendarApp;
import com.amphoreus.calendar.data.*;
import com.amphoreus.calendar.reminder.Reminders;
import com.amphoreus.calendar.widget.*;
import java.util.*;

public final class SettingsActivity extends Activity {
    private LinearLayout root,body,sourcesView;
    private ScrollView settingsScroll;
    private TextView splashAction;
    private SharedPreferences preferences;
    private EventRepository repository;
    private int generation;
    private boolean rendered;
    @Override public void onCreate(Bundle state) { super.onCreate(state); preferences=getSharedPreferences("settings",0); repository=((CalendarApp)getApplication()).repository(); }
    @Override public void onResume() { super.onResume(); if(!rendered) { render(); rendered=true; } }
    private void render() {
        rendered=true;
        int restoreY=settingsScroll==null?0:settingsScroll.getScrollY();
        root=Ui.root(this); Ui.toolbar(this,root,"设置"); ScrollView scroll=new ScrollView(this); settingsScroll=scroll; body=Ui.column(this); Ui.pad(body,22,10); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.post(()->scroll.scrollTo(0,restoreY));
        section("日历与同步","连接手机日历后，可直接读取与编辑系统日程。账号云同步由手机上已有的同步服务完成。");
        action(repository.system.canWrite()?"日历权限已开启 · 管理权限":"连接系统日历",()->{
            if(repository.system.canWrite()) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
            else requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},100);
        });
        action("账号同步设置",()->{ try { startActivity(new Intent("android.settings.SYNC_SETTINGS")); } catch(ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); } });
        if(!repository.system.canWrite()) action("权限被拒绝？前往应用权限设置",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        sourcesView=Ui.column(this); body.addView(sourcesView); loadSources();
        section("日期显示","首页上半是当月原版海报插画，下半是应用自己计算的真实日期格，两者在同一页面；主题插画按月份切换，真实日历支持跨年和闰年。");
        Switch monday=new Switch(this); monday.setText(LocaleText.t(this,"每周从周一开始")); monday.setTextColor(Ui.TEXT); Ui.pad(monday,0,12); monday.setChecked(preferences.getBoolean("monday",true)); monday.setOnCheckedChangeListener((v,checked)->{ preferences.edit().putBoolean("monday",checked).apply(); ((CalendarApp)getApplication()).changed(); }); body.addView(monday);
        section("语言与外观","选择应用语言，并设置浅色、深色或按时间自动切换的颜色。");
        action(languageSummary(),this::editLanguage);
        action(themeSummary(),this::editTheme);
        Switch autoTheme=new Switch(this); autoTheme.setText(LocaleText.t(this,"按时间自动切换颜色（白天浅色，晚上深色）")); autoTheme.setTextColor(Ui.TEXT); Ui.pad(autoTheme,0,12); autoTheme.setChecked(AppSettings.autoTheme(this));
        autoTheme.setOnCheckedChangeListener((v,checked)->{ preferences.edit().putBoolean(AppSettings.AUTO_THEME_KEY,checked).apply(); Ui.applyTheme(this); ((CalendarApp)getApplication()).changed(); recreate(); }); body.addView(autoTheme);
        section("开屏动画","每次从图标启动应用时，随机展示所选素材的角色插画和寄语；原海报下方的月份日历、标题与印刷文字不会出现在开屏。");
        splashAction=action(splashSummary(),this::editSplashPool);
        section("本地日程提醒","系统日程的提醒由手机日历服务发送。本地日程的通知与精确提醒状态如下；厂商省电限制仍可能影响后台提醒。");
        boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();
        action(notifications?"通知已允许 · 查看设置":"允许提醒通知",()->{
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        action(Reminders.exactAllowed(this)?"精确提醒已允许":"开启精确提醒（当前可能延迟）",()->{
            if(Build.VERSION.SDK_INT>=31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))); else Ui.toast(this,"当前系统支持精确提醒");
        });
        section("桌面小组件","也可以长按手机桌面 → 小组件 → 翁法罗斯月历。添加后可长按调整尺寸。卡片可配置是否展示日程。");
        action("添加主题月历卡片",()->pin(MonthWidget.class)); action("添加今日安排卡片",()->pin(AgendaWidget.class)); action("添加图文月历卡片",()->pin(SplitMonthWidget.class));
        section("关于","翁法罗斯月历 0.1.0\n离线图片 · 本地日程 · 系统日历\n原图中的署名与版权信息保留在完整海报中。\n本应用不包含广告、统计服务或账号登录。"); Ui.space(body,24);
    }
    private void section(String title,String note) { Ui.space(body,18); TextView h=Ui.text(this,title,20,Ui.GOLD); body.addView(h); TextView n=Ui.text(this,note,13,Ui.MUTED); n.setLineSpacing(Ui.dp(this,4),1); Ui.pad(n,0,10); body.addView(n); }
    private TextView action(String label,Runnable action) { TextView button=Ui.button(this,label,action); body.addView(button); Ui.space(body,8); return button; }
    private String splashSummary() {
        Set<String> selected=SplashActivity.selectedMonthKeys(preferences);
        if(selected.size()>=13)return LocaleText.t(this,"开屏素材：全部 13 张（随机）");
        if(LocaleText.isEnglish(this))return "Splash artwork: "+selected.size()+" selected (tap to choose)";
        if(LocaleText.isTraditional(this))return "開屏素材：已選 "+selected.size()+" 張（點選選擇）";
        return "开屏素材：已选 "+selected.size()+" 张（点击选择）";
    }
    private void editSplashPool() {
        Set<String> selected=SplashActivity.selectedMonthKeys(preferences);
        LinearLayout panel=Ui.column(this); Ui.pad(panel,16,0);
        LinearLayout controls=Ui.row(this);
        TextView selectAll=Ui.button(this,"全选",()->{}),selectNone=Ui.button(this,"全不选",()->{});
        controls.addView(selectAll,new LinearLayout.LayoutParams(0,-2,1));
        Ui.space(controls,0);
        controls.addView(selectNone,new LinearLayout.LayoutParams(0,-2,1)); panel.addView(controls);
        ScrollView listScroll=new ScrollView(this);
        int listHeight=Math.min(Ui.dp(this,470),Math.max(Ui.dp(this,250),getResources().getDisplayMetrics().heightPixels-Ui.dp(this,300)));
        listScroll.setLayoutParams(new LinearLayout.LayoutParams(-1,listHeight));
        LinearLayout list=Ui.column(this); Ui.pad(list,0,6); listScroll.addView(list); panel.addView(listScroll);
        CheckBox[] boxes=new CheckBox[13];
        for(int i=0;i<=12;i++) {
            final int index=i;
            LinearLayout row=Ui.row(this); row.setBackground(Ui.background(Ui.CARD,12,this)); Ui.pad(row,8,5);
            ImageView thumbnail=new ImageView(this); thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP); thumbnail.setContentDescription(LocaleText.splashLabel(this,i)); thumbnail.setBackground(Ui.background(Ui.BG,10,this));
            try { thumbnail.setImageBitmap(Art.splashArtwork(this,i)); } catch(RuntimeException ignored) { }
            row.addView(thumbnail,new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,88)));
            CheckBox box=new CheckBox(this); boxes[i]=box; box.setText(LocaleText.splashLabel(this,i)); box.setTextColor(Ui.TEXT); box.setTextSize(14); box.setGravity(android.view.Gravity.CENTER_VERTICAL); box.setChecked(selected.contains(String.valueOf(i)));
            row.addView(box,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(v->box.setChecked(!box.isChecked()));
            list.addView(row); if(i<12) Ui.space(list,6);
        }
        selectAll.setOnClickListener(v->{ for(CheckBox box:boxes)box.setChecked(true); });
        selectNone.setOnClickListener(v->{ for(CheckBox box:boxes)box.setChecked(false); });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(LocaleText.t(this,"选择随机开屏素材"))
            .setView(panel)
            .setNegativeButton(LocaleText.t(this,"取消"),null)
            .setPositiveButton(LocaleText.t(this,"保存"),null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Set<String> chosen=new HashSet<>(); for(int i=0;i<boxes.length;i++)if(boxes[i].isChecked())chosen.add(String.valueOf(i));
            if(chosen.isEmpty()) { Ui.toast(this,LocaleText.t(this,"至少保留一张开屏图片")); return; }
            preferences.edit().putStringSet(SplashActivity.MONTH_POOL_KEY,chosen).apply();
            if(splashAction!=null)splashAction.setText(splashSummary());
            dialog.dismiss();
        }));
        dialog.show();
    }

    private String languageSummary() {
        String value=AppSettings.language(this);
        if(AppSettings.LANGUAGE_EN.equals(value))return LocaleText.t(this,"语言：English");
        if(AppSettings.LANGUAGE_ZH_CN.equals(value))return LocaleText.t(this,"语言：简体中文");
        if(AppSettings.LANGUAGE_ZH_TW.equals(value))return LocaleText.t(this,"语言：繁體中文");
        return LocaleText.t(this,"语言：跟随系统");
    }
    private void editLanguage() {
        String[] labels=LocaleText.languageLabels(this); String value=AppSettings.language(this);
        int checked=AppSettings.LANGUAGE_ZH_CN.equals(value)?1:AppSettings.LANGUAGE_ZH_TW.equals(value)?2:AppSettings.LANGUAGE_EN.equals(value)?3:0;
        new AlertDialog.Builder(this).setTitle(LocaleText.t(this,"语言与外观")).setSingleChoiceItems(labels,checked,(dialog,which)->{
            String[] values={AppSettings.LANGUAGE_SYSTEM,AppSettings.LANGUAGE_ZH_CN,AppSettings.LANGUAGE_ZH_TW,AppSettings.LANGUAGE_EN};
            preferences.edit().putString(AppSettings.LANGUAGE_KEY,values[which]).apply(); dialog.dismiss(); Ui.applyTheme(this); ((CalendarApp)getApplication()).changed(); recreate();
        }).setNegativeButton(LocaleText.t(this,"取消"),null).show();
    }
    private String themeSummary() {
        if(AppSettings.autoTheme(this))return LocaleText.t(this,"颜色：按时间自动（白天浅色 / 晚上深色）");
        String value=AppSettings.themeMode(this);
        if(AppSettings.THEME_LIGHT.equals(value))return LocaleText.t(this,"颜色：浅色");
        if(AppSettings.THEME_DARK.equals(value))return LocaleText.t(this,"颜色：深色");
        return LocaleText.t(this,"颜色：跟随系统");
    }
    private void editTheme() {
        String[] labels=LocaleText.themeLabels(this); String value=AppSettings.themeMode(this);
        int checked=AppSettings.THEME_LIGHT.equals(value)?1:AppSettings.THEME_DARK.equals(value)?2:0;
        new AlertDialog.Builder(this).setTitle(LocaleText.t(this,"颜色")).setSingleChoiceItems(labels,checked,(dialog,which)->{
            String[] values={AppSettings.THEME_SYSTEM,AppSettings.THEME_LIGHT,AppSettings.THEME_DARK};
            preferences.edit().putString(AppSettings.THEME_MODE_KEY,values[which]).putBoolean(AppSettings.AUTO_THEME_KEY,false).apply(); dialog.dismiss(); Ui.applyTheme(this); ((CalendarApp)getApplication()).changed(); recreate();
        }).setNegativeButton(LocaleText.t(this,"取消"),null).show();
    }
    private void loadSources() {
        int token=++generation; sourcesView.addView(Ui.text(this,"正在读取日历来源…",13,Ui.MUTED));
        CalendarApp.IO.execute(()->{ try { List<CalendarSource> sources=repository.sources(); runOnUiThread(()->{ if(isDestroyed()||token!=generation)return;
            sourcesView.removeAllViews();
            if(repository.system.canRead() && sources.size()==1) sourcesView.addView(Ui.text(this,"设备还没有系统日历，请先在手机日历中添加账号。",13,Ui.MUTED));
            Set<String> hidden=new HashSet<>(preferences.getStringSet("hiddenCalendars",Collections.emptySet()));
            sourcesView.addView(Ui.text(this,"选择显示的日历",14,Ui.GOLD));
            for(CalendarSource source:sources) { CheckBox check=new CheckBox(this); check.setText(source.toString()); check.setTextColor(Ui.TEXT); check.setTextSize(13); check.setChecked(!hidden.contains(String.valueOf(source.id)));
                check.setOnCheckedChangeListener((v,checked)->{ Set<String> changed=new HashSet<>(preferences.getStringSet("hiddenCalendars",Collections.emptySet())); if(checked)changed.remove(String.valueOf(source.id)); else changed.add(String.valueOf(source.id)); preferences.edit().putStringSet("hiddenCalendars",changed).apply(); ((CalendarApp)getApplication()).changed(); }); sourcesView.addView(check); }
            List<CalendarSource> writable=new ArrayList<>(); for(CalendarSource source:sources)if(source.writable)writable.add(source);
            sourcesView.addView(Ui.button(this,"默认新建日历："+defaultName(sources),()->new AlertDialog.Builder(this).setTitle("默认新建日历").setItems(writable.stream().map(CalendarSource::toString).toArray(String[]::new),(dialog,which)->{ preferences.edit().putLong("defaultCalendar",writable.get(which).id).apply(); render(); }).show()));
        }); } catch(Exception e) { runOnUiThread(()->{ if(isDestroyed()||token!=generation)return; sourcesView.removeAllViews(); sourcesView.addView(Ui.text(this,"无法读取日历："+e.getMessage(),13,Ui.MUTED)); }); } });
    }
    private String defaultName(List<CalendarSource> sources) { long id=preferences.getLong("defaultCalendar",-1); for(CalendarSource source:sources)if(source.id==id)return source.name; return "原日历不可用（将使用本地日历）"; }
    private void pin(Class<?> type) {
        AppWidgetManager manager=getSystemService(AppWidgetManager.class);
        if(manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this,type),null,null);
        else Ui.toast(this,"请长按桌面，从“小组件”列表手动添加");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==100 && repository.system.canWrite() && !preferences.contains("defaultCalendar")) {
            CalendarApp.IO.execute(()->{ try { for(CalendarSource source:repository.system.sources()) if(source.writable) { preferences.edit().putLong("defaultCalendar",source.id).apply(); break; } } catch(Exception e) { runOnUiThread(()->{ if(!isDestroyed())Ui.error(this,e); }); } finally { runOnUiThread(()->{ if(!isDestroyed())render(); }); } });
        } else render();
        ((CalendarApp)getApplication()).changed();
    }
}
