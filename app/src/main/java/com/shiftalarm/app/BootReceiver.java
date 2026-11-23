package com.shiftalarm.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 휴대폰을 껐다 켜거나 앱이 업데이트되면 알람을 다시 예약 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        AlarmScheduler.rescheduleFromStore(c);
    }
}
