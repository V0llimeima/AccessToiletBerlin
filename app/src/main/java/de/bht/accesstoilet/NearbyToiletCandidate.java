package de.bht.accesstoilet;

import java.util.Objects;

/** Immutable reference to one loaded toilet and its unrounded air-line distance. */
public final class NearbyToiletCandidate {
    private final Toilet toilet;
    private final double distanceMeters;

    public NearbyToiletCandidate(Toilet toilet, double distanceMeters) {
        this.toilet = Objects.requireNonNull(toilet, "toilet");
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0.0) {
            throw new IllegalArgumentException("Distance must be finite and non-negative");
        }
        this.distanceMeters = distanceMeters;
    }

    public Toilet getToilet() { return toilet; }
    public double getDistanceMeters() { return distanceMeters; }
}
