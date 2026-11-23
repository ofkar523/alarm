package com.shiftalarm.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 예약한 시각이 되면 호출 → 알람 서비스(소리·진동·알람 화면) 시작 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Intent s = new Intent(c, AlarmService.class);
        s.setAction(AlarmService.ACTION_RING);
        s.putExtra("time", in.getStringExtra("time"));
        s.putExtra("label", in.getStringExtra("label"));
        s.putExtra("sound", in.getStringExtra("sound"));
        try {
            c.startForegroundService(s);
        } catch (Exception e) {
            // 드물게 백그라운드 시작이 막히면, 소리 나는 알림으로라도 알림
            AlarmService.postFallback(c, in.getStringExtra("time"), in.getStringExtra("label"));
        }
    }
}
