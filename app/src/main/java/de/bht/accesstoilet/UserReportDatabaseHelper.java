package de.bht.accesstoilet;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Owns only the versioned local report schema. */
final class UserReportDatabaseHelper extends SQLiteOpenHelper {
    static final String DATABASE_NAME = "access_toilet_user_reports.db";
    static final int DATABASE_VERSION = 1;
    static final String TABLE = "user_reports";

    UserReportDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE user_reports ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "facility_id TEXT NOT NULL,"
                + "category TEXT NOT NULL,"
                + "note TEXT,"
                + "latitude REAL NOT NULL,"
                + "longitude REAL NOT NULL,"
                + "created_at_epoch_ms INTEGER NOT NULL)");
        database.execSQL("CREATE INDEX idx_user_reports_facility_id "
                + "ON user_reports(facility_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException("No non-destructive user-report migration from "
                + oldVersion + " to " + newVersion);
    }
}
