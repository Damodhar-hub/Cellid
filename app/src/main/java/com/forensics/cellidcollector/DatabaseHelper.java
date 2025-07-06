package com.forensics.cellidcollector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "cellid_forensics.db";
    private static final int DATABASE_VERSION = 2;
    
    private static final String TABLE_CELL_DATA = "cell_data";
    
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_TECHNOLOGY = "technology";
    private static final String COLUMN_CELL_ID = "cell_id";
    private static final String COLUMN_LAC = "lac_tac";
    private static final String COLUMN_MCC = "mcc";
    private static final String COLUMN_MNC = "mnc";
    private static final String COLUMN_SIGNAL_STRENGTH = "signal_strength";
    private static final String COLUMN_IS_REGISTERED = "is_registered";
    private static final String COLUMN_LATITUDE = "latitude";
    private static final String COLUMN_LONGITUDE = "longitude";
    private static final String COLUMN_ACCURACY = "accuracy";
    private static final String COLUMN_PCI = "pci";
    private static final String COLUMN_PSC = "psc";
    private static final String COLUMN_BSIC = "bsic";
    private static final String COLUMN_EARFCN = "earfcn";
    private static final String COLUMN_UARFCN = "uarfcn";
    private static final String COLUMN_ARFCN = "arfcn";
    private static final String COLUMN_NRARFCN = "nrarfcn";
    private static final String COLUMN_ADDITIONAL_INFO = "additional_info";
    
    private static final String CREATE_TABLE_CELL_DATA = 
        "CREATE TABLE " + TABLE_CELL_DATA + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
        COLUMN_TECHNOLOGY + " TEXT NOT NULL, " +
        COLUMN_CELL_ID + " TEXT NOT NULL, " +
        COLUMN_LAC + " TEXT, " +
        COLUMN_MCC + " TEXT, " +
        COLUMN_MNC + " TEXT, " +
        COLUMN_SIGNAL_STRENGTH + " INTEGER, " +
        COLUMN_IS_REGISTERED + " INTEGER DEFAULT 0, " +
        COLUMN_LATITUDE + " REAL DEFAULT 0, " +
        COLUMN_LONGITUDE + " REAL DEFAULT 0, " +
        COLUMN_ACCURACY + " REAL DEFAULT 0, " +
        COLUMN_PCI + " INTEGER DEFAULT -1, " +
        COLUMN_PSC + " INTEGER DEFAULT -1, " +
        COLUMN_BSIC + " INTEGER DEFAULT -1, " +
        COLUMN_EARFCN + " INTEGER DEFAULT -1, " +
        COLUMN_UARFCN + " INTEGER DEFAULT -1, " +
        COLUMN_ARFCN + " INTEGER DEFAULT -1, " +
        COLUMN_NRARFCN + " INTEGER DEFAULT -1, " +
        COLUMN_ADDITIONAL_INFO + " TEXT" +
        ");";
    
    private static final String CREATE_INDEX_TIMESTAMP = 
        "CREATE INDEX idx_timestamp ON " + TABLE_CELL_DATA + "(" + COLUMN_TIMESTAMP + ");";
    
    private static final String CREATE_INDEX_CELL_ID = 
        "CREATE INDEX idx_cell_id ON " + TABLE_CELL_DATA + "(" + COLUMN_CELL_ID + ");";
    
    private static final String CREATE_INDEX_TECHNOLOGY = 
        "CREATE INDEX idx_technology ON " + TABLE_CELL_DATA + "(" + COLUMN_TECHNOLOGY + ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating database tables");
        
        db.execSQL(CREATE_TABLE_CELL_DATA);
        db.execSQL(CREATE_INDEX_TIMESTAMP);
        db.execSQL(CREATE_INDEX_CELL_ID);
        db.execSQL(CREATE_INDEX_TECHNOLOGY);
        
        Log.d(TAG, "Database tables created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_CELL_DATA + " ADD COLUMN " + COLUMN_PCI + " INTEGER DEFAULT -1");
                db.execSQL("ALTER TABLE " + TABLE_CELL_DATA + " ADD COLUMN " + COLUMN_PSC + " INTEGER DEFAULT -1");
                db.execSQL("ALTER TABLE " + TABLE_CELL_DATA + " ADD COLUMN " + COLUMN_BSIC + " INTEGER DEFAULT -1");
                db.execSQL("ALTER TABLE " + TABLE_CELL_DATA + " ADD COLUMN " + COLUMN_EARFCN + " INTEGER DEFAULT -1");
                db.execSQL("ALTER TABLE " + TABLE_CELL_DATA + " ADD COLUMN " + COLUMN_UARFCN + " INTEGER DEFAULT -1");
                db.execSQL("ALTER TABLE " + TABLE_CELL_DATA + " ADD COLUMN " + COLUMN_ARFCN + " INTEGER DEFAULT -1");
                db.execSQL("ALTER TABLE " + TABLE_CELL_DATA + " ADD COLUMN " + COLUMN_NRARFCN + " INTEGER DEFAULT -1");
                Log.d(TAG, "Database upgrade completed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading database", e);
            }
        }
    }

    public long insertCellData(CellData cellData) {
        SQLiteDatabase db = this.getWritableDatabase();
        long id = -1;
        
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_TIMESTAMP, cellData.timestamp);
            values.put(COLUMN_TECHNOLOGY, cellData.technology);
            values.put(COLUMN_CELL_ID, cellData.cellId);
            values.put(COLUMN_LAC, cellData.lac);
            values.put(COLUMN_MCC, cellData.mcc);
            values.put(COLUMN_MNC, cellData.mnc);
            values.put(COLUMN_SIGNAL_STRENGTH, cellData.signalStrength);
            values.put(COLUMN_IS_REGISTERED, cellData.isRegistered ? 1 : 0);
            values.put(COLUMN_LATITUDE, cellData.latitude);
            values.put(COLUMN_LONGITUDE, cellData.longitude);
            values.put(COLUMN_ACCURACY, cellData.accuracy);
            values.put(COLUMN_PCI, cellData.pci);
            values.put(COLUMN_PSC, cellData.psc);
            values.put(COLUMN_BSIC, cellData.bsic);
            values.put(COLUMN_EARFCN, cellData.earfcn);
            values.put(COLUMN_UARFCN, cellData.uarfcn);
            values.put(COLUMN_ARFCN, cellData.arfcn);
            values.put(COLUMN_NRARFCN, cellData.nrarfcn);
            values.put(COLUMN_ADDITIONAL_INFO, cellData.additionalInfo);
            
            id = db.insert(TABLE_CELL_DATA, null, values);
            
            if (id > 0) {
                cellData.id = id;
                Log.v(TAG, "Inserted cell data with ID: " + id);
            } else {
                Log.w(TAG, "Failed to insert cell data");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error inserting cell data", e);
        }
        
        return id;
    }

    public List<CellData> getAllCellData() {
        List<CellData> cellDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_CELL_DATA + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        
        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                do {
                    CellData cellData = cursorToCellData(cursor);
                    cellDataList.add(cellData);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all cell data", e);
        }
        
        return cellDataList;
    }

    public List<CellData> getRecentCellData(int limit) {
        List<CellData> cellDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_CELL_DATA + 
                      " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT " + limit;
        
        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                do {
                    CellData cellData = cursorToCellData(cursor);
                    cellDataList.add(cellData);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting recent cell data", e);
        }
        
        return cellDataList;
    }

    public int getTotalCellCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_CELL_DATA;
        
        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting total cell count", e);
        }
        
        return 0;
    }

    public void clearAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        
        try {
            db.delete(TABLE_CELL_DATA, null, null);
            Log.d(TAG, "All cell data cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing all data", e);
        }
    }

    private CellData cursorToCellData(Cursor cursor) {
        CellData cellData = new CellData();
        
        cellData.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
        cellData.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
        cellData.technology = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TECHNOLOGY));
        cellData.cellId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CELL_ID));
        cellData.lac = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAC));
        cellData.mcc = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MCC));
        cellData.mnc = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MNC));
        cellData.signalStrength = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SIGNAL_STRENGTH));
        cellData.isRegistered = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_REGISTERED)) == 1;
        cellData.latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE));
        cellData.longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE));
        cellData.accuracy = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ACCURACY));
        cellData.pci = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PCI));
        cellData.psc = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PSC));
        cellData.bsic = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BSIC));
        cellData.earfcn = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_EARFCN));
        cellData.uarfcn = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_UARFCN));
        cellData.arfcn = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ARFCN));
        cellData.nrarfcn = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_NRARFCN));
        cellData.additionalInfo = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDITIONAL_INFO));
        
        return cellData;
    }
}
