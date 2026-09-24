package com.amphoreus.calendar.reminder;

import android.app.*;
import android.content.*;
import android.net.Uri;
import com.amphoreus.calendar.*;
import com.amphoreus.calendar.core.CalendarEvent;
import com.amphoreus.calendar.ui.EventActivity;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        long id=intent.getLongExtra("id",-1),trigger=intent.getLongExtra("trigger",Long.MAX_VALUE),occurrence=intent.getLongExtra("occurrence",Long.MIN_VALUE);
        String eventKey=intent.getStringExtra("eventKey"); if(eventKey==null&&id>=0)eventKey="l:"+id;
        String fingerprint=intent.getStringExtra("fingerprint"); PendingResult pending=goAsync();
        final String key=eventKey;
        CalendarApp.IO.execute(()->{
            try {
                CalendarEvent event=key==null?null:((CalendarApp)c.getApplicationContext()).repository().get(key);
                SharedPreferences delivered=c.getSharedPreferences("delivered_reminders",0);
                if(event!=null && Reminders.appReminderMinutes(event)>=0 && Reminders.fingerprint(event).equals(fingerprint) && delivered.getLong("last_"+key.replace(':','_'),Long.MIN_VALUE)!=occurrence && trigger<=System.currentTimeMillis()+1000 && System.currentTimeMillis()-trigger<86400000L) {
                    Intent open=new Intent(c,EventActivity.class).putExtra("key",event.key()).setData(Uri.parse("amphoreus://event/"+event.key()));
                    PendingIntent tap=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    Notification notification=ReminderNotifications.build(c,event,tap);
                    String tag=event.system?"system:"+event.id:"local:"+event.id;
                    try { c.getSystemService(NotificationManager.class).notify(tag,0,notification); delivered.edit().putLong("last_"+key.replace(':','_'),occurrence).apply(); }
                    catch(SecurityException ignored) {
                        // Do not reschedule the same past occurrence every second when the user
                        // has revoked notification permission or disabled the channel.
                        delivered.edit().putLong("last_"+key.replace(':','_'),occurrence).apply();
                    }
                }
                Reminders.reschedule(c);
            } finally { pending.finish(); }
        });
    }
}
