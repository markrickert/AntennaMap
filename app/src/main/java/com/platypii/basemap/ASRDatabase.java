package com.platypii.basemap;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class ASRDatabase {

    private static ASRDatabaseHelper helper;
    private static SQLiteDatabase database;
    private static boolean started = false;
    private static boolean loading = false;
    private static boolean loaded = false;

    public static synchronized void start(Context appContext) {
        // Start database
        if (!started) {
            helper = new ASRDatabaseHelper(appContext);
            database = helper.getReadableDatabase();
            Log.w("ASRDatabase", "Database started");
            started = true;
        } else {
            Log.e("ASRDatabase", "Already started");
            if (database == null) {
                database = helper.getReadableDatabase();
            }
        }
    }

    public static synchronized void loadIfEmpty(Context appContext) {
        // Load data, if necessary
        if(!started) {
            Log.e("ASRDatabase", "Attempted to load, but database is not started");
        } else if(loading) {
            Log.e("ASRDatabase", "Attempted to load, but already loading");
        } else {
            if (ASRDatabase.isEmpty()) {
                Log.w("ASRDatabase", "Database is empty, loading from file");
                loading = true;
                ASRFile.loadAsync(appContext);
            } else {
                loaded = true;
                ASR.databaseLoaded();
            }
        }
    }

    public static void loadDataAsync(final Iterator<ASRRecord> asrIterator) {
        if (started && loading) {
            new LoadDataTask(asrIterator).execute();
        } else {
            Log.e("ASRDatabase", "Unexpected load data callback");
        }
    }

    private static class LoadDataTask extends AsyncTask<Void, Integer, Void> {
        private final Iterator<ASRRecord> asrIterator;
        private int totalSize = -1;

        LoadDataTask(Iterator<ASRRecord> asrIterator) {
            this.asrIterator = asrIterator;
        }

        @Override
        protected void onPreExecute() {
            MapsActivity.startProgress("Loading data...");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.w("ASRDatabase", "Loading database");
            // Get row count (TODO: Slow!)
            totalSize = ASRFile.rowCount();
            int count = 0;
            publishProgress(0);

            // Write to database
            database.close();
            database = null;
            final SQLiteDatabase writableDatabase = helper.getWritableDatabase();
            writableDatabase.execSQL("BEGIN TRANSACTION");
            while (asrIterator.hasNext()) {
                final ASRRecord record = asrIterator.next();
                if (record != null) {
                    // Add to database
                    writableDatabase.execSQL("INSERT OR IGNORE INTO asr VALUES (" + record.id + "," + record.latitude + "," + record.longitude + "," + record.height + ")");
                    if (count % 1000 == 0) {
                        Log.i("ASRDatabase", "Populating database row " + count);
                    }
                    if (count % 100 == 0) {
                        publishProgress(count);
                    }
                    count++;
                }
            }
            writableDatabase.execSQL("COMMIT");
            writableDatabase.close();
            database = helper.getReadableDatabase();
            loading = false;
            loaded = true;
            Log.w("ASRDatabase", "Database loaded from file");
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            MapsActivity.updateProgress("Loading data...", progress[0], totalSize);
        }

        @Override
        protected void onPostExecute(Void result) {
            MapsActivity.dismissProgress();
            ASR.databaseLoaded();
        }
    }

    private static boolean isEmpty() {
        final Cursor cursor = database.rawQuery("SELECT COUNT(id) FROM asr", null);
        cursor.moveToFirst();
        final int count = cursor.getInt(0);
        return count == 0;
    }

    /**
     * Search for the N tallest towers in view
     */
    public static List<ASRRecord> query(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude, int limit) {
        if(loaded) {
            final String params[] = {"" + minLatitude, "" + maxLatitude, "" + minLongitude, "" + maxLongitude, "" + limit};
            final String longitudeQuery = (minLongitude <= maxLongitude) ?
                    " AND ? < longitude AND longitude < ?" :
                    " AND ? < longitude OR longitude < ?";
            final Cursor cursor = database.rawQuery(
                    "SELECT * FROM asr" +
                            " WHERE ? < latitude AND latitude < ?" +
                            longitudeQuery +
                            " ORDER BY height DESC LIMIT ?", params);
            final ArrayList<ASRRecord> records = new ArrayList<>();
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final double latitude = cursor.getDouble(1);
                final double longitude = cursor.getDouble(2);
                final double height = cursor.getDouble(3);
                final ASRRecord record = new ASRRecord(id, latitude, longitude, height);
                records.add(record);
            }
            return records;
        } else if(loading) {
            Log.w("ASRDatabase", "Query attempted while still loading");
            return null;
        } else {
            Log.e("ASRDatabase", "Query attempted on uninitialized database");
            return null;
        }
    }

}
