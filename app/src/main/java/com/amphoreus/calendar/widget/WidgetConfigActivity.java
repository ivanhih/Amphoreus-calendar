package com.amphoreus.calendar.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import com.amphoreus.calendar.CalendarApp;
import com.amphoreus.calendar.ui.Ui;

public final class WidgetConfigActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setResult(RESULT_CANCELED);
        int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(!Widgets.owned(this,id)) { finish(); return; }
        LinearLayout root=Ui.root(this); Ui.toolbar(this,root,"桌面卡片"); LinearLayout body=Ui.column(this); Ui.pad(body,24,24); root.addView(body);
        body.addView(Ui.text(this,"让每一天，都有插画相伴。",23,Ui.GOLD)); Ui.space(body,24);
        CheckBox show=new CheckBox(this); show.setText(com.amphoreus.calendar.ui.LocaleText.t(this,"在此卡片展示日程标题与日期标记")); show.setTextColor(Ui.TEXT); show.setChecked(getSharedPreferences("widgets",0).getBoolean("events_"+id,true)); body.addView(show);
        Ui.space(body,12); body.addView(Ui.text(this,"打开后，手机桌面可直接看到你的日程。\n月历卡片默认跟随当前月份，也可以单独翻月。",14,Ui.MUTED)); Ui.space(body,28);
        body.addView(Ui.button(this,"保存卡片",()->{
            getSharedPreferences("widgets",0).edit().putBoolean("events_"+id,show.isChecked()).apply();
            CalendarApp.IO.execute(()->{ Widgets.updateAll(this); CalendarRefreshJob.schedule(this); runOnUiThread(()->{ setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)); finish(); }); });
        }));
    }
}
