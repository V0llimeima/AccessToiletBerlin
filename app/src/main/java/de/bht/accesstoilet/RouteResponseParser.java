package de.bht.accesstoilet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Parses only complete Valhalla route responses requested with shape_format=geojson. */
public final class RouteResponseParser {
    private RouteResponseParser() {
    }

    public static RouteResult parse(String response) throws RouteParseException {
        try {
            JsonObject root = object(JsonParser.parseString(response), "response");
            JsonObject trip = object(root.get("trip"), "trip");
            JsonObject summary = object(trip.get("summary"), "summary");
            double lengthKilometers = finiteNonNegativeNumber(summary.get("length"), "length");
            double durationSeconds = finiteNonNegativeNumber(summary.get("time"), "time");
            double distanceMeters = lengthKilometers * 1_000.0;
            if (!Double.isFinite(distanceMeters)) {
                throw new RouteParseException("Route length is outside the supported range");
            }

            JsonArray legs = array(trip.get("legs"), "legs");
            if (legs.isEmpty()) throw new RouteParseException("Route has no legs");
            List<RouteResult.Coordinate> coordinates = new ArrayList<>();
            for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
                JsonObject leg = object(legs.get(legIndex), "leg");
                List<RouteResult.Coordinate> legCoordinates = parseShape(leg.get("shape"));
                for (int positionIndex = 0; positionIndex < legCoordinates.size(); positionIndex++) {
                    RouteResult.Coordinate coordinate = legCoordinates.get(positionIndex);
                    if (legIndex > 0 && positionIndex == 0 && sameAsLast(coordinates, coordinate)) {
                        continue;
                    }
                    coordinates.add(coordinate);
                }
            }
            try {
                return new RouteResult(coordinates, distanceMeters, durationSeconds);
            } catch (IllegalArgumentException exception) {
                throw new RouteParseException("Route result is invalid", exception);
            }
        } catch (RouteParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RouteParseException("Incomplete or invalid Valhalla JSON", exception);
        }
    }

    private static List<RouteResult.Coordinate> parseShape(JsonElement value)
            throws RouteParseException {
        if (value == null) throw new RouteParseException("shape is missing");
        if (value.isJsonObject()) return parseGeoJsonShape(value.getAsJsonObject());
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new RouteParseException("shape is neither GeoJSON nor an encoded polyline");
        }
        String encodedOrJson = value.getAsString();
        if (encodedOrJson.trim().startsWith("{")) {
            try {
                return parseGeoJsonShape(JsonParser.parseString(encodedOrJson).getAsJsonObject());
            } catch (RuntimeException exception) {
                throw new RouteParseException("Stringified GeoJSON shape is invalid", exception);
            }
        }
        return decodePolyline6(encodedOrJson);
    }

    private static List<RouteResult.Coordinate> parseGeoJsonShape(JsonObject shape)
            throws RouteParseException {
        if (!string(shape.get("type"), "shape type").equals("LineString")) {
            throw new RouteParseException("Route shape is not a GeoJSON LineString");
        }
        JsonArray positions = array(shape.get("coordinates"), "coordinates");
        if (positions.size() < 2) {
            throw new RouteParseException("Route leg has fewer than two coordinates");
        }
        List<RouteResult.Coordinate> coordinates = new ArrayList<>(positions.size());
        for (JsonElement value : positions) {
            JsonArray position = array(value, "position");
            if (position.size() != 2) {
                throw new RouteParseException("Route coordinate must be [longitude, latitude]");
            }
            coordinates.add(coordinate(
                    finiteNumber(position.get(0), "longitude"),
                    finiteNumber(position.get(1), "latitude")));
        }
        return coordinates;
    }

    private static List<RouteResult.Coordinate> decodePolyline6(String encoded)
            throws RouteParseException {
        if (encoded.isEmpty()) throw new RouteParseException("Encoded route shape is empty");
        List<RouteResult.Coordinate> coordinates = new ArrayList<>();
        int[] index = {0};
        long latitude = 0;
        long longitude = 0;
        try {
            while (index[0] < encoded.length()) {
                latitude = Math.addExact(latitude, decodeDelta(encoded, index));
                longitude = Math.addExact(longitude, decodeDelta(encoded, index));
                coordinates.add(coordinate(longitude * 1e-6, latitude * 1e-6));
            }
        } catch (ArithmeticException exception) {
            throw new RouteParseException("Encoded route shape overflows", exception);
        }
        if (coordinates.size() < 2) {
            throw new RouteParseException("Route leg has fewer than two coordinates");
        }
        return coordinates;
    }

    private static long decodeDelta(String encoded, int[] index) throws RouteParseException {
        long result = 0;
        int shift = 0;
        int chunk;
        do {
            if (index[0] >= encoded.length() || shift > 60) {
                throw new RouteParseException("Encoded route shape is truncated or invalid");
            }
            chunk = encoded.charAt(index[0]++) - 63;
            if (chunk < 0 || chunk > 63) {
                throw new RouteParseException("Encoded route shape contains an invalid character");
            }
            result |= (long) (chunk & 0x1f) << shift;
            shift += 5;
        } while (chunk >= 0x20);
        return (result & 1L) != 0L ? ~(result >> 1) : result >> 1;
    }

    private static RouteResult.Coordinate coordinate(double longitude, double latitude)
            throws RouteParseException {
        try {
            return new RouteResult.Coordinate(longitude, latitude);
        } catch (IllegalArgumentException exception) {
            throw new RouteParseException("Route contains an invalid coordinate", exception);
        }
    }

    private static boolean sameAsLast(
            List<RouteResult.Coordinate> coordinates, RouteResult.Coordinate coordinate) {
        if (coordinates.isEmpty()) return false;
        RouteResult.Coordinate previous = coordinates.get(coordinates.size() - 1);
        return previous.getLongitude() == coordinate.getLongitude()
                && previous.getLatitude() == coordinate.getLatitude();
    }

    private static JsonObject object(JsonElement value, String name) throws RouteParseException {
        if (value == null || !value.isJsonObject()) {
            throw new RouteParseException(name + " is missing or not an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, String name) throws RouteParseException {
        if (value == null || !value.isJsonArray()) {
            throw new RouteParseException(name + " is missing or not an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonElement value, String name) throws RouteParseException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new RouteParseException(name + " is missing or not a string");
        }
        return value.getAsString();
    }

    private static double finiteNonNegativeNumber(JsonElement value, String name)
            throws RouteParseException {
        double number = finiteNumber(value, name);
        if (number < 0.0) throw new RouteParseException(name + " must be non-negative");
        return number;
    }

    private static double finiteNumber(JsonElement value, String name) throws RouteParseException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new RouteParseException(name + " is missing or not numeric");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw new RouteParseException(name + " must be finite");
        return number;
    }

    public static final class RouteParseException extends Exception {
        public RouteParseException(String message) { super(message); }
        public RouteParseException(String message, Throwable cause) { super(message, cause); }
    }
}
