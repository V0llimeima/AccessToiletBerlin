package de.bht.accesstoilet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Fully validated, immutable route geometry and summary values. */
public final class RouteResult {
    public static final class Coordinate {
        private final double longitude;
        private final double latitude;

        public Coordinate(double longitude, double latitude) {
            GeoDistance.requireCoordinate(latitude, longitude);
            this.longitude = longitude;
            this.latitude = latitude;
        }

        public double getLongitude() { return longitude; }
        public double getLatitude() { return latitude; }
    }

    private final List<Coordinate> coordinates;
    private final double distanceMeters;
    private final double durationSeconds;

    public RouteResult(List<Coordinate> coordinates, double distanceMeters, double durationSeconds) {
        Objects.requireNonNull(coordinates, "coordinates");
        if (coordinates.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two coordinates");
        }
        ArrayList<Coordinate> copy = new ArrayList<>(coordinates.size());
        for (Coordinate coordinate : coordinates) {
            copy.add(Objects.requireNonNull(coordinate, "coordinate"));
        }
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0.0) {
            throw new IllegalArgumentException("Distance must be finite and non-negative");
        }
        if (!Double.isFinite(durationSeconds) || durationSeconds < 0.0) {
            throw new IllegalArgumentException("Duration must be finite and non-negative");
        }
        this.coordinates = Collections.unmodifiableList(copy);
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
    }

    public List<Coordinate> getCoordinates() { return coordinates; }
    public double getDistanceMeters() { return distanceMeters; }
    public double getDurationSeconds() { return durationSeconds; }
}
