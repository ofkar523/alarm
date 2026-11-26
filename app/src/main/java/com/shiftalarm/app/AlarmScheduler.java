package com.shiftalarm.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 휴대폰 알람(AlarmManager.setAlarmClock)에 알람을 예약/취소합니다. */
public class AlarmScheduler {
    static final String ACTION_ALARM = "com.shiftalarm.app.ALARM";
    static final int SNOOZE_CODE = 100000;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("alarms", Context.MODE_PRIVATE);
    }

    static String hhmm(long ms) {
        return new SimpleDateFormat("a h:mm", Locale.KOREA).format(new Date(ms));
    }

    /** 기존 예약을 모두 지우고 목록대로 다시 예약 */
    public static synchronized void scheduleAll(Context c, String json) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = prefs(c);
        int prev = p.getInt("count", 0);
        for (int i = 0; i < prev; i++) {
            PendingIntent old = pending(c, i, null);
            am.cancel(old);
            old.cancel();
        }

        JSONArray in;
        try { in = new JSONArray(json == null ? "[]" : json); } catch (Exception e) { in = new JSONArray(); }
        JSONArray kept = new JSONArray();
        long now = System.currentTimeMillis();
        int n = 0;
        for (int i = 0; i < in.length(); i++) {
            JSONObject o = in.optJSONObject(i);
            if (o == null) continue;
            long at = o.optLong("at", 0);
            if (at <= now) continue;
            set(c, am, at, n, o);
            kept.put(o);
            n++;
        }
        p.edit().putInt("count", n).putString("list", kept.toString()).apply();
    }

    /** 재부팅·앱 업데이트 후 저장해 둔 목록으로 다시 예약 */
    public static void rescheduleFromStore(Context c) {
        scheduleAll(c, prefs(c).getString("list", "[]"));
    }

    /** 5분 후 다시 울림 */
    public static void snooze(Context c, String label, String sound, int minutes) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long at = System.currentTimeMillis() + minutes * 60_000L;
        JSONObject o = new JSONObject();
        try {
            o.put("time", hhmm(at));
            o.put("label", label);
            o.put("sound", sound);
        } catch (Exception ignored) { }
        set(c, am, at, SNOOZE_CODE, o);
    }

    private static PendingIntent pending(Context c, int code, JSONObject o) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        if (o != null) {
            i.putExtra("time", o.optString("time", ""));
            i.putExtra("label", o.optString("label", ""));
            i.putExtra("sound", o.optString("sound", "beep"));
        }
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void set(Context c, AlarmManager am, long at, int code, JSONObject o) {
        PendingIntent op = pending(c, code, o);
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op);
            } else {
                // 알람시계용 예약: 절전 모드에서도 정확한 시각에 울리고, 상태바에 알람 아이콘이 표시됨
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), op);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op);
        }
    }
}
