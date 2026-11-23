package com.shiftalarm.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** 알람이 울리는 동안 실행: 소리 반복 + 진동 + 알람 화면. '끄기'나 '5분 후'를 누를 때까지 계속 울립니다. */
public class AlarmService extends Service {
    static final String ACTION_RING = "com.shiftalarm.app.RING";
    static final String ACTION_STOP = "com.shiftalarm.app.STOP";
    static final String ACTION_SNOOZE = "com.shiftalarm.app.SNOOZE";
    static final String CH_RING = "alarm_ring";
    static final String CH_FALLBACK = "alarm_fallback";
    static final int NID = 1;

    static volatile boolean ringing = false;
    static volatile String curTime = "";
    static volatile String curLabel = "";
    static volatile String curSound = "beep";

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float volume = 1f;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopAll();
            return START_NOT_STICKY;
        }
        if (ACTION_SNOOZE.equals(action)) {
            AlarmScheduler.snooze(this, curLabel, curSound, 5);
            stopAll();
            return START_NOT_STICKY;
        }
        if (intent == null) {           // 시스템이 서비스를 재시작한 경우 등
            stopSelf();
            return START_NOT_STICKY;
        }

        String t = intent.getStringExtra("time");
        curTime = (t == null || t.isEmpty()) ? AlarmScheduler.hhmm(System.currentTimeMillis()) : t;
        String l = intent.getStringExtra("label");
        curLabel = l == null ? "" : l;
        String s = intent.getStringExtra("sound");
        curSound = (s == null || s.isEmpty()) ? "beep" : s;

        ensureChannels(this);
        Notification n = buildRingNotification(this, curTime, curLabel);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NID, n);
        }

        acquireWakeLock();
        startSound();
        startVibration();
        ringing = true;

        // 알람 화면 띄우기
        //  - 화면이 꺼져 있으면: 알림의 전체 화면 인텐트가 띄움 ('잠금 화면 알람' 권한)
        //  - '다른 앱 위에 표시' 권한이 있으면: 여기서 직접 띄움 (화면 꺼짐/잠금/다른 앱 사용 중 모두)
        openAlarmScreen();
        handler.postDelayed(this::openAlarmScreen, 700);   // 화면이 켜지는 중이라 첫 시도가 무시될 때 대비
        handler.postDelayed(this::wakeScreen, 300);
        AlarmActivity.refreshAll();
        return START_NOT_STICKY;
    }

    private void openAlarmScreen() {
        if (!ringing || AlarmActivity.isShowing()) return;
        try { startActivity(alarmActivityIntent(this)); } catch (Exception ignored) { }
    }

    /** 꺼진 화면을 켬 (알람 화면이 잠금 화면 위에 보이도록) */
    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm.isInteractive()) return;
            PowerManager.WakeLock w = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "ShiftAlarm:screen");
            w.acquire(10_000L);
        } catch (Exception ignored) { }
    }

    private void startSound() {
        stopSound();
        player = Sounds.create(this, curSound, true);
        if (player == null) return;
        if ("rooster".equals(curSound)) {          // 점점 커지는 소리
            volume = 0.15f;
            player.setVolume(volume, volume);
            handler.post(ramp);
        }
        player.start();
    }

    private final Runnable ramp = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            volume = Math.min(1f, volume + 0.03f);
            try { player.setVolume(volume, volume); } catch (Exception ignored) { }
            if (volume < 1f) handler.postDelayed(this, 1000);
        }
    };

    private void stopSound() {
        handler.removeCallbacks(ramp);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void startVibration() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 600, 500}, 0), Sounds.alarmAttrs());
        } catch (Exception ignored) { }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShiftAlarm:ring");
        wakeLock.acquire(60 * 60 * 1000L);
    }

    @SuppressWarnings("deprecation")
    private void stopAll() {
        ringing = false;
        handler.removeCallbacksAndMessages(null);
        stopSound();
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(true);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NID);
        AlarmActivity.closeAll();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        ringing = false;
        stopSound();
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    /* ---------- 알림 ---------- */

    static Intent alarmActivityIntent(Context c) {
        Intent i = new Intent(c, AlarmActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return i;
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CH_RING) == null) {
            NotificationChannel ch = new NotificationChannel(CH_RING, "알람", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("근무 알람이 울릴 때 표시됩니다");
            ch.setSound(null, null);            // 소리는 앱이 직접 재생
            ch.enableVibration(false);
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CH_FALLBACK) == null) {
            NotificationChannel ch = new NotificationChannel(CH_FALLBACK, "알람 (예비)", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), Sounds.alarmAttrs());
            ch.enableVibration(true);
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    static Notification buildRingNotification(Context c, String time, String label) {
        PendingIntent full = PendingIntent.getActivity(c, 1, alarmActivityIntent(c),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(c, 2,
                new Intent(c, AlarmService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snooze = PendingIntent.getService(c, 3,
                new Intent(c, AlarmService.class).setAction(ACTION_SNOOZE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(c, CH_RING)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("⏰ " + time + " 알람")
                .setContentText(label == null || label.isEmpty() ? "근무 알람" : label + " 근무")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(new Notification.Action.Builder((Icon) null, "끄기", stop).build())
                .addAction(new Notification.Action.Builder((Icon) null, "5분 후 다시", snooze).build())
                .build();
    }

    /** 서비스를 시작할 수 없을 때 쓰는 예비 알림 (휴대폰 기본 알람음으로 한 번 울림) */
    static void postFallback(Context c, String time, String label) {
        ensureChannels(c);
        PendingIntent open = PendingIntent.getActivity(c, 4, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH_FALLBACK)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("⏰ " + (time == null ? "" : time) + " 알람")
                .setContentText(label == null ? "근무 알람" : label + " 근무")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .build();
        n.flags |= Notification.FLAG_INSISTENT;   // 확인할 때까지 소리 반복
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(2, n);
    }
}
