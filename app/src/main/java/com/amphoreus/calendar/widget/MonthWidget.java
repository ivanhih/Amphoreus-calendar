package com.amphoreus.calendar.widget;

import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import com.amphoreus.calendar.CalendarApp;
import java.time.YearMonth;

public class MonthWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids) { refresh(c); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options) { refresh(c); }
    @Override public void onReceive(Context c,Intent intent) {
        super.onReceive(c,intent);
        String action=intent.getAction();
        if(action!=null && action.startsWith("com.amphoreus.calendar.MONTH_")) {
            int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);
            if(!Widgets.owned(c,id))return;
            SharedPreferences p=c.getSharedPreferences("widgets",0);
            YearMonth month=Widgets.month(c,id);
            if(action.endsWith("PREV"))month=month.minusMonths(1); else if(action.endsWith("NEXT"))month=month.plusMonths(1); else month=YearMonth.now();
            p.edit().putString("month_"+id,month.toString()).putBoolean("follow_"+id,action.endsWith("TODAY")).apply(); refresh(c);
        }
    }
    private void refresh(Context c) { PendingResult pending=goAsync(); CalendarApp.IO.execute(()->{ try { Widgets.updateAll(c); CalendarRefreshJob.schedule(c); } finally { pending.finish(); } }); }
    @Override public void onDeleted(Context c,int[] ids) {
        SharedPreferences.Editor edit=c.getSharedPreferences("widgets",0).edit(); for(int id:ids) { edit.remove("month_"+id).remove("follow_"+id).remove("events_"+id); } edit.apply();
    }
}
