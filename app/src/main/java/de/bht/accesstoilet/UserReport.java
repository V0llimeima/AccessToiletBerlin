package de.bht.accesstoilet;

import java.util.Objects;

/** One immutable local report linked to a static toilet by facility_id. */
public final class UserReport {
    public static final int MAX_NOTE_LENGTH = 500;

    private final long id;
    private final String facilityId;
    private final UserReportCategory category;
    private final String note;
    private final double latitude;
    private final double longitude;
    private final long createdAtEpochMs;

    public UserReport(long id, String facilityId, UserReportCategory category, String note,
            double latitude, double longitude, long createdAtEpochMs) {
        if (id <= 0) throw new IllegalArgumentException("Report id must be positive");
        validateInput(facilityId, category, note, latitude, longitude, createdAtEpochMs, true);
        this.id = id;
        this.facilityId = facilityId.trim();
        this.category = Objects.requireNonNull(category, "category");
        this.note = normalizeNote(note);
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    static void validateNew(String facilityId, UserReportCategory category, String note,
            double latitude, double longitude, long createdAtEpochMs) {
        validateInput(facilityId, category, note, latitude, longitude, createdAtEpochMs, false);
    }

    private static void validateInput(String facilityId, UserReportCategory category, String note,
            double latitude, double longitude, long createdAtEpochMs, boolean allowUnknown) {
        if (facilityId == null || facilityId.trim().isEmpty()) {
            throw new IllegalArgumentException("facility_id must not be empty");
        }
        Objects.requireNonNull(category, "category");
        if (!allowUnknown && !category.isSelectable()) {
            throw new IllegalArgumentException("New reports need a known category");
        }
        normalizeNote(note);
        GeoDistance.requireCoordinate(latitude, longitude);
        if (createdAtEpochMs <= 0) {
            throw new IllegalArgumentException("Report timestamp must be positive");
        }
    }

    public static String normalizeNote(String note) {
        if (note == null) return null;
        String normalized = note.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Report note exceeds 500 characters");
        }
        return normalized;
    }

    public long getId() { return id; }
    public String getFacilityId() { return facilityId; }
    public UserReportCategory getCategory() { return category; }
    public String getNote() { return note; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
}
