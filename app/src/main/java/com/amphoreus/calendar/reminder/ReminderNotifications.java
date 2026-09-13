package com.amphoreus.calendar.reminder;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import com.amphoreus.calendar.*;
import com.amphoreus.calendar.core.CalendarEvent;
import com.amphoreus.calendar.core.ReminderArtwork;
import com.amphoreus.calendar.ui.Art;
import com.amphoreus.calendar.ui.LocaleText;
import java.util.concurrent.ThreadLocalRandom;

/** Builds the one notification shape used by real reminders and the in-editor preview. */
public final class ReminderNotifications {
    public static Notification build(Context c,CalendarEvent event,PendingIntent tap) {
        Notification.Builder builder=new Notification.Builder(c,Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_calendar)
            .setContentTitle(event.title)
            .setContentText(event.allDay?LocaleText.t(c,"今天的全天日程"):
                event.location.isEmpty()?LocaleText.t(c,"你的日程即将开始"):event.location)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE);
        int artworkIndex=event.reminderArt==CalendarEvent.RANDOM_ART?ReminderArtwork.randomIndex(ThreadLocalRandom.current()):event.reminderArt;
        if(artworkIndex>=0&&artworkIndex<=12) {
            try {
                Bitmap artwork=notificationArtwork(c,artworkIndex);
                builder.setLargeIcon(artwork);
                builder.setStyle(new Notification.BigPictureStyle().bigPicture(artwork)
                    .setSummaryText(LocaleText.t(c,"角色提醒")+" · "+
                        LocaleText.t(c,Art.SPLASH_NAMES[artworkIndex])));
            } catch(RuntimeException ignored) {
                // A missing optional image must never prevent a text notification.
            }
        }
        return builder.build();
    }

    /** Send the exact same visual notification the alarm receiver will send. */
    public static void showPreview(Context c,CalendarEvent event) {
        Reminders.createChannel(c);
        Intent open=new Intent(c,com.amphoreus.calendar.ui.MainActivity.class)
            .setData(Uri.parse("amphoreus://notification-preview"));
        PendingIntent tap=PendingIntent.getActivity(c,20260913,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(NotificationManager.class).notify("preview",20260913,build(c,event,tap));
    }

    private static Bitmap notificationArtwork(Context c,int month) {
        Bitmap source=Art.splashArtwork(c,month);
        int width=Math.min(source.getWidth(),320);
        int height=Math.max(1,Math.round(source.getHeight()*(width/(float)source.getWidth())));
        if(width==source.getWidth()) return source;
        Bitmap scaled=Bitmap.createScaledBitmap(source,width,height,true);
        if(!source.isRecycled())source.recycle();
        return scaled;
    }
    private ReminderNotifications() {}
}
