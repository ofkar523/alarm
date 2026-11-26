package com.shiftalarm.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** 설치 진행 결과를 받음: 사용자 확인이 필요하면 안드로이드 설치 화면을 띄움 */
public class InstallReceiver extends BroadcastReceiver {
    @SuppressWarnings("deprecation")
    @Override
    public void onReceive(Context c, Intent in) {
        int status = in.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent confirm = in.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (Exception ignored) { }
                }
                Updater.notifyWeb("confirm", "");
                break;
            }
            case PackageInstaller.STATUS_SUCCESS:
                Updater.notifyWeb("done", "");
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                Updater.notifyWeb("error", "설치를 취소했어요");
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                Updater.notifyWeb("error", "서명 키가 달라 업데이트할 수 없어요. 앱을 지우고 새로 설치해야 해요");
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                Updater.notifyWeb("error", "저장 공간이 부족해요");
                break;
            default: {
                String msg = in.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Updater.notifyWeb("error", "설치하지 못했어요" + (msg == null ? "" : " (" + msg + ")"));
            }
        }
    }
}
