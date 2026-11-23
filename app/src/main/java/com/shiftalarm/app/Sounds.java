package com.shiftalarm.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

/** 알람 소리 목록과 재생 도우미 */
public class Sounds {
    static int res(String key) {
        if (key == null) return R.raw.snd_beep;
        switch (key) {
            case "digital": return R.raw.snd_digital;
            case "chime":   return R.raw.snd_chime;
            case "bell":    return R.raw.snd_bell;
            case "melody":  return R.raw.snd_melody;
            case "rooster": return R.raw.snd_rooster;
            case "siren":   return R.raw.snd_siren;
            case "alert":   return R.raw.snd_alert;
            default:        return R.raw.snd_beep;
        }
    }

    static AudioAttributes alarmAttrs() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    /** 알람용 플레이어 생성 (실패하면 null) */
    static MediaPlayer create(Context c, String key, boolean loop) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(alarmAttrs());
            if ("system".equals(key)) {
                Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                mp.setDataSource(c, u);
            } else {
                AssetFileDescriptor afd = c.getResources().openRawResourceFd(res(key));
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }
            mp.setLooping(loop);
            mp.prepare();
            return mp;
        } catch (Exception e) {
            mp.release();
            if (!"beep".equals(key)) return create(c, "beep", loop);
            return null;
        }
    }

    private static MediaPlayer previewPlayer;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    static void preview(Context c, String key) {
        stopPreview();
        previewPlayer = create(c, key, false);
        if (previewPlayer == null) return;
        final MediaPlayer mine = previewPlayer;
        mine.setOnCompletionListener(mp -> { if (previewPlayer == mine) stopPreview(); });
        mine.start();
        // 휴대폰 기본 알람음처럼 긴 소리는 4초만 들려줌
        handler.postDelayed(() -> { if (previewPlayer == mine) stopPreview(); }, 4000);
    }

    static void stopPreview() {
        if (previewPlayer != null) {
            try { previewPlayer.stop(); } catch (Exception ignored) { }
            previewPlayer.release();
            previewPlayer = null;
        }
    }
}
