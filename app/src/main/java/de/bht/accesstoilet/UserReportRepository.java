package de.bht.accesstoilet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** Encapsulates all SQLite access for local user reports. */
public final class UserReportRepository implements AutoCloseable {
    private static final String[] COLUMNS = {
            "_id", "facility_id", "category", "note", "latitude", "longitude",
            "created_at_epoch_ms"
    };

    private final UserReportDatabaseHelper helper;

    public UserReportRepository(Context context) {
        helper = new UserReportDatabaseHelper(context.getApplicationContext());
    }

    public List<UserReport> loadAll() {
        ArrayList<UserReport> reports = new ArrayList<>();
        SQLiteDatabase database = helper.getReadableDatabase();
        try (Cursor cursor = database.query(UserReportDatabaseHelper.TABLE, COLUMNS,
                null, null, null, null, "created_at_epoch_ms DESC, _id DESC")) {
            int idColumn = cursor.getColumnIndexOrThrow("_id");
            int facilityColumn = cursor.getColumnIndexOrThrow("facility_id");
            int categoryColumn = cursor.getColumnIndexOrThrow("category");
            int noteColumn = cursor.getColumnIndexOrThrow("note");
            int latitudeColumn = cursor.getColumnIndexOrThrow("latitude");
            int longitudeColumn = cursor.getColumnIndexOrThrow("longitude");
            int createdColumn = cursor.getColumnIndexOrThrow("created_at_epoch_ms");
            while (cursor.moveToNext()) {
                reports.add(new UserReport(
                        cursor.getLong(idColumn),
                        cursor.getString(facilityColumn),
                        UserReportCategory.fromCode(cursor.getString(categoryColumn)),
                        cursor.isNull(noteColumn) ? null : cursor.getString(noteColumn),
                        cursor.getDouble(latitudeColumn),
                        cursor.getDouble(longitudeColumn),
                        cursor.getLong(createdColumn)));
            }
        }
        return List.copyOf(reports);
    }

    public UserReport insert(String facilityId, UserReportCategory category, String note,
            double latitude, double longitude, long createdAtEpochMs) {
        UserReport.validateNew(facilityId, category, note,
                latitude, longitude, createdAtEpochMs);
        String normalizedFacilityId = facilityId.trim();
        String normalizedNote = UserReport.normalizeNote(note);
        ContentValues values = new ContentValues();
        values.put("facility_id", normalizedFacilityId);
        values.put("category", category.getCode());
        if (normalizedNote == null) values.putNull("note"); else values.put("note", normalizedNote);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("created_at_epoch_ms", createdAtEpochMs);
        long id = helper.getWritableDatabase().insertOrThrow(
                UserReportDatabaseHelper.TABLE, null, values);
        if (id <= 0) throw new IllegalStateException("SQLite returned an invalid report id");
        return new UserReport(id, normalizedFacilityId, category, normalizedNote,
                latitude, longitude, createdAtEpochMs);
    }

    public boolean deleteById(long id) {
        if (id <= 0) throw new IllegalArgumentException("Report id must be positive");
        int deleted = helper.getWritableDatabase().delete(
                UserReportDatabaseHelper.TABLE, "_id = ?", new String[]{Long.toString(id)});
        return deleted == 1;
    }

    @Override
    public void close() {
        helper.close();
    }
}
