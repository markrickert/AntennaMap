package com.platypii.basemap;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ASRDatabase {
    private Context context;

    private ASRDatabaseHelper helper;
    private SQLiteDatabase database;

    public ASRDatabase(Context context) {
        this.context = context;  // TODO: Leaks context
        helper = new ASRDatabaseHelper(context);
        database = helper.getReadableDatabase();
        if(isEmpty()) {
            loadFromFile();
        }
    }

    private void loadFromFile() {
        Log.w("ASRDatabse", "Loading into database");
        SQLiteDatabase writableDatabase = helper.getWritableDatabase();
        final ASRFile asrFile = new ASRFile(context);
        for(ASRRecord record : asrFile) {
            // Add to database
            writableDatabase.execSQL("INSERT INTO asr VALUES ("+record.latitude+", "+record.longitude+", "+record.height+");");
        }
        writableDatabase.close();
        Log.w("ASRDatabse", "Loaded into database");
    }

    private boolean isEmpty() {
        Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM asr", null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        return count == 0;
    }

    /**
     * Search for the N tallest towers in view
     */
    public List<ASRRecord> query(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude, int limit) {
        String params[] = {""+minLatitude, ""+maxLatitude, ""+minLongitude, ""+maxLongitude, ""+limit};
        SQLiteDatabase readableDatabase = helper.getReadableDatabase();
        Cursor cursor = readableDatabase.rawQuery(
                "SELECT * FROM asr" +
                        " WHERE ? < latitude AND latitude < ?" +
                        " AND ? < longitude AND longitude < ?" +
                        " ORDER BY height DESC LIMIT ?", params);
        ArrayList<ASRRecord> records = new ArrayList<>();
        while(cursor.moveToNext()) {
            final double latitude = cursor.getDouble(0);
            final double longitude = cursor.getDouble(1);
            final double height = cursor.getDouble(2);
            ASRRecord record = new ASRRecord(latitude, longitude, height);
            records.add(record);
        }
        readableDatabase.close();
        return records;
    }

}
