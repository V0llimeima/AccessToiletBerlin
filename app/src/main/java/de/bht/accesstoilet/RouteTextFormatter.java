package de.bht.accesstoilet;

import android.content.Context;

import java.util.Locale;

/** Applies German display rounding while keeping all visible templates in string resources. */
public final class RouteTextFormatter {
    private RouteTextFormatter() {
    }

    public static String airDistance(Context context, double meters) {
        requireNonNegativeFinite(meters);
        if (meters == 0.0) return context.getString(R.string.distance_airline_zero);
        if (meters < 1_000.0) {
            return context.getString(R.string.distance_airline_meters, Math.round(meters));
        }
        return context.getString(R.string.distance_airline_kilometers,
                oneDecimalKilometers(meters));
    }

    public static String routeSummary(Context context, RouteResult route) {
        return context.getString(R.string.route_summary,
                routeLength(context, route.getDistanceMeters()),
                routeDuration(context, route.getDurationSeconds()));
    }

    static String oneDecimalKilometers(double meters) {
        requireNonNegativeFinite(meters);
        return String.format(Locale.GERMANY, "%.1f", meters / 1_000.0);
    }

    static long roundedMeters(double meters) {
        requireNonNegativeFinite(meters);
        return Math.round(meters);
    }

    static long roundedMinutes(double seconds) {
        requireNonNegativeFinite(seconds);
        return Math.round(seconds / 60.0);
    }

    private static String routeLength(Context context, double meters) {
        if (meters < 1_000.0) {
            return context.getString(R.string.route_length_meters, roundedMeters(meters));
        }
        return context.getString(R.string.route_length_kilometers, oneDecimalKilometers(meters));
    }

    private static String routeDuration(Context context, double seconds) {
        long totalMinutes = roundedMinutes(seconds);
        if (totalMinutes < 60) {
            return context.getString(R.string.route_duration_minutes, totalMinutes);
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return minutes == 0
                ? context.getString(R.string.route_duration_hours, hours)
                : context.getString(R.string.route_duration_hours_minutes, hours, minutes);
    }

    private static void requireNonNegativeFinite(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Value must be finite and non-negative");
        }
    }
}
