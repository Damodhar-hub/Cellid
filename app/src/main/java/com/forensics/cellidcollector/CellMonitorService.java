package com.forensics.cellidcollector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class CellMonitorService extends Service implements LocationListener {
    private static final String TAG = "CellMonitorService";
    private static final String CHANNEL_ID = "CellMonitorChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 10000;
    private static final long CELL_SCAN_INTERVAL = 5000;
    
    private final IBinder binder = new LocalBinder();
    private TelephonyManager telephonyManager;
    private LocationManager locationManager;
    private DatabaseHelper dbHelper;
    private Handler scanHandler;
    private Runnable scanRunnable;
    
    private Location currentLocation;
    private int totalCellsDetected = 0;
    
    public class LocalBinder extends Binder {
        CellMonitorService getService() {
            return CellMonitorService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        dbHelper = new DatabaseHelper(this);
        scanHandler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        startLocationUpdates();
        setupCellScanning();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        
        startCellMonitoring();
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        stopCellMonitoring();
        stopLocationUpdates();
        
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Cell ID Monitoring",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background cell tower monitoring for forensic analysis");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cell ID Forensics")
            .setContentText("Monitoring cell towers - " + totalCellsDetected + " detected")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void updateNotification() {
        Notification notification = createNotification();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification);
    }

    private void startLocationUpdates() {
        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 
                    LOCATION_UPDATE_INTERVAL, 
                    10, 
                    this
                );
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 
                    LOCATION_UPDATE_INTERVAL, 
                    10, 
                    this
                );
                
                Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                
                if (lastGps != null && (lastNetwork == null || lastGps.getTime() > lastNetwork.getTime())) {
                    currentLocation = lastGps;
                } else if (lastNetwork != null) {
                    currentLocation = lastNetwork;
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception in location updates", e);
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception stopping location updates", e);
            }
        }
    }

    private void setupCellScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                scanCellTowers();
                scanHandler.postDelayed(this, CELL_SCAN_INTERVAL);
            }
        };
    }

    private void startCellMonitoring() {
        Log.d(TAG, "Starting cell monitoring");
        
        scanHandler.post(scanRunnable);
        
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting cell monitoring", e);
        }
    }

    private void stopCellMonitoring() {
        Log.d(TAG, "Stopping cell monitoring");
        
        scanHandler.removeCallbacks(scanRunnable);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    private void scanCellTowers() {
        try {
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            
            if (cellInfoList != null) {
                for (CellInfo cellInfo : cellInfoList) {
                    processCellInfo(cellInfo);
                }
                
                Intent updateIntent = new Intent("CELL_DATA_UPDATE");
                sendBroadcast(updateIntent);
                
                updateNotification();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception scanning cell towers", e);
        }
    }

    private void processCellInfo(CellInfo cellInfo) {
        CellData cellData = new CellData();
        cellData.timestamp = System.currentTimeMillis();
        cellData.isRegistered = cellInfo.isRegistered();
        
        if (currentLocation != null) {
            cellData.latitude = currentLocation.getLatitude();
            cellData.longitude = currentLocation.getLongitude();
            cellData.accuracy = currentLocation.getAccuracy();
        }

        if (cellInfo instanceof CellInfoLte) {
            processCellInfoLte((CellInfoLte) cellInfo, cellData);
        } else if (cellInfo instanceof CellInfoGsm) {
            processCellInfoGsm((CellInfoGsm) cellInfo, cellData);
        } else if (cellInfo instanceof CellInfoWcdma) {
            processCellInfoWcdma((CellInfoWcdma) cellInfo, cellData);
        } else if (cellInfo instanceof CellInfoNr) {
            processCellInfoNr((CellInfoNr) cellInfo, cellData);
        }

        ProviderHelper.enrichCellDataWithProvider(cellData);
        
        String provider = ProviderHelper.getProviderName(cellData.mcc, cellData.mnc);
        Log.d(TAG, "Detected " + provider + " tower: " + cellData.technology + " - " + cellData.cellId + 
              " (Registered: " + cellData.isRegistered + ")");

        long id = dbHelper.insertCellData(cellData);
        if (id > 0) {
            totalCellsDetected++;
            Log.d(TAG, "Saved " + provider + " cell data: " + cellData.technology + " - " + cellData.cellId);
        }
    }

    private void processCellInfoLte(CellInfoLte lteInfo, CellData cellData) {
        CellIdentityLte identity = lteInfo.getCellIdentity();
        CellSignalStrengthLte signalStrength = lteInfo.getCellSignalStrength();
        
        cellData.technology = "LTE";
        cellData.cellId = String.valueOf(identity.getCi());
        cellData.lac = String.valueOf(identity.getTac());
        cellData.mcc = String.valueOf(identity.getMccString());
        cellData.mnc = String.valueOf(identity.getMncString());
        cellData.signalStrength = signalStrength.getDbm();
        cellData.pci = identity.getPci();
        cellData.earfcn = identity.getEarfcn();
        
        cellData.additionalInfo = String.format(
            "eNodeB:%d,Sector:%d,RSRP:%d,RSRQ:%d,RSSNR:%d,CQI:%d,TimingAdvance:%d",
            identity.getCi() >> 8,
            identity.getCi() & 0xFF,
            signalStrength.getRsrp(),
            signalStrength.getRsrq(),
            signalStrength.getRssnr(),
            signalStrength.getCqi(),
            signalStrength.getTimingAdvance()
        );
    }

    private void processCellInfoGsm(CellInfoGsm gsmInfo, CellData cellData) {
        CellIdentityGsm identity = gsmInfo.getCellIdentity();
        CellSignalStrengthGsm signalStrength = gsmInfo.getCellSignalStrength();
        
        cellData.technology = "GSM";
        cellData.cellId = String.valueOf(identity.getCid());
        cellData.lac = String.valueOf(identity.getLac());
        cellData.mcc = String.valueOf(identity.getMccString());
        cellData.mnc = String.valueOf(identity.getMncString());
        cellData.signalStrength = signalStrength.getDbm();
        cellData.arfcn = identity.getArfcn();
        cellData.bsic = identity.getBsic();
        
        cellData.additionalInfo = String.format(
            "BSIC:%d,TimingAdvance:%d,BitErrorRate:%d",
            identity.getBsic(),
            signalStrength.getTimingAdvance(),
            signalStrength.getBitErrorRate()
        );
    }

    private void processCellInfoWcdma(CellInfoWcdma wcdmaInfo, CellData cellData) {
        CellIdentityWcdma identity = wcdmaInfo.getCellIdentity();
        CellSignalStrengthWcdma signalStrength = wcdmaInfo.getCellSignalStrength();
        
        cellData.technology = "WCDMA";
        cellData.cellId = String.valueOf(identity.getCid());
        cellData.lac = String.valueOf(identity.getLac());
        cellData.mcc = String.valueOf(identity.getMccString());
        cellData.mnc = String.valueOf(identity.getMncString());
        cellData.signalStrength = signalStrength.getDbm();
        cellData.psc = identity.getPsc();
        cellData.uarfcn = identity.getUarfcn();
        
        cellData.additionalInfo = String.format(
            "PSC:%d,CPICH_RSCP:%d,CPICH_EcNo:%d",
            identity.getPsc(),
            signalStrength.getDbm(),
            signalStrength.getEcNo()
        );
    }

    private void processCellInfoNr(CellInfoNr nrInfo, CellData cellData) {
        CellIdentityNr identity = (CellIdentityNr) nrInfo.getCellIdentity();
        CellSignalStrengthNr signalStrength = (CellSignalStrengthNr) nrInfo.getCellSignalStrength();
        
        cellData.technology = "NR";
        cellData.cellId = String.valueOf(identity.getNci());
        cellData.lac = String.valueOf(identity.getTac());
        cellData.mcc = String.valueOf(identity.getMccString());
        cellData.mnc = String.valueOf(identity.getMncString());
        cellData.signalStrength = signalStrength.getDbm();
        cellData.pci = identity.getPci();
        cellData.nrarfcn = identity.getNrarfcn();
        
        cellData.additionalInfo = String.format(
            "gNodeB:%d,SS-RSRP:%d,SS-RSRQ:%d,SS-SINR:%d",
            identity.getNci() >> 12,
            signalStrength.getSsRsrp(),
            signalStrength.getSsRsrq(),
            signalStrength.getSsSinr()
        );
    }

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCellLocationChanged(android.telephony.CellLocation location) {
            Log.d(TAG, "Cell location changed");
            scanCellTowers();
        }
    };

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        Log.d(TAG, "Location updated: " + location.getLatitude() + ", " + location.getLongitude());
    }

    @Override
    public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Location provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Location provider disabled: " + provider);
    }

    public int getTotalCellsDetected() {
        return totalCellsDetected;
    }
}
