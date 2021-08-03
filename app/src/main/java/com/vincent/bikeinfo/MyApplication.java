package com.vincent.bikeinfo;

import android.app.Application;
import android.os.Handler;
import android.util.Log;

import com.inuker.bluetooth.library.BluetoothContext;

public class MyApplication extends Application {

    private static MyApplication instance;

    public static Application getInstance() {
        return instance;
    }

    private String TAG = getClass().getSimpleName();

    @Override
    public void onCreate() {
        Log.d(TAG,"onCreate");
        super.onCreate();
        instance = this;
        BluetoothContext.set(this);

    }
}
