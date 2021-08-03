package com.vincent.bikeinfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.inuker.bluetooth.library.search.SearchRequest;
import com.inuker.bluetooth.library.search.SearchResult;
import com.inuker.bluetooth.library.search.response.SearchResponse;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements CyclingMeasurementDevice.DataUpdateCallback {

    int REQUEST_ENABLE_BT = 101;

    int LOCATION_REQUEST_CODE = 200;
    int REQUEST_ENABLE_LOCATION = 201;
    DeviceListAdapter mAdapter;
    List<SearchResult> mDevices;
    String TAG = "Bike";
    TextView speedDisplay,cadenceDisplay,powerMeterDisplay;
    Map<Integer, TextView> viewMap;
    Button btn;
    AlertDialog.Builder builder;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAndRequestPermission();
        checkLocationEnabled();
        initView();
        mAdapter = new DeviceListAdapter(this);
        mDevices = new ArrayList<>();
        builder = new AlertDialog.Builder(MainActivity.this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void initView(){
        speedDisplay = findViewById(R.id.speedDisplay);
        cadenceDisplay = findViewById(R.id.cadenceDisplay);
        powerMeterDisplay = findViewById(R.id.powerMeterDisplay);
        viewMap = new HashMap<>();
        viewMap.put(CyclingMeasurementDevice.CADENCE,cadenceDisplay);
        viewMap.put(CyclingMeasurementDevice.POWER,powerMeterDisplay);
        viewMap.put(CyclingMeasurementDevice.SPEED,speedDisplay);
        btn = findViewById(R.id.searchDevice);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanDevice();
            }
        });
    }

    private void scanDevice(){
        SearchRequest request = new SearchRequest.Builder()
                .searchBluetoothLeDevice(5000, 2).build();

        ClientManager.getClient().search(request, mSearchResponse);
    }


    private void navigateToBluetoothSetting(){
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    private void navigateToLocationSetting(){
        Intent callGPSSettingIntent = new Intent(
                android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivityForResult(callGPSSettingIntent, REQUEST_ENABLE_LOCATION);
    }

    private void checkLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this).setMessage("GPS Not Enable").setNeutralButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    navigateToLocationSetting();
                }
            }).show();
        }
    }

    private void checkAndRequestPermission(){
        Log.d(TAG,"check BT permission");
        String requiredPermission = Manifest.permission.ACCESS_FINE_LOCATION;
        int granted = ContextCompat.checkSelfPermission(getApplicationContext(),requiredPermission);
        if(granted != PackageManager.PERMISSION_GRANTED){
            Log.d(TAG,"permission not granted,request permission");
            requestPermissions(new String[]{requiredPermission}, LOCATION_REQUEST_CODE);
        }else{
            Log.d(TAG,"location permission is granted");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_LOCATION) {
            Log.d(TAG,"Location request code:"+resultCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == LOCATION_REQUEST_CODE &&grantResults.length > 0){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
                dialogBuilder.setMessage("request permission fail");
                dialogBuilder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
                AlertDialog dialog = dialogBuilder.create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
        }
    }

    private void updateText(final TextView targetView,final String msg){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                targetView.setText(msg);
            }
        });
    }

    private final SearchResponse mSearchResponse = new SearchResponse(){

        @Override
        public void onSearchStarted() {
            mDevices.clear();
            builder.setTitle("DeviceList");
            builder.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    ClientManager.getClient().stopSearch();
                }
            });
            builder.setAdapter(mAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    SearchResult item = (SearchResult)mAdapter.getItem(i);
                    Log.d(TAG,"click item:"+item.getName());
                    new CyclingMeasurementDevice(item.getAddress()).connect(MainActivity.this);
                    ClientManager.getClient().stopSearch();
                }
            });
            builder.create().show();
        }

        @Override
        public void onDeviceFounded(SearchResult device) {
            if (!mDevices.contains(device)) {
                mDevices.add(device);
                mAdapter.setDataList(mDevices);
            }
        }

        @Override
        public void onSearchStopped() {

        }

        @Override
        public void onSearchCanceled() {

        }
    };

    @Override
    public void onDataUpdate(int deviceType, String measurement) {
        TextView targetView = viewMap.get(deviceType);
        if(targetView!=null){
            updateText(targetView,measurement);
        }
    }
}