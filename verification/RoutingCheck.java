package de.bht.accesstoilet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executable Phase-6 checks; no request is sent to the public routing service. */
public final class RoutingCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        checkDistanceCalculations();
        RouteResult valid = checkRouteParser();
        checkImmutabilityAndState(valid);
        checkRequestContract();
        checkResourceFormats(Path.of(args[0]));
        System.out.println("PASS: " + checks + " routing checks; no network request performed.");
    }

    private static void checkDistanceCalculations() {
        check(GeoDistance.meters(52.5451, 13.3547, 52.5451, 13.3547) == 0.0,
                "identical coordinates");
        double forward = GeoDistance.meters(52.5451, 13.3547, 52.546271, 13.352299);
        double reverse = GeoDistance.meters(52.546271, 13.352299, 52.5451, 13.3547);
        check(Math.abs(forward - reverse) < 1e-9, "distance symmetry");
        check(Math.abs(GeoDistance.meters(0, 0, 1, 0) - 111_195.08) < 1.0,
                "known one-degree distance");
        check(Math.abs(GeoDistance.meters(0, 179.9, 0, -179.9) - 22_239.02) < 1.0,
                "antimeridian distance");
        rejects(() -> GeoDistance.meters(Double.NaN, 0, 0, 0), "NaN latitude");
        rejects(() -> GeoDistance.meters(0, Double.POSITIVE_INFINITY, 0, 0), "infinite longitude");
        rejects(() -> GeoDistance.meters(90.1, 0, 0, 0), "latitude range");
        rejects(() -> GeoDistance.meters(0, -180.1, 0, 0), "longitude range");
        check(RouteTextFormatter.roundedMeters(239.6) == 240, "meter rounding");
        check("1,3".equals(RouteTextFormatter.oneDecimalKilometers(1_250)),
                "German kilometer decimal");
    }

    private static RouteResult checkRouteParser() throws Exception {
        String validJson = validResponse(false);
        RouteResult result = RouteResponseParser.parse(validJson);
        check(result.getCoordinates().size() == 3, "valid coordinate count");
        check(result.getCoordinates().get(0).getLongitude() == 13.3547,
                "longitude is first");
        check(result.getCoordinates().get(0).getLatitude() == 52.5451,
                "latitude is second");
        check(Math.abs(result.getDistanceMeters() - 620.0) < 1e-9,
                "route length converted to meters");
        check(result.getDurationSeconds() == 540.0, "route duration");
        RouteResult withUnknown = RouteResponseParser.parse(validResponse(true));
        check(withUnknown.getCoordinates().size() == 3, "unknown fields ignored");
        RouteResult encoded = RouteResponseParser.parse(encodedResponse());
        check(encoded.getCoordinates().size() == 3, "polyline6 fallback coordinate count");
        check(Math.abs(encoded.getCoordinates().get(2).getLongitude() - 13.352299) < 1e-6,
                "polyline6 longitude decoded");
        check(Math.abs(encoded.getCoordinates().get(2).getLatitude() - 52.546271) < 1e-6,
                "polyline6 latitude decoded");

        parseRejects("{}", "missing trip");
        parseRejects("{\"trip\":{\"summary\":{\"length\":1,\"time\":1}}}", "missing legs");
        parseRejects("{\"trip\":{\"summary\":{\"length\":1,\"time\":1},\"legs\":[{}]}}",
                "missing geometry");
        parseRejects(responseWithCoordinates("[[13.3,52.5]]"), "too few coordinates");
        parseRejects(responseWithCoordinates("[[181,52.5],[13.4,52.6]]"), "invalid longitude");
        parseRejects(responseWithCoordinates("[[13.3,-91],[13.4,52.6]]"), "invalid latitude");
        parseRejects(responseWithSummary(-1, 20), "negative distance");
        parseRejects(responseWithSummary(1, -20), "negative duration");
        rejects(() -> new RouteResult(result.getCoordinates(), Double.NaN, 1),
                "non-finite distance");
        rejects(() -> new RouteResult(result.getCoordinates(), 1, Double.POSITIVE_INFINITY),
                "non-finite duration");
        return result;
    }

    private static void checkImmutabilityAndState(RouteResult valid) {
        rejectsUnsupported(() -> valid.getCoordinates().add(
                new RouteResult.Coordinate(13, 52)), "route list unmodifiable");
        ArrayList<RouteResult.Coordinate> original = new ArrayList<>(valid.getCoordinates());
        RouteResult copy = new RouteResult(original, 10, 20);
        original.clear();
        check(copy.getCoordinates().size() == 3, "route defensively copied");

        RoutingSession locationState = new RoutingSession();
        check(!locationState.beginRequest(false), "missing location blocks request");
        check(!locationState.isRequestRunning(), "blocked request stays idle");

        RoutingSession duplicateState = new RoutingSession();
        check(duplicateState.beginRequest(true), "first request starts");
        check(!duplicateState.beginRequest(true), "duplicate request blocked");
        duplicateState.complete(valid);
        check(duplicateState.getActiveRoute() == valid, "complete route accepted atomically");
        check(duplicateState.clearForFilterChange(), "filter reports active route removal");
        check(duplicateState.getActiveRoute() == null, "filter removes route");

        RoutingSession failureState = new RoutingSession();
        check(failureState.beginRequest(true), "initial request starts");
        failureState.complete(valid);
        check(failureState.beginRequest(true), "replacement request starts");
        failureState.fail();
        check(failureState.getActiveRoute() == valid,
                "failed replacement keeps fully validated route");
    }

    private static void checkRequestContract() throws Exception {
        JsonObject request = JsonParser.parseString(ValhallaRoutingClient.createRequest(
                52.5451, 13.3547, 52.546271, 13.352299)).getAsJsonObject();
        check("pedestrian".equals(request.get("costing").getAsString()), "pedestrian costing");
        check("geojson".equals(request.get("shape_format").getAsString()), "GeoJSON shape request");
        check("kilometers".equals(request.get("units").getAsString()), "kilometer units");
        check("de-DE".equals(request.getAsJsonObject("directions_options").get("language").getAsString()),
                "German directions language");
        check(request.getAsJsonArray("locations").size() == 2, "only start and target sent");
        check("de.bht.accesstoilet.courseproject".equals(ValhallaRoutingClient.CLIENT_ID),
                "identifying client id");
        check(ValhallaRoutingClient.CONNECT_TIMEOUT_MILLIS == 10_000, "connect timeout");
        check(ValhallaRoutingClient.READ_TIMEOUT_MILLIS == 15_000, "read timeout");
        check(ValhallaRoutingClient.MAX_RESPONSE_BYTES == 2 * 1024 * 1024,
                "response size limit");
    }

    private static void checkResourceFormats(Path stringsPath) throws Exception {
        String strings = Files.readString(stringsPath);
        check(strings.contains("ca. %1$d m Luftlinie"), "air-line meter resource");
        check(strings.contains("ca. %1$s km Luftlinie"), "air-line kilometer resource");
        check(strings.contains("Fußweg: %1$s · ca. %2$s"), "route summary resource");
        check(strings.contains("%1$d min"), "minute resource");
        check(strings.contains("%1$d h %2$d min"), "hour-minute resource");
        check(RouteTextFormatter.roundedMinutes(539) == 9, "minutes rounded");
        check(RouteTextFormatter.roundedMinutes(3_900) == 65, "hours retain remaining minutes");
    }

    private static String validResponse(boolean unknownField) throws Exception {
        JsonArray coordinates = new JsonArray();
        coordinates.add(position(13.3547, 52.5451));
        coordinates.add(position(13.3530, 52.5455));
        coordinates.add(position(13.352299, 52.546271));
        JsonObject shape = new JsonObject();
        shape.addProperty("type", "LineString");
        shape.add("coordinates", coordinates);
        JsonObject leg = new JsonObject();
        leg.add("shape", shape);
        if (unknownField) {
            JsonObject future = new JsonObject();
            future.addProperty("anything", true);
            leg.add("future_field", future);
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("length", 0.62);
        summary.addProperty("time", 540);
        JsonArray legs = new JsonArray();
        legs.add(leg);
        JsonObject trip = new JsonObject();
        trip.add("summary", summary);
        trip.add("legs", legs);
        JsonObject root = new JsonObject();
        root.add("trip", trip);
        return root.toString();
    }

    private static JsonArray position(double longitude, double latitude) {
        JsonArray position = new JsonArray();
        position.add(longitude);
        position.add(latitude);
        return position;
    }

    private static String responseWithCoordinates(String coordinates) {
        return "{\"trip\":{\"summary\":{\"length\":1,\"time\":1},\"legs\":[{\"shape\":"
                + "{\"type\":\"LineString\",\"coordinates\":" + coordinates + "}}]}}";
    }

    private static String encodedResponse() {
        String encoded = encodePolyline6(new double[][]{
                {13.3547, 52.5451}, {13.3530, 52.5455}, {13.352299, 52.546271}});
        JsonObject summary = new JsonObject();
        summary.addProperty("length", 0.62);
        summary.addProperty("time", 540);
        JsonObject leg = new JsonObject();
        leg.addProperty("shape", encoded);
        JsonArray legs = new JsonArray();
        legs.add(leg);
        JsonObject trip = new JsonObject();
        trip.add("summary", summary);
        trip.add("legs", legs);
        JsonObject root = new JsonObject();
        root.add("trip", trip);
        return root.toString();
    }

    private static String encodePolyline6(double[][] longitudeLatitude) {
        StringBuilder encoded = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;
        for (double[] position : longitudeLatitude) {
            long longitude = Math.round(position[0] * 1_000_000);
            long latitude = Math.round(position[1] * 1_000_000);
            encodeDelta(encoded, latitude - previousLatitude);
            encodeDelta(encoded, longitude - previousLongitude);
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return encoded.toString();
    }

    private static void encodeDelta(StringBuilder encoded, long delta) {
        long value = delta < 0 ? ~(delta << 1) : delta << 1;
        while (value >= 0x20) {
            encoded.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        encoded.append((char) (value + 63));
    }

    private static String responseWithSummary(double length, double time) {
        return "{\"trip\":{\"summary\":{\"length\":" + length + ",\"time\":" + time
                + "},\"legs\":[{\"shape\":{\"type\":\"LineString\",\"coordinates\":"
                + "[[13.3,52.5],[13.4,52.6]]}}]}}";
    }

    private static void parseRejects(String response, String name) {
        try {
            RouteResponseParser.parse(response);
            throw new AssertionError(name);
        } catch (RouteResponseParser.RouteParseException expected) {
            checks++;
        }
    }

    private static void rejects(ThrowingRunnable runnable, String name) {
        try {
            runnable.run();
            throw new AssertionError(name);
        } catch (IllegalArgumentException expected) {
            checks++;
        } catch (Exception exception) {
            throw new AssertionError(name, exception);
        }
    }

    private static void rejectsUnsupported(Runnable runnable, String name) {
        try {
            runnable.run();
            throw new AssertionError(name);
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
