package com.vincent.bikeinfo;

import android.util.Log;

import com.inuker.bluetooth.library.BluetoothClient;
import com.inuker.bluetooth.library.connect.options.BleConnectOptions;
import com.inuker.bluetooth.library.connect.response.BleConnectResponse;
import com.inuker.bluetooth.library.connect.response.BleNotifyResponse;
import com.inuker.bluetooth.library.model.BleGattCharacter;
import com.inuker.bluetooth.library.model.BleGattProfile;
import com.inuker.bluetooth.library.model.BleGattService;

import java.util.UUID;

import static com.inuker.bluetooth.library.Constants.REQUEST_SUCCESS;

public class CyclingMeasurementDevice implements BleConnectResponse {
    public final static int SPEED = 101;
    public final static int CADENCE = 102;
    public final static int POWER = 103;
    public static final UUID CSCServiceUUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    public static final UUID CSCMeasurementUUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb");
    public static final UUID PowerMeterServiceUUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
    public static final UUID PowerMeterMeasurementUUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb");
    private String address;
    private DataUpdateCallback l = null;
    private long lastRev = -1;
    private long lastEventTime = -1;
    private final String TAG = getClass().getSimpleName();
    private final float circumference = 2136;//700 28c;
    private int type=-1;


    public interface DataUpdateCallback {
        void onDataUpdate(int deviceType, String measurement);
    }


    @Override
    public void onResponse(int code, BleGattProfile data) {
        if (code == REQUEST_SUCCESS) {
            for(BleGattService s:data.getServices()) {
                for (BleGattCharacter c : s.getCharacters()) {
                    UUID service = s.getUUID();
                    UUID characters = c.getUuid();
                    if (isCSCDevice(service,characters)) {
                        Log.d(TAG, "found CSC device ,open notification");
                        ClientManager.getClient().notify(address, CSCServiceUUID, CSCMeasurementUUID, new CSCNotify());
                    }else if(isPowerMeter(service,characters)){
                        Log.d(TAG, "found power meter,open notification");
                        type = POWER;
                        ClientManager.getClient().notify(address, PowerMeterServiceUUID, PowerMeterMeasurementUUID, new PowerMeterNotify());
                    }else{
                        Log.d(TAG,"invalid device");
                    }
                }
            }
        }
    }

    private boolean isCSCDevice(UUID Services,UUID Characters){
        return(Services.equals(CSCServiceUUID) && Characters.equals(CSCMeasurementUUID));
    }

    private boolean isPowerMeter(UUID Services,UUID Characters){
        return(Services.equals(PowerMeterServiceUUID) && Characters.equals(PowerMeterMeasurementUUID));
    }

    private void callBack(int deviceType,String measurement){
        if(this.l!=null){
            this.l.onDataUpdate(deviceType,measurement);
        }
    }

    public CyclingMeasurementDevice(String address){
        this.address = address;
    }

    public void connect(DataUpdateCallback l){
        BleConnectOptions options = new BleConnectOptions.Builder()
                .setConnectRetry(3)
                .setConnectTimeout(20000)
                .setServiceDiscoverRetry(3)
                .setServiceDiscoverTimeout(10000)
                .build();
        BluetoothClient cli = ClientManager.getClient();
        this.l = l;
        cli.connect(this.address, options,this);
    }

    private  int unsignedBytesToInt(final byte b0, final byte b1) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8));
    }

    private  int unsignedByteToInt(final byte b) {
        return b & 0xFF;
    }


    private class CSCNotify implements BleNotifyResponse{

        @Override
        public void onNotify(UUID service, UUID character, byte[] value) {
            final int flags = value[0];
            boolean SpeedData = (flags&0x01)!=0;
            boolean CadenceData = (flags&0x02)!=0;
            int index = value.length;
            long rev = unsignedByteToInt(value[1]);
            long eventTime = unsignedBytesToInt(value[index-2],value[index-1]);
            long revDiff = rev-lastRev;
            float timeDiff = ((float)eventTime-(float)lastEventTime)/1024;
            Log.d(TAG,String.format("rev:%s,eventTime:%s",rev,eventTime));
            Log.d(TAG,String.format("revDiff:%s,eventTimeDiff:%s",revDiff,timeDiff));
            if(SpeedData){
                type = CyclingMeasurementDevice.SPEED;
            }else if(CadenceData){
                type = CyclingMeasurementDevice.CADENCE;
            }else{
                callBack(-1,"invalid device");
            }
            if((lastEventTime == -1&&lastRev == -1)||timeDiff==0){
                callBack(type,"--");
                lastRev = rev;
                lastEventTime = eventTime;
                return;
            }
            float measurementOffset = 0;
            float measurement = revDiff/timeDiff;
            String unit = "--";
            if(SpeedData){
                measurementOffset = circumference / 1000.0f;// to meter
                measurementOffset = measurementOffset*60*60/1000;//km/h
                unit = "%.2fKM";
            }

            if(CadenceData){
                measurementOffset = 60;
                unit = "%.2fRPM";
            }

            callBack(type,String.format(unit,measurement*measurementOffset));
            lastRev = rev;
            lastEventTime = eventTime;
        }

        @Override
        public void onResponse(int code) {

        }
    }


    private class PowerMeterNotify implements BleNotifyResponse{

        @Override
        public void onNotify(UUID service, UUID character, byte[] value) {
                int power = unsignedBytesToInt(value[2],value[3]);
                callBack(type,String.valueOf(power)+"w");
        }

        @Override
        public void onResponse(int code) {

        }
    }
}
