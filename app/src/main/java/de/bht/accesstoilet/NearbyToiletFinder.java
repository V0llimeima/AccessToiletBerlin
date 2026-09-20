package de.bht.accesstoilet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure local candidate search over the already loaded immutable toilet objects. */
public final class NearbyToiletFinder {
    private NearbyToiletFinder() {
    }

    public static List<NearbyToiletCandidate> findNearest(
            List<Toilet> toilets,
            ToiletFilterState filterState,
            double userLatitude,
            double userLongitude,
            int limit) {
        Objects.requireNonNull(toilets, "toilets");
        Objects.requireNonNull(filterState, "filterState");
        GeoDistance.requireCoordinate(userLatitude, userLongitude);
        if (limit <= 0) throw new IllegalArgumentException("Limit must be positive");

        ArrayList<NearbyToiletCandidate> matches = new ArrayList<>();
        for (Toilet toilet : toilets) {
            Objects.requireNonNull(toilet, "toilet entry");
            String facilityId = toilet.getFacilityId();
            if (facilityId == null || facilityId.isBlank()) {
                throw new IllegalArgumentException("Every toilet needs a facility_id");
            }
            GeoDistance.requireCoordinate(toilet.getLatitude(), toilet.getLongitude());
            if (filterState.matches(toilet)) {
                matches.add(new NearbyToiletCandidate(toilet, GeoDistance.meters(
                        userLatitude, userLongitude,
                        toilet.getLatitude(), toilet.getLongitude())));
            }
        }
        matches.sort(Comparator.comparingDouble(NearbyToiletCandidate::getDistanceMeters)
                .thenComparing(candidate -> candidate.getToilet().getFacilityId()));
        return List.copyOf(matches.subList(0, Math.min(limit, matches.size())));
    }
}
