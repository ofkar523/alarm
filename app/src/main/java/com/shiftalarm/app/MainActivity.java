package com.shiftalarm.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** 앱 화면: assets/index.html 을 WebView로 띄우고, 알람 기능은 AlarmBridge로 연결합니다. */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage(설정 저장)
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);                    // 시스템 글자 크기에 레이아웃이 깨지지 않게
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new AlarmBridge(this, web), "NativeAlarm");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 설정 탭의 권한 표시를 새로 그리게 함
        if (web != null) web.evaluateJavascript("document.dispatchEvent(new Event('visibilitychange'))", null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // 창 닫기 → 캘린더 탭 → 앱 나가기 순서로 처리 (index.html 의 handleBack)
        web.evaluateJavascript("(window.handleBack && window.handleBack()) ? '1' : '0'", value -> {
            if (value == null || !value.contains("1")) moveTaskToBack(true);
        });
    }
}
