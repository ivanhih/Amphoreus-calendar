package com.amphoreus.calendar;

import android.app.Activity;
import android.appwidget.*;
import android.content.ComponentName;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;

/** Test-only real launcher host. Never included in the application APK. */
public final class WidgetHostActivity extends Activity {
    private AppWidgetHost host;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); host=new AppWidgetHost(this,260910); host.startListening();
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(20),dp(48),dp(20),dp(24)); root.setBackgroundColor(Color.rgb(37,38,52));
        TextView heading=new TextView(this); heading.setText("桌面卡片 · 实际 AppWidgetHost"); heading.setTextColor(Color.WHITE); heading.setTextSize(20); root.addView(heading); setContentView(root);
        for(String name:new String[]{"MonthWidget","AgendaWidget"}) {
            ComponentName provider=new ComponentName("com.amphoreus.calendar","com.amphoreus.calendar.widget."+name); int id=host.allocateAppWidgetId();
            int height=name.equals("MonthWidget")?370:170; Bundle options=new Bundle(); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,340); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,340); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height);
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            if(!manager.bindAppWidgetIdIfAllowed(id,provider,options)) { TextView warning=new TextView(this); warning.setText("Grant widget bind permission to test package first"); root.addView(warning); continue; }
            AppWidgetHostView view=host.createView(this,id,manager.getAppWidgetInfo(id)); view.setAppWidget(id,manager.getAppWidgetInfo(id)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height)); p.topMargin=dp(16); root.addView(view,p);
        }
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { host.stopListening(); host.deleteHost(); super.onDestroy(); }
}
