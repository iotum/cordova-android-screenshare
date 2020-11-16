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
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class MediaProjectionService extends Service {
    private static final String TAG = CordovaAndroidScreenshare.class.getName();
    private int mNotificationId = 0;
    private NotificationChannel mChannel;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = getApplicationContext();
        if (intent.getAction().equals("start")) {
            if (!mChannel) {
                // Create notification channel
                mChannel = new NotificationChannel("foreground.service.channel", "Background Services", NotificationManager.IMPORTANCE_LOW);
                getSystemService(NotificationManager.class).createNotificationChannel(channel);
            }

            mNotificationId += 1;
            Notification notification = new NotificationCompat.Builder(context, "foreground.service.channel")
                    .setSmallIcon(getIconResId())
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

    private int getIconResId()
    {
        Resources res  = getResources();
        String pkgName = getPackageName();

        int resId = res.getIdentifier("icon", "mipmap", pkgName);
        if (resId == 0) {
            resId = res.getIdentifier("icon", "drawable", pkgName);
        }

        return resId;
    }
}