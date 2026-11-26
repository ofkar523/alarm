package com.shiftalarm.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 앱 안 업데이트: 새 APK를 내려받아 안드로이드 설치(업데이트) 화면을 띄웁니다.
 * 같은 서명 키로 만든 더 높은 버전이라 '업데이트'로 설치되고, 설정은 그대로 남습니다.
 * 진행 상황은 화면(index.html)의 window.onUpdateState(state, value) 로 알립니다.
 *   state: download(진행률) / needPermission / confirm / done / error(메시지)
 */
public class Updater {
    static final String ACTION_STATUS = "com.shiftalarm.app.INSTALL_STATUS";
    private static volatile boolean running = false;
    private static volatile WebView web;

    static void start(Activity act, WebView w, String apkUrl) {
        web = w;
        // 이 앱이 다른 앱(새 버전)을 설치해도 되는지: 처음 한 번은 사용자가 허용해야 함
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            notifyWeb("needPermission", "");
            act.runOnUiThread(() -> {
                try {
                    act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + act.getPackageName())));
                } catch (Exception ignored) { }
            });
            return;
        }
        if (running) return;
        running = true;

        new Thread(() -> {
            PackageInstaller.Session session = null;
            try {
                PackageInstaller installer = act.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(act.getPackageName());
                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);

                HttpURLConnection conn = open(apkUrl);
                long total = conn.getContentLengthLong();
                try (InputStream in = conn.getInputStream();
                     OutputStream out = session.openWrite("update.apk", 0, total > 0 ? total : -1)) {
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    int lastPct = -1, n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != lastPct) { lastPct = pct; notifyWeb("download", String.valueOf(pct)); }
                        }
                    }
                    session.fsync(out);
                } finally {
                    conn.disconnect();
                }

                Intent status = new Intent(act, InstallReceiver.class).setAction(ACTION_STATUS);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pi = PendingIntent.getBroadcast(act, sessionId, status, flags);
                session.commit(pi.getIntentSender());   // → InstallReceiver 가 설치 확인 화면을 띄움
                session.close();
                session = null;
            } catch (Exception e) {
                if (session != null) { try { session.abandon(); } catch (Exception ignored) { } }
                notifyWeb("error", "내려받지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요");
            } finally {
                running = false;
            }
        }).start();
    }

    private static HttpURLConnection open(String url) throws Exception {
        // GitHub 릴리스 파일은 다른 주소로 넘겨 주므로(리다이렉트) 직접 따라감
        for (int i = 0; i < 5; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "ShiftAlarm-Updater");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("no location");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code != 200) { c.disconnect(); throw new Exception("HTTP " + code); }
            return c;
        }
        throw new Exception("too many redirects");
    }

    /** 화면에 진행 상황 알리기: window.onUpdateState(state, value) */
    static void notifyWeb(String state, String value) {
        WebView w = web;
        if (w == null) return;
        String code = "window.onUpdateState && window.onUpdateState("
                + JSONObject.quote(state) + "," + JSONObject.quote(value == null ? "" : value) + ")";
        w.post(() -> w.evaluateJavascript(code, null));
    }
}
