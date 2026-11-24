package com.zadalqolob.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "zad_daily_reminders";

    public static void schedule(Context context, String id, String title, String message, int hour, int minute) {
        if (id == null || id.trim().isEmpty() || hour < 0 || hour > 23 || minute < 0 || minute > 59) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.zadalqolob.REMINDER." + id);
        intent.putExtra("title", title == null ? "تذكير من زاد القلوب" : title);
        intent.putExtra("message", message == null ? "حان وقت وردك اليومي" : message);
        intent.putExtra("reminder_id", id);
        int requestCode = id.hashCode() & 0x7fffffff;
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(Calendar.getInstance())) next.add(Calendar.DAY_OF_YEAR, 1);
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pending);
    }

    public static void cancel(Context context, String id) {
        if (id == null) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.zadalqolob.REMINDER." + id);
        PendingIntent pending = PendingIntent.getBroadcast(context, id.hashCode() & 0x7fffffff,
                intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "تذكيرات زاد القلوب",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("تذكيرات الأذكار والختمة والورد اليومي");
            manager.createNotificationChannel(channel);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent contentIntent = launch == null ? null : PendingIntent.getActivity(context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title == null ? "زاد القلوب" : title)
                .setContentText(message == null ? "حان وقت وردك اليومي" : message)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        String id = intent.getStringExtra("reminder_id");
        manager.notify((id == null ? "zad" : id).hashCode(), builder.build());
    }
}
