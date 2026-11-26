package com.shiftalarm.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/** index.html 에서 window.NativeAlarm 으로 호출하는 함수들 */
public class AlarmBridge {
    private final Activity act;
    private final android.webkit.WebView web;

    AlarmBridge(Activity act, android.webkit.WebView web) { this.act = act; this.web = web; }

    /** 새 버전 APK를 내려받아 설치 화면 띄우기 */
    @JavascriptInterface
    public void update(String apkUrl) {
        if (apkUrl == null || !apkUrl.startsWith("https://")) return;
        Updater.start(act, web, apkUrl);
    }

    /** 앞으로 울릴 알람 목록(JSON 배열)을 받아 휴대폰 알람에 예약 */
    @JavascriptInterface
    public void schedule(String json) {
        AlarmScheduler.scheduleAll(act.getApplicationContext(), json);
    }

    /** 알람 테스트: 지금 바로 알람 화면을 띄우고 울림 */
    @JavascriptInterface
    public void test(String sound, String label) {
        Intent i = new Intent(act, AlarmService.class);
        i.setAction(AlarmService.ACTION_RING);
        i.putExtra("time", AlarmScheduler.hhmm(System.currentTimeMillis()));
        i.putExtra("label", label);
        i.putExtra("sound", sound);
        try { act.startForegroundService(i); } catch (Exception ignored) { }
    }

    /** 소리 미리 듣기(한 번) */
    @JavascriptInterface
    public void preview(String sound) {
        act.runOnUiThread(() -> Sounds.preview(act, sound));
    }

    /** 앱 버전 (예: 1.0.12) */
    @JavascriptInterface
    public String appVersion() {
        try {
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** 권한 상태: {notif, exact, fullscreen, battery, overlay} */
    @JavascriptInterface
    public String permStatus() {
        // 하나를 확인하다 오류가 나도 나머지는 제대로 알려 주도록 따로따로 확인
        JSONObject o = new JSONObject();
        NotificationManager nm = (NotificationManager) act.getSystemService(Context.NOTIFICATION_SERVICE);
        AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
        PowerManager pm = (PowerManager) act.getSystemService(Context.POWER_SERVICE);
        try { o.put("notif", nm.areNotificationsEnabled()); } catch (Exception e) { putTrue(o, "notif"); }
        try { o.put("exact", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()); } catch (Exception e) { putTrue(o, "exact"); }
        try { o.put("fullscreen", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()); } catch (Exception e) { putTrue(o, "fullscreen"); }
        try { o.put("battery", pm.isIgnoringBatteryOptimizations(act.getPackageName())); } catch (Exception e) { putTrue(o, "battery"); }
        try { o.put("overlay", Settings.canDrawOverlays(act)); } catch (Exception e) { putTrue(o, "overlay"); }
        return o.toString();
    }

    private static void putTrue(JSONObject o, String k) { try { o.put(k, true); } catch (Exception ignored) { } }

    /** 해당 권한의 설정 화면 열기 */
    @JavascriptInterface
    public void openPerm(String key) {
        act.runOnUiThread(() -> {
            Uri pkg = Uri.parse("package:" + act.getPackageName());
            Intent i = null;
            switch (key) {
                case "notif":
                    i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, act.getPackageName());
                    break;
                case "exact":
                    if (Build.VERSION.SDK_INT >= 31) i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg);
                    break;
                case "fullscreen":
                    if (Build.VERSION.SDK_INT >= 34) i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg);
                    break;
                case "overlay":
                    i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg);
                    break;
                case "battery":
                    i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg);
                    break;
            }
            if (i == null) i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg);
            try {
                act.startActivity(i);
            } catch (Exception e) {
                try { act.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); } catch (Exception ignored) { }
            }
        });
    }
}
