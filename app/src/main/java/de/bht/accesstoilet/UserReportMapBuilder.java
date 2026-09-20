package de.bht.accesstoilet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure aggregation for one filtered map halo per reported facility. */
public final class UserReportMapBuilder {
    private UserReportMapBuilder() {
    }

    public static List<Marker> build(List<UserReport> reports,
            Map<String, Toilet> toiletsByFacilityId, ToiletFilterState filterState) {
        Objects.requireNonNull(reports, "reports");
        Objects.requireNonNull(toiletsByFacilityId, "toiletsByFacilityId");
        Objects.requireNonNull(filterState, "filterState");
        LinkedHashMap<String, MutableMarker> byFacility = new LinkedHashMap<>();
        for (UserReport report : reports) {
            Objects.requireNonNull(report, "report entry");
            Toilet toilet = toiletsByFacilityId.get(report.getFacilityId());
            if (toilet == null || !filterState.matches(toilet)) continue;
            MutableMarker marker = byFacility.get(report.getFacilityId());
            if (marker == null) {
                marker = new MutableMarker(report.getFacilityId(),
                        report.getLatitude(), report.getLongitude());
                byFacility.put(report.getFacilityId(), marker);
            }
            marker.count++;
        }
        ArrayList<Marker> markers = new ArrayList<>(byFacility.size());
        for (MutableMarker marker : byFacility.values()) {
            markers.add(new Marker(marker.facilityId, marker.count,
                    marker.latitude, marker.longitude));
        }
        return List.copyOf(markers);
    }

    private static final class MutableMarker {
        private final String facilityId;
        private final double latitude;
        private final double longitude;
        private int count;

        private MutableMarker(String facilityId, double latitude, double longitude) {
            this.facilityId = facilityId;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final class Marker {
        private final String facilityId;
        private final int reportCount;
        private final double latitude;
        private final double longitude;

        private Marker(String facilityId, int reportCount,
                double latitude, double longitude) {
            this.facilityId = facilityId;
            this.reportCount = reportCount;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getFacilityId() { return facilityId; }
        public int getReportCount() { return reportCount; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}
