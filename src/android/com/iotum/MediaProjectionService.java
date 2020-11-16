package com.iotum;

import android.annotation.TargetApi;
import android.support.v4.app.NotificationCompat;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class MediaProjectionService extends Service {
    private static final String TAG = CordovaAndroidScreenshare.class.getName();
    private int mNotificationId = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = getApplicationContext();
        if (intent.getAction().equals("start")) {
            // Delete notification channel if it already exists
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.deleteNotificationChannel("foreground.service.channel");

            // Create notification channel
            NotificationChannel channel = new NotificationChannel("foreground.service.channel", "Background Services", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);

            mNotificationId += 1;
            Notification notification = new NotificationCompat.Builder(context, "foreground.service.channel")
                    .setSmallIcon(android.R.drawable.notification_icon)
                    .setContentTitle("Sharing your screen")
                    .setContentText("Everything on screen will be shared.")
                    .setOngoing(true)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(mNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(mNotificationId, notification);
            }
        } else {
            // Stop the service
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }
}