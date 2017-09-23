package com.wang.appupgrade;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private IUpgrade iUpgrade;
    private TextView upgradeProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        upgradeProgress=(TextView)findViewById(R.id.upgradeProgress);

        if (checkSDCardPermission()) {
            Intent intent = new Intent(this, UpgradeService.class);
            bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
        }else{
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},ConstUtils.PERMISSION_SDCARD);
        }
    }

    private boolean checkSDCardPermission() {
        if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) &&
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==ConstUtils.PERMISSION_SDCARD){
            for(int g=0;g<grantResults.length;g++){
                if(grantResults[g]==PackageManager.PERMISSION_GRANTED){
                    Intent intent = new Intent(this, UpgradeService.class);
                    bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
                }
            }
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            try {
                iUpgrade = IUpgrade.Stub.asInterface(service);
                if (!iUpgrade.getUpgradeState()) {
                    int progress = iUpgrade.getUpgradeProgress();
                    Log.e("tag", "=====progress=" + progress);

                    Intent intent = new Intent(MainActivity.this, UpgradeService.class);
                    intent.putExtra(ConstUtils.APK_URL, ConstUtils.UPGRADE_ADDRESS);
                    startService(intent);
                }
                mHandler.sendEmptyMessage(ConstUtils.PROGRESS);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
             if(msg.what==ConstUtils.PROGRESS){
                 try {
                     upgradeProgress.setText(iUpgrade.getUpgradeProgress() + "%");
                     if(iUpgrade.getUpgradeProgress()>=100){
                         return ;
                     }
                     mHandler.sendEmptyMessageDelayed(ConstUtils.PROGRESS, 150);
                 } catch (RemoteException e) {
                     e.printStackTrace();
                 }
             }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mHandler.removeCallbacksAndMessages(null);
    }
}
