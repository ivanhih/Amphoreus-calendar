package com.amphoreus.calendar.ui;

import android.app.*;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.amphoreus.calendar.CalendarApp;
import com.amphoreus.calendar.core.*;
import com.amphoreus.calendar.data.*;
import com.amphoreus.calendar.reminder.ReminderNotifications;
import com.amphoreus.calendar.reminder.Reminders;
import java.time.*;
import java.util.*;

public final class EventActivity extends Activity {
    private CalendarEvent event;
    private EventRepository repository;
    private List<CalendarSource> sources;
    private LinearLayout root,form;
    private EditText title,location,notes;
    private Switch allDay;
    private Spinner calendar,repeat,reminder,systemReminder;
    private LinearLayout systemReminderSection;
    private final List<Integer> reminderValues=new ArrayList<>(Arrays.asList(-1,0,5,10,15,30,60,1440,CalendarEvent.DEFAULT_IMPORTED_REMINDER_MINUTES));
    private LocalDate startDate,endDate;
    private LocalTime startTime,endTime;
    private TextView startDateButton,startTimeButton,endDateButton,endTimeButton,saveButton,reminderArtButton;
    private ImageView reminderPreview;
    private Bitmap reminderPreviewBitmap;
    private int reminderArt=-1;
    private int randomPreviewArt=ReminderArtwork.randomIndex(new Random());
    private int appReminderMinutes=-1;
    private boolean systemReminderEdited;
    private boolean endTimeEdited;
    private boolean saving;
    private CalendarEvent pendingSavedEvent;
    private boolean previewAfterNotificationPermission;
    private Bundle restored;
    private SaveOperation saveOperation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int REQUEST_SAVE_NOTIFICATION=102;
    private static final int REQUEST_PREVIEW_NOTIFICATION=103;
    private static final class SaveOperation {
        volatile CalendarEvent saved;
        volatile Exception failure;
        volatile boolean complete;
    }
    public static void create(Context c,LocalDate date) { c.startActivity(new Intent(c,EventActivity.class).putExtra("date",date.toString())); }
    public static void open(Context c,String key) { c.startActivity(new Intent(c,EventActivity.class).putExtra("key",key)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); restored=state; saveOperation=(SaveOperation)getLastNonConfigurationInstance(); repository=((CalendarApp)getApplication()).repository(); root=Ui.root(this); Ui.toolbar(this,root,"日程"); root.addView(Ui.text(this,"正在读取…",16,Ui.MUTED));
        CalendarApp.IO.execute(()->{
            try {
                if(saveOperation!=null && saveOperation.complete && saveOperation.saved!=null) { runOnUiThread(()->{ if(!isDestroyed())afterSave(saveOperation.saved); }); return; }
                String key=getIntent().getStringExtra("key"); CalendarEvent loaded=key==null?newEvent():repository.get(key);
                if(loaded==null) throw new IllegalStateException("此日程已被删除，请返回刷新");
                List<CalendarSource> calendars=repository.sources();
                runOnUiThread(()->{ if(isDestroyed())return; event=loaded; sources=calendars; initializeDates(); render(); if(saveOperation!=null)waitForSaveOperation(); });
            } catch(Exception e) { runOnUiThread(()->{ if(isDestroyed())return; root.removeViews(1,root.getChildCount()-1); TextView error=Ui.text(this,e.getMessage(),16,Ui.MUTED); Ui.pad(error,24,24); root.addView(error); }); }
        });
    }
    private CalendarEvent newEvent() {
        CalendarEvent e=new CalendarEvent(); LocalDate date=LocalDate.now();
        try { date=LocalDate.parse(getIntent().getStringExtra("date")); } catch(Exception ignored) {}
        LocalDateTime start=CalendarMath.defaultStart(date,LocalDateTime.now());
        ZoneId zone=ZoneId.systemDefault(); e.zone=zone.getId(); e.start=EventTime.toMillis(start.toLocalDate(),start.toLocalTime(),zone);
        LocalDateTime end=EventTime.endAfterHour(start.toLocalDate(),start.toLocalTime()); e.end=EventTime.toMillis(end.toLocalDate(),end.toLocalTime(),zone);
        e.calendarId=getSharedPreferences("settings",0).getLong("defaultCalendar",-1); e.system=e.calendarId!=-1;
        e.reminderMinutes=e.system?-1:0; e.appReminderMinutes=0; e.reminderArt=CalendarEvent.RANDOM_ART;
        return e;
    }
    private void initializeDates() {
        ZoneId zone=event.allDay?ZoneOffset.UTC:ZoneId.of(event.zone);
        startDate=EventTime.date(event.start,zone); endDate=event.allDay?EventTime.date(event.end,zone).minusDays(1):EventTime.date(event.end,zone);
        startTime=EventTime.time(event.start,zone).withSecond(0).withNano(0); endTime=EventTime.time(event.end,zone).withSecond(0).withNano(0);
        appReminderMinutes=event.system?event.appReminderMinutes:event.reminderMinutes; reminderArt=event.reminderArt;
        endTimeEdited=event.id!=0;
        if(restored!=null) {
            startDate=LocalDate.parse(restored.getString("startDate",startDate.toString())); endDate=LocalDate.parse(restored.getString("endDate",endDate.toString()));
            startTime=LocalTime.parse(restored.getString("startTime",startTime.toString())); endTime=LocalTime.parse(restored.getString("endTime",endTime.toString()));
            reminderArt=restored.getInt("reminderArt",reminderArt); endTimeEdited=restored.getBoolean("endTimeEdited",endTimeEdited);
        }
        if(reminderArt==CalendarEvent.RANDOM_ART)randomPreviewArt=ReminderArtwork.randomIndex(new Random());
    }
    private void render() {
        root=Ui.root(this); Ui.toolbar(this,root,event.id==0?"新建日程":"日程详情");
        ScrollView scroll=new ScrollView(this); form=Ui.column(this); Ui.pad(form,22,12); scroll.addView(form); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(event.id!=0 && (!event.writable||event.advanced)) { renderProtected(); return; }
        if(event.id!=0 && !event.recurrence.isEmpty()) hint("正在编辑整个重复系列，包括过去及未来的日程。");
        title=field("标题",event.title,"给这件事取个名字",true);
        label("所属日历"); List<CalendarSource> writable=new ArrayList<>(); for(CalendarSource s:sources) if(s.writable) writable.add(s);
        sources=writable;
        calendar=spinner(sources.toArray()); int selected=0; for(int i=0;i<sources.size();i++) if(sources.get(i).id==event.calendarId)selected=i;
        if(event.id!=0 && event.system && sources.get(selected).id!=event.calendarId) { hint("原日历已不可写，请返回刷新。"); return; }
        calendar.setSelection(selected); if(event.id!=0) calendar.setEnabled(false);
        calendar.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { refreshReminderSections(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { refreshReminderSections(); }
        });
        if(event.id==0 && event.system && sources.get(selected).id!=event.calendarId) hint("原默认系统日历不可用，本次已切换为本地日历。");
        hint("选择系统账号日历后，保存的日程会同步到该账号。选择本地日历则仅保存在此应用。");
        allDay=new Switch(this); allDay.setText(LocaleText.t(this,"全天日程")); allDay.setTextColor(Ui.TEXT); allDay.setTextSize(16); Ui.pad(allDay,0,14); allDay.setChecked(restored==null?event.allDay:restored.getBoolean("allDay")); form.addView(allDay);
        label("开始");
        LinearLayout startRow=Ui.row(this);
        startDateButton=Ui.button(this,"",()->pickDate(true)); startTimeButton=Ui.button(this,"",()->pickTime(true));
        addDateTimeButton(startRow,startDateButton); addDateTimeButton(startRow,startTimeButton); form.addView(startRow);
        label("结束（全天事件包含所选结束日）");
        LinearLayout endRow=Ui.row(this);
        endDateButton=Ui.button(this,"",()->pickDate(false)); endTimeButton=Ui.button(this,"",()->pickTime(false));
        addDateTimeButton(endRow,endDateButton); addDateTimeButton(endRow,endTimeButton); form.addView(endRow); updateDates();
        allDay.setOnCheckedChangeListener((button,checked)->{ if(!endTimeEdited) { if(checked)endDate=startDate; else setDefaultEndFromStart(); } updateDates(); });
        hint("时区："+event.zone+"。日期和时间分开选择；开始时间修改后，未手动调整的结束时间会自动设为一小时后。");
        label("重复"); repeat=spinner(Recurrence.LABELS); repeat.setSelection(Math.max(0,Arrays.asList(Recurrence.RULES).indexOf(event.recurrence)));
        hint("每月 29–31 日或每年 2 月 29 日重复时，无对应日期的月份/年份会跳过。");
        renderReminderSections();
        location=field("地点",event.location,"地点（选填）",true); notes=field("备注",event.notes,"想记住的细节（选填）",false);
        if(restored!=null) { title.setText(restored.getString("title",event.title)); location.setText(restored.getString("location",event.location)); notes.setText(restored.getString("notes",event.notes)); repeat.setSelection(restored.getInt("repeat",repeat.getSelectedItemPosition())); reminder.setSelection(Math.min(restored.getInt("reminder",reminder.getSelectedItemPosition()),reminderValues.size()-1)); if(systemReminder!=null)systemReminder.setSelection(Math.min(restored.getInt("systemReminder",systemReminder.getSelectedItemPosition()),reminderValues.size()-1)); long calendarId=restored.getLong("calendar",event.calendarId); for(int i=0;i<sources.size();i++)if(sources.get(i).id==calendarId)calendar.setSelection(i); }
        Ui.space(form,18); saveButton=Ui.button(this,"保存日程",this::save); saveButton.setBackground(Ui.background(Ui.GOLD,16,this)); saveButton.setTextColor(Ui.BG); form.addView(saveButton);
        if(event.id!=0) { Ui.space(form,14); TextView delete=Ui.button(this,event.recurrence.isEmpty()?"删除日程":"删除整个重复系列",this::confirmDelete); delete.setTextColor(0xffefa7a7); form.addView(delete); }
        Ui.space(form,24);
    }
    private void addDateTimeButton(LinearLayout row,TextView button) {
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
        if(row.getChildCount()>0)params.setMargins(Ui.dp(this,8),0,0,0);
        row.addView(button,params);
    }
    private void renderReminderSections() {
        List<String> labels=reminderLabels();
        systemReminderSection=Ui.column(this);
        label(systemReminderSection,"系统日历提醒");
        systemReminder=spinnerInto(systemReminderSection,labels.toArray()); systemReminder.setSelection(reminderSelection(event.reminderMinutes));
        systemReminder.setOnTouchListener((view,motion)->{ if(motion.getAction()==MotionEvent.ACTION_DOWN)systemReminderEdited=true; return false; });
        hint(systemReminderSection,"由手机日历服务发送；关闭这里不会影响本应用的独立提醒。"); form.addView(systemReminderSection);
        label(event.system?"应用内提醒":"提前提醒");
        reminder=spinner(labels.toArray()); reminder.setSelection(reminderSelection(appReminderMinutes));
        hint(event.system?"由本应用独立发送，可用于节日、只读日历和手机日历中的其他事件。":"本地日程由本应用发送；全天日程默认在当天 09:00 提醒。");
        addReminderArtControls(form); refreshReminderSections();
    }
    private void renderAppReminderControls(LinearLayout parent) {
        label(parent,"应用内提醒"); List<String> labels=reminderLabels();
        reminder=spinnerInto(parent,labels.toArray()); reminder.setSelection(reminderSelection(appReminderMinutes));
        hint(parent,"由本应用独立发送，可用于节日、只读日历和手机日历中的其他事件。"); addReminderArtControls(parent);
    }
    private void addReminderArtControls(LinearLayout parent) {
        label(parent,"提醒图片"); LinearLayout reminderArtRow=Ui.row(this);
        reminderPreview=new ImageView(this); reminderPreview.setScaleType(ImageView.ScaleType.CENTER_CROP); reminderPreview.setBackground(Ui.background(Ui.CARD,12,this));
        reminderArtRow.addView(reminderPreview,new LinearLayout.LayoutParams(Ui.dp(this,78),Ui.dp(this,100)));
        reminderArtButton=Ui.button(this,reminderArtLabel(),this::editReminderArt);
        LinearLayout.LayoutParams reminderButtonParams=new LinearLayout.LayoutParams(0,-2,1); reminderButtonParams.setMargins(Ui.dp(this,12),0,0,0); reminderArtRow.addView(reminderArtButton,reminderButtonParams);
        parent.addView(reminderArtRow); refreshReminderPreview(); parent.addView(Ui.button(this,"发送通知预览",this::previewReminder));
    }
    private List<String> reminderLabels() {
        if(!reminderValues.contains(event.reminderMinutes))reminderValues.add(event.reminderMinutes);
        if(!reminderValues.contains(appReminderMinutes))reminderValues.add(appReminderMinutes);
        List<String> labels=new ArrayList<>(); for(int minutes:reminderValues)labels.add(reminderLabel(minutes)); return labels;
    }
    private int reminderSelection(int minutes) { int index=reminderValues.indexOf(minutes); return index<0?0:index; }
    private boolean selectedCalendarIsSystem() {
        return calendar!=null&&sources!=null&&calendar.getSelectedItemPosition()>=0&&calendar.getSelectedItemPosition()<sources.size()&&sources.get(calendar.getSelectedItemPosition()).id!=-1;
    }
    private void refreshReminderSections() {
        boolean systemCalendar=selectedCalendarIsSystem();
        // 新建日程如果从本地日历切换到手机日历，也要保持“系统日历不提醒”的默认值；
        // 已存在的日程则保留用户当前选择，不覆盖已有设置。
        if(event!=null&&event.id==0&&!systemReminderEdited&&systemCalendar&&event.reminderMinutes>=0&&systemReminder!=null)
            systemReminder.setSelection(reminderSelection(-1));
        if(systemReminderSection!=null)systemReminderSection.setVisibility(systemCalendar?View.VISIBLE:View.GONE);
    }
    private void renderProtected() {
        TextView heading=Ui.text(this,event.title,28,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); form.addView(heading); Ui.space(form,18);
        hint(event.calendarName+"\n"+startDate+" "+(event.allDay?"全天":startTime)+" → "+endDate+" "+(event.allDay?"":endTime));
        if(!event.location.isEmpty()) hint("地点："+event.location); if(!event.notes.isEmpty()) hint(event.notes);
        if(event.imported) hint("来自 ICS 订阅，只读；下一次同步可能更新标题、时间和地点。此应用可单独为它发送提醒。");
        else hint(event.writable?"此日程含复杂重复规则、例外或参会信息。请使用系统日历编辑，以保留完整信息。":"此日历为只读，或未获得写入权限。");
        if(event.imported) {
            appReminderMinutes=event.appReminderMinutes; reminderArt=event.reminderArt;
            renderAppReminderControls(form); Ui.space(form,12); saveButton=Ui.button(this,"保存应用提醒",this::saveProtectedAppReminder); saveButton.setBackground(Ui.background(Ui.GOLD,16,this)); saveButton.setTextColor(Ui.BG); form.addView(saveButton); Ui.space(form,12);
            return;
        }
        if(event.system) {
            renderAppReminderControls(form); Ui.space(form,12); saveButton=Ui.button(this,"保存应用提醒",this::saveProtectedAppReminder); saveButton.setBackground(Ui.background(Ui.GOLD,16,this)); saveButton.setTextColor(Ui.BG); form.addView(saveButton); Ui.space(form,12);
        }
        form.addView(Ui.button(this,"在系统日历中打开",()->{
            Intent intent=new Intent(Intent.ACTION_VIEW,ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,event.id));
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,event.start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,event.end);
            try { startActivity(intent); } catch(ActivityNotFoundException e) { Ui.toast(this,"手机没有可处理此事件的系统日历应用"); }
        }));
    }
    private void waitForSaveOperation() {
        if (saveOperation == null || saveButton == null) return;
        saving = !saveOperation.complete;
        saveButton.setEnabled(false);
        saveButton.setText(LocaleText.t(this,"正在保存…"));
        handler.postDelayed(() -> {
            if (isDestroyed() || saveOperation == null) return;
            if (!saveOperation.complete) { waitForSaveOperation(); return; }
            if (saveOperation.saved != null) { afterSave(saveOperation.saved); return; }
            saving = false; saveButton.setEnabled(true); saveButton.setText(LocaleText.t(this,"保存日程"));
            Ui.error(this, saveOperation.failure == null ? new IllegalStateException("保存失败，请重试") : saveOperation.failure);
        }, 100);
    }
    private void label(String value) { label(form,value); }
    private void label(LinearLayout parent,String value) { TextView label=Ui.text(this,value,12,Ui.GOLD); Ui.pad(label,0,12); parent.addView(label); }
    private void hint(String value) { hint(form,value); }
    private void hint(LinearLayout parent,String value) { TextView hint=Ui.text(this,value,12,Ui.MUTED); hint.setLineSpacing(Ui.dp(this,3),1); Ui.pad(hint,0,10); parent.addView(hint); }
    private EditText field(String name,String value,String placeholder,boolean single) {
        label(name); EditText input=new EditText(this); input.setText(value); input.setTextColor(Ui.TEXT); input.setHint(LocaleText.t(this,placeholder)); input.setHintTextColor(Ui.MUTED); input.setTextSize(name.equals("标题")?22:16); input.setSingleLine(single);
        input.setInputType(InputType.TYPE_CLASS_TEXT|(single?InputType.TYPE_TEXT_FLAG_CAP_SENTENCES:InputType.TYPE_TEXT_FLAG_MULTI_LINE)); if(!single)input.setMinLines(3);
        form.addView(input,new LinearLayout.LayoutParams(-1,-2)); return input;
    }
    private Spinner spinner(Object[] choices) { return spinnerInto(form,choices); }
    private Spinner spinnerInto(LinearLayout parent,Object[] choices) {
        Object[] localized=choices.clone(); for(int i=0;i<localized.length;i++)if(localized[i] instanceof String)localized[i]=LocaleText.t(this,(String)localized[i]);
        Spinner s=new Spinner(this,Spinner.MODE_DROPDOWN); ArrayAdapter<Object> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,localized); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter); parent.addView(s,new LinearLayout.LayoutParams(-1,Ui.dp(this,52))); return s;
    }
    private String reminderLabel(int minutes) {
        if(minutes<0)return LocaleText.t(this,"不提醒"); if(minutes==0)return LocaleText.t(this,"日程开始时");
        if(minutes==1440)return LocaleText.t(this,"提前 1 天");
        if(minutes==CalendarEvent.DEFAULT_IMPORTED_REMINDER_MINUTES)return LocaleText.t(this,"提前 3 天");
        if(LocaleText.isEnglish(this))return minutes+" minutes before";
        if(LocaleText.isTraditional(this))return "提前 "+minutes+" 分鐘";
        return "提前 "+minutes+" 分钟";
    }
    private String reminderArtLabel() {
        if(reminderArt==CalendarEvent.RANDOM_ART)return LocaleText.t(this,"随机角色图片");
        return reminderArt<0?LocaleText.t(this,"不使用角色图片"):LocaleText.splashLabel(this,reminderArt);
    }
    private void rerollRandomPreview() {
        if(reminderArt!=CalendarEvent.RANDOM_ART)return;
        randomPreviewArt=ReminderArtwork.randomIndex(new Random()); refreshReminderPreview();
    }
    private void refreshReminderPreview() {
        if(reminderPreview==null)return;
        if(reminderPreviewBitmap!=null&&!reminderPreviewBitmap.isRecycled()) { reminderPreviewBitmap.recycle(); reminderPreviewBitmap=null; }
        if(reminderArt<0&&reminderArt!=CalendarEvent.RANDOM_ART) {
            reminderPreview.setImageDrawable(null); reminderPreview.setBackground(Ui.background(Ui.CARD,12,this)); reminderPreview.setContentDescription(LocaleText.t(this,"不使用角色图片"));
        } else {
            try {
                int previewArt=reminderArt==CalendarEvent.RANDOM_ART?randomPreviewArt:reminderArt;
                Bitmap source=Art.splashArtwork(this,previewArt);
                reminderPreviewBitmap=Bitmap.createScaledBitmap(source,Ui.dp(this,78),Ui.dp(this,100),true);
                if(source!=reminderPreviewBitmap&&!source.isRecycled())source.recycle();
                reminderPreview.setBackground(null); reminderPreview.setImageBitmap(reminderPreviewBitmap); reminderPreview.setContentDescription(reminderArt==CalendarEvent.RANDOM_ART?LocaleText.t(this,"随机角色图片"):LocaleText.splashLabel(this,reminderArt));
            } catch(RuntimeException ignored) {
                reminderPreview.setImageDrawable(null); reminderPreview.setBackground(Ui.background(Ui.CARD,12,this));
            }
        }
        if(reminderArtButton!=null)reminderArtButton.setText(reminderArtLabel());
    }
    private void editReminderArt() {
        rerollRandomPreview();
        LinearLayout panel=Ui.column(this); Ui.pad(panel,14,0); ScrollView scroll=new ScrollView(this); LinearLayout list=Ui.column(this); Ui.pad(list,0,4); scroll.addView(list); panel.addView(scroll,new LinearLayout.LayoutParams(-1,Ui.dp(this,460)));
        RadioButton[] choices=new RadioButton[15]; final AlertDialog[] dialogHolder=new AlertDialog[1];
        for(int position=0;position<15;position++) {
            final int selected=position==0?CalendarEvent.RANDOM_ART:position==1?-1:position-2;
            LinearLayout row=Ui.row(this); row.setBackground(Ui.background(Ui.CARD,12,this)); Ui.pad(row,7,4);
            ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackground(Ui.background(Ui.BG,10,this));
            if(selected==CalendarEvent.RANDOM_ART||selected>=0) {
                try {
                    int thumbnailArt=selected==CalendarEvent.RANDOM_ART?randomPreviewArt:selected;
                    Bitmap source=Art.splashArtwork(this,thumbnailArt); Bitmap thumbnail=Bitmap.createScaledBitmap(source,Ui.dp(this,58),Ui.dp(this,74),true);
                    if(source!=thumbnail&&!source.isRecycled())source.recycle(); image.setImageBitmap(thumbnail);
                } catch(RuntimeException ignored) { }
            }
            row.addView(image,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,74)));
            RadioButton choice=new RadioButton(this); choices[position]=choice; choice.setText(selected==CalendarEvent.RANDOM_ART?LocaleText.t(this,"随机角色图片"):selected<0?LocaleText.t(this,"不使用角色图片"):LocaleText.splashLabel(this,selected)); choice.setTextColor(Ui.TEXT); choice.setTextSize(14); choice.setChecked(reminderArt==selected);
            row.addView(choice,new LinearLayout.LayoutParams(0,-2,1));
            View.OnClickListener select=v->{ for(RadioButton other:choices)if(other!=null)other.setChecked(false); choice.setChecked(true); reminderArt=selected; if(selected==CalendarEvent.RANDOM_ART)rerollRandomPreview(); else refreshReminderPreview(); if(dialogHolder[0]!=null)dialogHolder[0].dismiss(); };
            row.setOnClickListener(select); choice.setOnClickListener(select);
            list.addView(row); if(position<14)Ui.space(list,5);
        }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(LocaleText.t(this,"选择提醒图片")).setView(panel).setNegativeButton(LocaleText.t(this,"取消"),null).create();
        dialogHolder[0]=dialog; dialog.show();
    }
    private void updateDates() {
        if(startDateButton==null)return;
        startDateButton.setText(startDate.toString()); endDateButton.setText(endDate.toString());
        startTimeButton.setText(timeLabel(startTime)); endTimeButton.setText(timeLabel(endTime));
        int visibility=allDay.isChecked()?View.GONE:View.VISIBLE;
        startTimeButton.setVisibility(visibility); endTimeButton.setVisibility(visibility);
    }
    private String timeLabel(LocalTime time) { return String.format(Locale.ROOT,"%02d:%02d",time.getHour(),time.getMinute()); }
    private void pickDate(boolean start) {
        LocalDate date=start?startDate:endDate;
        new DatePickerDialog(this,(picker,y,m,d)->{
            LocalDate chosen=LocalDate.of(y,m+1,d);
            if(start) {
                startDate=chosen;
                if(!endTimeEdited) setDefaultEndFromStart(); else if(endDate.isBefore(chosen))endDate=chosen;
            } else { endDate=chosen; endTimeEdited=true; }
            updateDates();
        },date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();
    }
    private void pickTime(boolean start) {
        LocalTime time=start?startTime:endTime;
        new TimePickerDialog(this,(view,hourOfDay,minute)->{
            LocalTime chosen=LocalTime.of(hourOfDay,minute);
            if(start) {
                startTime=chosen;
                if(!endTimeEdited)setDefaultEndFromStart();
            } else { endTime=chosen; endTimeEdited=true; }
            updateDates();
        },time.getHour(),time.getMinute(),android.text.format.DateFormat.is24HourFormat(this)).show();
    }
    private void setDefaultEndFromStart() {
        if(allDay!=null&&allDay.isChecked()) { endDate=startDate; return; }
        LocalDateTime end=EventTime.endAfterHour(startDate,startTime); endDate=end.toLocalDate(); endTime=end.toLocalTime();
    }
    private CalendarEvent previewEvent() {
        CalendarEvent preview=event.copy(); preview.id=0; preview.system=false; preview.calendarId=-1;
        preview.title=title==null?event.title:title.getText().toString().trim(); if(preview.title.isEmpty())preview.title=LocaleText.t(this,"角色提醒");
        preview.location=location==null?event.location:location.getText().toString(); preview.notes=notes==null?event.notes:notes.getText().toString(); preview.allDay=allDay==null?event.allDay:allDay.isChecked();
        // 预览要复用当前设置页正在展示的那张样图；正式提醒仍保留 RANDOM_ART，届时每次重新抽图。
        preview.reminderArt=reminderArt==CalendarEvent.RANDOM_ART?randomPreviewArt:reminderArt;
        return preview;
    }
    private void previewReminder() {
        rerollRandomPreview();
        if(!notificationsEnabled()) {
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                previewAfterNotificationPermission=true; requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_PREVIEW_NOTIFICATION);
            } else Ui.toast(this,LocaleText.t(this,"系统通知已关闭，请在设置中打开"));
            return;
        }
        ReminderNotifications.showPreview(this,previewEvent()); Ui.toast(this,LocaleText.t(this,"通知预览已发送"));
    }
    private boolean notificationsEnabled() {
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager==null||!manager.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=manager.getNotificationChannel(Reminders.CHANNEL);
            if(channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        }
        return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    private void save() {
        if(saving)return;
        CalendarEvent draft=event.copy(); draft.title=title.getText().toString().trim(); draft.location=location.getText().toString(); draft.notes=notes.getText().toString(); draft.allDay=allDay.isChecked();
        CalendarSource source=sources.get(calendar.getSelectedItemPosition()); draft.calendarId=source.id; draft.system=source.id!=-1; draft.color=source.color;
        draft.recurrence=Recurrence.RULES[repeat.getSelectedItemPosition()]; draft.reminderArt=reminderArt;
        if(draft.system) {
            draft.reminderMinutes=reminderValues.get(systemReminder.getSelectedItemPosition());
            draft.appReminderMinutes=reminderValues.get(reminder.getSelectedItemPosition());
            if(draft.appReminderMinutes<0)draft.reminderArt=-1;
        } else {
            draft.reminderMinutes=reminderValues.get(reminder.getSelectedItemPosition()); draft.appReminderMinutes=draft.reminderMinutes;
            if(draft.appReminderMinutes<0)draft.reminderArt=-1;
        }
        try {
            if(draft.allDay) { draft.start=CalendarMath.midnightUtc(startDate); draft.end=CalendarMath.midnightUtc(endDate.plusDays(1)); }
            else { ZoneId zone=ZoneId.of(draft.zone); draft.start=EventTime.toMillis(startDate,startTime,zone); draft.end=EventTime.toMillis(endDate,endTime,zone); }
            draft.validate();
        } catch(Exception e) { Ui.error(this,e); return; }
        saving=true; saveOperation=new SaveOperation(); final SaveOperation operation=saveOperation; saveButton.setEnabled(false); saveButton.setText(LocaleText.t(this,"正在保存…"));
        CalendarApp.IO.execute(()->{
            try { draft.id=repository.save(draft); operation.saved=draft; operation.complete=true; ((CalendarApp)getApplication()).changed(); runOnUiThread(()->{ if(isDestroyed())return; afterSave(draft); }); }
            catch(Exception e) { operation.failure=e; operation.complete=true; runOnUiThread(()->{ if(isDestroyed())return; saving=false; saveButton.setEnabled(true); saveButton.setText(LocaleText.t(this,"保存日程")); Ui.error(this,e); }); }
        });
    }
    private void saveProtectedAppReminder() {
        if(saving||!event.system)return;
        CalendarEvent draft=event.copy(); draft.appReminderMinutes=reminderValues.get(reminder.getSelectedItemPosition()); draft.reminderArt=reminderArt;
        if(draft.appReminderMinutes<0)draft.reminderArt=-1;
        saving=true; saveButton.setEnabled(false); saveButton.setText(LocaleText.t(this,"正在保存…"));
        CalendarApp.IO.execute(()->{
            try { repository.saveSystemReminder(draft); ((CalendarApp)getApplication()).changed(); runOnUiThread(()->{ if(isDestroyed())return; afterAppReminderSave(draft); }); }
            catch(Exception e) { runOnUiThread(()->{ if(isDestroyed())return; saving=false; saveButton.setEnabled(true); saveButton.setText(LocaleText.t(this,"保存应用提醒")); Ui.error(this,e); }); }
        });
    }
    private void afterAppReminderSave(CalendarEvent saved) {
        saving=false;
        if(saved.appReminderMinutes>=0&&!notificationsEnabled()) {
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                pendingSavedEvent=saved; requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_SAVE_NOTIFICATION); return;
            }
            Ui.toast(this,LocaleText.t(this,"应用提醒已保存，但系统通知已关闭，提醒不会弹出")); finish(); return;
        }
        Ui.toast(this,LocaleText.t(this,"应用提醒已保存")); finish();
    }
    private void afterSave(CalendarEvent saved) {
        boolean appReminder=saved.system?saved.appReminderMinutes>=0:saved.reminderMinutes>=0;
        if(appReminder&&!notificationsEnabled()) {
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                pendingSavedEvent=saved; requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_SAVE_NOTIFICATION); return;
            }
            Ui.toast(this,LocaleText.t(this,saved.system?"应用提醒已保存，但系统通知已关闭，提醒不会弹出":"日程已保存，但系统通知已关闭，提醒不会弹出")); finish(); return;
        }
        Ui.toast(this,saved.system?LocaleText.t(this,"已保存到系统日历"):LocaleText.t(this,"日程已保存")); finish();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQUEST_SAVE_NOTIFICATION) {
            CalendarEvent saved=pendingSavedEvent; pendingSavedEvent=null;
            if(saved!=null) {
                if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED) Ui.toast(this,LocaleText.t(this,"日程已保存，提醒通知已允许"));
                else Ui.toast(this,LocaleText.t(this,"日程已保存，但未允许通知，提醒不会弹出"));
                finish();
            }
        } else if(requestCode==REQUEST_PREVIEW_NOTIFICATION&&previewAfterNotificationPermission) {
            previewAfterNotificationPermission=false;
            if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED&&notificationsEnabled()) {
                ReminderNotifications.showPreview(this,previewEvent()); Ui.toast(this,LocaleText.t(this,"通知预览已发送"));
            } else Ui.toast(this,LocaleText.t(this,"系统通知已关闭，请在设置中打开"));
        }
    }
    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle(event.recurrence.isEmpty()?"删除这条日程？":"删除整个重复系列？").setMessage("「"+event.title+"」"+(event.system?"将从系统日历删除，账号同步后其他设备也会更新。":"将从本地日历删除。")+(!event.recurrence.isEmpty()?"过去和未来的重复日程都会删除。":""))
            .setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->{
                if(saving)return; saving=true;
                CalendarApp.IO.execute(()->{ try { repository.delete(event); ((CalendarApp)getApplication()).changed(); runOnUiThread(()->{ if(!isDestroyed())finish(); }); }
                    catch(Exception e) { runOnUiThread(()->{ if(isDestroyed())return; saving=false; Ui.error(this,e); }); } });
            }).show();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); if(title==null)return;
        out.putString("title",title.getText().toString()); out.putString("location",location.getText().toString()); out.putString("notes",notes.getText().toString());
        out.putString("startDate",startDate.toString()); out.putString("endDate",endDate.toString()); out.putString("startTime",startTime.toString()); out.putString("endTime",endTime.toString());
        out.putBoolean("allDay",allDay.isChecked()); out.putBoolean("endTimeEdited",endTimeEdited); out.putInt("repeat",repeat.getSelectedItemPosition()); out.putInt("reminder",reminder.getSelectedItemPosition()); if(systemReminder!=null)out.putInt("systemReminder",systemReminder.getSelectedItemPosition()); out.putInt("reminderArt",reminderArt); out.putLong("calendar",sources.get(calendar.getSelectedItemPosition()).id);
    }
    @Override public Object onRetainNonConfigurationInstance() { return saveOperation; }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if(reminderPreviewBitmap!=null&&!reminderPreviewBitmap.isRecycled())reminderPreviewBitmap.recycle(); super.onDestroy(); }
}
