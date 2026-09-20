package de.bht.accesstoilet;

import android.content.Context;

import java.util.Locale;

/** Erzeugt nur Dialogtexte; fachliche Rohwerte bleiben im Toilet-Objekt unverändert. */
public final class ToiletDetailsFormatter {

    private ToiletDetailsFormatter() {
    }

    public static String title(Context context, Toilet toilet) {
        return isMissing(toilet.getName())
                ? context.getString(R.string.details_default_title)
                : toilet.getName().trim();
    }

    public static String content(Context context, Toilet toilet) {
        return content(context, toilet, context.getString(R.string.distance_location_unavailable));
    }

    public static String content(Context context, Toilet toilet, String distanceText) {
        StringBuilder text = new StringBuilder();
        append(text, context, R.string.details_address, value(context, toilet.getAddress()));
        append(text, context, R.string.details_distance, distanceText);
        append(text, context, R.string.details_opening_hours, value(context, toilet.getOpeningHours()));
        append(text, context, R.string.details_wheelchair, generic(context, toilet.getWheelchair()));
        append(text, context, R.string.details_fee, fee(context, toilet.getFee()));
        append(text, context, R.string.details_changing_table, generic(context, toilet.getChangingTable()));
        append(text, context, R.string.details_access, access(context, toilet.getAccess()));
        append(text, context, R.string.details_indoor, indoor(context, toilet.getIndoor()));
        append(text, context, R.string.details_payment_method, value(context, toilet.getPaymentMethod()));
        append(text, context, R.string.details_operator, value(context, toilet.getOperator()));
        append(text, context, R.string.details_source, value(context, toilet.getSource()));
        append(text, context, R.string.details_source_id, value(context, toilet.getSourceId()));
        append(text, context, R.string.details_data_status, value(context, toilet.getLastVerified()));
        append(text, context, R.string.details_latitude,
                String.format(Locale.GERMANY, "%.6f", toilet.getLatitude()));
        append(text, context, R.string.details_longitude,
                String.format(Locale.GERMANY, "%.6f", toilet.getLongitude()));
        return text.toString();
    }

    private static void append(StringBuilder text, Context context, int labelId, String value) {
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append(context.getString(labelId)).append(": ").append(value);
    }

    private static String fee(Context context, String raw) {
        if ("no".equals(raw)) {
            return context.getString(R.string.value_free);
        }
        if ("yes".equals(raw)) {
            return context.getString(R.string.value_paid);
        }
        return generic(context, raw);
    }

    private static String access(Context context, String raw) {
        if ("public".equals(raw)) {
            return context.getString(R.string.value_public);
        }
        return generic(context, raw);
    }

    private static String indoor(Context context, String raw) {
        if ("yes".equals(raw)) {
            return context.getString(R.string.value_indoor);
        }
        if ("no".equals(raw)) {
            return context.getString(R.string.value_outdoor);
        }
        return generic(context, raw);
    }

    private static String generic(Context context, String raw) {
        if (isMissing(raw)) {
            return context.getString(R.string.value_no_information);
        }
        switch (raw) {
            case "yes":
                return context.getString(R.string.value_yes);
            case "no":
                return context.getString(R.string.value_no);
            case "limited":
                return context.getString(R.string.value_limited);
            case "unknown":
                return context.getString(R.string.value_unknown);
            default:
                return raw;
        }
    }

    private static String value(Context context, String raw) {
        return isMissing(raw) ? context.getString(R.string.value_no_information) : raw;
    }

    private static boolean isMissing(String value) {
        return value == null || value.trim().isEmpty();
    }
}
