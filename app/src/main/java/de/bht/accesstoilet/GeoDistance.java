package de.bht.accesstoilet;

/** Pure WGS84 distance calculations. Parameters are always latitude, longitude. */
public final class GeoDistance {
    static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoDistance() {
    }

    /** Returns the Haversine distance in meters between two WGS84 positions. */
    public static double meters(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude) {
        requireCoordinate(startLatitude, startLongitude);
        requireCoordinate(endLatitude, endLongitude);

        double latitudeDelta = Math.toRadians(endLatitude - startLatitude);
        double longitudeDelta = Math.toRadians(endLongitude - startLongitude);
        double startLatitudeRadians = Math.toRadians(startLatitude);
        double endLatitudeRadians = Math.toRadians(endLatitude);
        double sinLatitude = Math.sin(latitudeDelta / 2.0);
        double sinLongitude = Math.sin(longitudeDelta / 2.0);
        double haversine = sinLatitude * sinLatitude
                + Math.cos(startLatitudeRadians) * Math.cos(endLatitudeRadians)
                * sinLongitude * sinLongitude;
        double centralAngle = 2.0 * Math.atan2(
                Math.sqrt(haversine), Math.sqrt(Math.max(0.0, 1.0 - haversine)));
        return EARTH_RADIUS_METERS * centralAngle;
    }

    public static void requireCoordinate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be finite and in [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be finite and in [-180, 180]");
        }
    }
}
