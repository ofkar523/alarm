package com.shiftalarm.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 잠금 화면 위에 뜨는 알람 화면 */
public class AlarmActivity extends Activity {
    private static WeakReference<AlarmActivity> current = new WeakReference<>(null);
    private TextView timeView, labelView;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = dp(32);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0F172A"), Color.parseColor("#1E3A8A")}));

        TextView date = text(new SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(new Date()), 16, false);
        date.setAlpha(0.7f);
        timeView = text("", 76, true);
        labelView = text("", 22, true);
        labelView.setPadding(0, dp(4), 0, dp(48));

        Button stop = button("끄기", Color.WHITE, Color.parseColor("#0F172A"));
        stop.setOnClickListener(v -> send(AlarmService.ACTION_STOP));
        Button snooze = button("5분 후 다시 알림", Color.parseColor("#33FFFFFF"), Color.WHITE);
        snooze.setOnClickListener(v -> send(AlarmService.ACTION_SNOOZE));

        root.addView(date);
        root.addView(timeView);
        root.addView(labelView);
        root.addView(stop, buttonParams());
        root.addView(snooze, buttonParams());
        setContentView(root);

        current = new WeakReference<>(this);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AlarmService.ringing) finish();   // 이미 꺼진 알람이면 닫기
        else refresh();
    }

    private void refresh() {
        timeView.setText(AlarmService.curTime);
        String l = AlarmService.curLabel;
        labelView.setText(l == null || l.isEmpty() ? "근무 알람" : l);
    }

    private void send(String action) {
        Intent i = new Intent(this, AlarmService.class).setAction(action);
        try { startService(i); } catch (Exception ignored) { }
        finish();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // 뒤로가기로는 알람을 끌 수 없음 (끄기 / 5분 후 버튼 사용)
    }

    static void closeAll() {
        AlarmActivity a = current.get();
        if (a != null) a.runOnUiThread(a::finish);
    }

    static void refreshAll() {
        AlarmActivity a = current.get();
        if (a != null) a.runOnUiThread(a::refresh);
    }

    /* ---------- 화면 구성 도우미 ---------- */
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(18));
        b.setBackground(d);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(300), dp(64));
        p.topMargin = dp(12);
        return p;
    }
}
