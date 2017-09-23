package com.wang.appupgrade;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by Administrator on 2017/9/23 0023.
 */
public class UpgradeService extends Service {


    private boolean bUpgradeState = false;
    private String mUpgradeAddress;

    private int mDownloadProgress = 0;
    private String mSDCardPath;
    private File mInstallPath;
    private NotificationManager mNotificationManger;
    private Notification mNotification;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    IUpgrade.Stub iBinder = new IUpgrade.Stub() {
        @Override
        public boolean getUpgradeState() throws RemoteException {
            return bUpgradeState;
        }

        @Override
        public int getUpgradeProgress() throws RemoteException {
            return mDownloadProgress;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mSDCardPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        mNotificationManger = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mUpgradeAddress = intent.getStringExtra(ConstUtils.APK_URL);
        String fileName = mUpgradeAddress.substring(mUpgradeAddress.lastIndexOf("/") + 1);
        mInstallPath = new File(mSDCardPath, fileName);

        Log.e("tag", "=========mInstallPath=" + mInstallPath.getAbsolutePath());
        mDownloadProgress = 0;
        createDownloadNotity();

        new Thread(new Runnable() {
            @Override
            public void run() {
                downloadAPK();
            }
        }).start();

        return super.onStartCommand(intent, flags, startId);
    }

    private void createDownloadNotity() {
        Notification.Builder builder = new Notification.Builder(this)
                .setWhen(System.currentTimeMillis()) //通知什么时候触发
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("开始下载");
        mNotification = builder.build();
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.download_notification_layout);
        mNotification.contentView = remoteViews;
        remoteViews.setTextViewText(R.id.downloadContent, "0%");
        remoteViews.setProgressBar(R.id.downloadProgress, 100, 0, false);
        mNotificationManger.notify(ConstUtils.NOTITY_ID, mNotification);
    }


    private void downloadAPK() {

        try {
            bUpgradeState = true;

            Log.e("tag", "===========mUpgradeAddress===" + mUpgradeAddress);

            URL url = new URL(mUpgradeAddress);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

            if (200 != httpURLConnection.getResponseCode()) {
                mHandler.sendEmptyMessage(ConstUtils.NETWORK_FAIL);
                return;
            }

            int fileSize = httpURLConnection.getContentLength();

            InputStream inputStream = httpURLConnection.getInputStream();
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(mInstallPath));

            byte[] buffer = new byte[1024 * 50];
            int len;
            double readFileLen = 0;
            int lastProgress = 0;

            while (-1 != (len = inputStream.read(buffer))) {
                bufferedOutputStream.write(buffer, 0, len);
                bufferedOutputStream.flush();
                readFileLen += len;
                mDownloadProgress = (int) (readFileLen / fileSize * 100);

                Log.e("tag", "=========progress=" + mDownloadProgress);

                if (mDownloadProgress > lastProgress) {
                    Message msg = new Message();
                    msg.what = ConstUtils.PROGRESS;
                    msg.arg1 = mDownloadProgress;
                    mHandler.sendMessage(msg);
                    lastProgress = mDownloadProgress;
                }

            }
            bufferedOutputStream.close();
            inputStream.close();

            mHandler.sendEmptyMessage(ConstUtils.INSTALL_APK);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == ConstUtils.NETWORK_FAIL) {
                Toast.makeText(UpgradeService.this, "网络连接失败!", Toast.LENGTH_SHORT).show();
            } else if (msg.what == ConstUtils.PROGRESS) {
                int progress = msg.arg1;
                mNotification.contentView.setProgressBar(R.id.downloadProgress, 100, progress, false);
                mNotification.contentView.setTextViewText(R.id.downloadContent, progress + "%");
                mNotificationManger.notify(ConstUtils.NOTITY_ID, mNotification);
            } else if (msg.what == ConstUtils.INSTALL_APK) {
                installApk();
                stopSelf();
                mNotificationManger.cancel(ConstUtils.NOTITY_ID);
            }
        }
    };

    private void installApk() {
        if (!mInstallPath.exists()) {
            Log.e("tag", "======安装包路径不存在====");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.e("tag", "==========path=" + mInstallPath.getAbsolutePath());
        i.setDataAndType(Uri.parse("file://" + mInstallPath.getAbsolutePath()), "application/vnd.android.package-archive");
        startActivity(i);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

    }
}
