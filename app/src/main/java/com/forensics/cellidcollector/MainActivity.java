package com.forensics.cellidcollector;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CellIDForensics";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private TextView statusText;
    private TextView countText;
    private TextView currentCellText;
    private ListView cellListView;
    private Button startButton;
    private Button stopButton;
    private Button exportButton;
    private Button clearButton;
    
    private TelephonyManager telephonyManager;
    private CellMonitorService cellService;
    private boolean serviceBound = false;
    private CellDataAdapter cellAdapter;
    private List<CellData> cellDataList;
    private DatabaseHelper dbHelper;
    
    private String[] requiredPermissions = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        initializeData();
        checkPermissions();
        
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        dbHelper = new DatabaseHelper(this);
        
        setupClickListeners();
        updateUI();
        
        // Register broadcast receiver for cell updates
        IntentFilter filter = new IntentFilter("CELL_DATA_UPDATE");
        registerReceiver(cellUpdateReceiver, filter);
    }

    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        countText = findViewById(R.id.countText);
        currentCellText = findViewById(R.id.currentCellText);
        cellListView = findViewById(R.id.cellListView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        exportButton = findViewById(R.id.exportButton);
        clearButton = findViewById(R.id.clearButton);
    }

    private void initializeData() {
        cellDataList = new ArrayList<>();
        cellAdapter = new CellDataAdapter(this, cellDataList);
        cellListView.setAdapter(cellAdapter);
    }

    private void setupClickListeners() {
        startButton.setOnClickListener(v -> startMonitoring());
        stopButton.setOnClickListener(v -> stopMonitoring());
        exportButton.setOnClickListener(v -> exportData());
        clearButton.setOnClickListener(v -> clearData());
    }

    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void startMonitoring() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "All permissions required for operation", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent serviceIntent = new Intent(this, CellMonitorService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        statusText.setText("Status: MONITORING ACTIVE");
        statusText.setTextColor(getColor(android.R.color.holo_green_dark));
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        
        // Get initial cell info
        getCurrentCellInfo();
    }

    private void stopMonitoring() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        
        Intent serviceIntent = new Intent(this, CellMonitorService.class);
        stopService(serviceIntent);
        
        statusText.setText("Status: MONITORING STOPPED");
        statusText.setTextColor(getColor(android.R.color.holo_red_dark));
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void getCurrentCellInfo() {
        if (!hasRequiredPermissions()) return;
        
        try {
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if (cellInfoList != null && !cellInfoList.isEmpty()) {
                CellInfo primaryCell = cellInfoList.get(0);
                updateCurrentCellDisplay(primaryCell);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting cell info", e);
        }
    }

    private void updateCurrentCellDisplay(CellInfo cellInfo) {
        StringBuilder cellText = new StringBuilder();
        cellText.append("Current Cell:\n");
        
        if (cellInfo instanceof CellInfoLte) {
            CellInfoLte lteInfo = (CellInfoLte) cellInfo;
            CellIdentityLte identity = lteInfo.getCellIdentity();
            cellText.append("Type: LTE (4G)\n");
            cellText.append("Cell ID: ").append(identity.getCi()).append("\n");
            cellText.append("eNodeB: ").append(identity.getCi() >> 8).append("\n");
            cellText.append("TAC: ").append(identity.getTac()).append("\n");
            cellText.append("EARFCN: ").append(identity.getEarfcn()).append("\n");
        } else if (cellInfo instanceof CellInfoGsm) {
            CellInfoGsm gsmInfo = (CellInfoGsm) cellInfo;
            CellIdentityGsm identity = gsmInfo.getCellIdentity();
            cellText.append("Type: GSM (2G)\n");
            cellText.append("Cell ID: ").append(identity.getCid()).append("\n");
            cellText.append("LAC: ").append(identity.getLac()).append("\n");
            cellText.append("ARFCN: ").append(identity.getArfcn()).append("\n");
        } else if (cellInfo instanceof CellInfoWcdma) {
            CellInfoWcdma wcdmaInfo = (CellInfoWcdma) cellInfo;
            CellIdentityWcdma identity = wcdmaInfo.getCellIdentity();
            cellText.append("Type: WCDMA (3G)\n");
            cellText.append("Cell ID: ").append(identity.getCid()).append("\n");
            cellText.append("LAC: ").append(identity.getLac()).append("\n");
            cellText.append("UARFCN: ").append(identity.getUarfcn()).append("\n");
        } else if (cellInfo instanceof CellInfoNr) {
            CellInfoNr nrInfo = (CellInfoNr) cellInfo;
            CellIdentityNr identity = (CellIdentityNr) nrInfo.getCellIdentity();
            cellText.append("Type: NR (5G)\n");
            cellText.append("Cell ID: ").append(identity.getNci()).append("\n");
            cellText.append("TAC: ").append(identity.getTac()).append("\n");
            cellText.append("NRARFCN: ").append(identity.getNrarfcn()).append("\n");
        }
        
        currentCellText.setText(cellText.toString());
    }

    private void exportData() {
        try {
            List<CellData> allData = dbHelper.getAllCellData();
            
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File exportFile = new File(downloadsDir, "CellID_Forensics_" + timestamp + ".csv");
            
            FileWriter writer = new FileWriter(exportFile);
            
            // Write CSV header
            writer.append("Timestamp,Provider,Technology,Cell_ID,LAC_TAC,Signal_Strength,MCC,MNC,Location,Registered,Additional_Info\n");
            
            // Write data
            for (CellData data : allData) {
                writer.append(data.toCsvString()).append("\n");
            }
            
            writer.close();
            
            Toast.makeText(this, "Data exported to: " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.i(TAG, "Data exported to: " + exportFile.getAbsolutePath());
            
        } catch (IOException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Export failed", e);
        }
    }

    private void clearData() {
        dbHelper.clearAllData();
        cellDataList.clear();
        cellAdapter.notifyDataSetChanged();
        updateUI();
        Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        int totalCount = dbHelper.getTotalCellCount();
        countText.setText("Total Cells Detected: " + totalCount);
        
        // Show provider statistics
        List<CellData> allData = dbHelper.getAllCellData();
        String providerSummary = ProviderHelper.generateProviderSummary(allData);
        Log.d(TAG, "Provider Summary:\n" + providerSummary);
        
        // Check if all major providers detected
        if (ProviderHelper.hasDetectedAllMajorProviders(allData)) {
            Log.i(TAG, "🎉 ALL MAJOR PROVIDERS DETECTED!");
        } else {
            List<String> missing = ProviderHelper.getMissingProviders(allData);
            Log.i(TAG, "Missing providers: " + missing.toString());
        }
        
        // Load recent data for display
        cellDataList.clear();
        cellDataList.addAll(dbHelper.getRecentCellData(50)); // Show last 50 entries
        cellAdapter.notifyDataSetChanged();
    }

    private boolean hasRequiredPermissions() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // Service connection for binding to CellMonitorService
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CellMonitorService.LocalBinder binder = (CellMonitorService.LocalBinder) service;
            cellService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    // Broadcast receiver for cell data updates
    private BroadcastReceiver cellUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUI();
            getCurrentCellInfo();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
        }
        unregisterReceiver(cellUpdateReceiver);
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(this, "All permissions are required for proper operation", Toast.LENGTH_LONG).show();
            }
        }
    }
}
