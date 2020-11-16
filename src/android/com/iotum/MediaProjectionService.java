package com.iotum;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class MediaProjectionService extends Service {
    private static final String TAG = CordovaAndroidScreenshare.class.getName();
    private String mNotificationTitle = "Sharing your screen";
    private String mNotificationText = "Everything on screen will be shared.";
    private int mNotificationId = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = getApplicationContext();
        if (intent.getAction().equals("start")) {
            mNotificationId += 1;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder builder = new Notification.Builder(context, "foreground.service.channel")
                        .setContentTitle(mNotificationTitle)
                        .setContentText(mNotificationText)
                        .setOngoing(true);

                Notification notification = builder.build();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(mNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                } else {
                    startForeground(mNotificationId, notification);
                }
            } else {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                        .setContentTitle(mNotificationTitle)
                        .setContentText(mNotificationText)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setOngoing(true);

                Notification notification = builder.build();

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