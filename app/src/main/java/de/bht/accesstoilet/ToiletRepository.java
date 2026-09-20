package de.bht.accesstoilet;

import android.content.res.AssetManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Liest ausschließlich das festgelegte Asset; korrigiert und filtert keine Daten. */
public final class ToiletRepository {

    public static final String ASSET_NAME = "toilets_berlin_open_data_clean.geojson";
    private static final int EXPECTED_COUNT = 509;

    public Result load(AssetManager assets) throws IOException {
        StringBuilder json = new StringBuilder();
        try (Reader reader = new InputStreamReader(assets.open(ASSET_NAME), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Asset loading interrupted");
                }
                json.append(buffer, 0, count);
            }
        }
        return parse(json.toString());
    }

    // Separat vom Android-Dateizugriff prüfbar.
    static Result parse(String json) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(json);
            require(root.isJsonObject(), "Root must be an object");
            JsonObject object = root.getAsJsonObject();
            require("FeatureCollection".equals(text(object, "type")), "Expected FeatureCollection");
            JsonElement rawFeatures = object.get("features");
            require(rawFeatures != null && rawFeatures.isJsonArray(), "Missing feature array");
            JsonArray array = rawFeatures.getAsJsonArray();
            require(array.size() == EXPECTED_COUNT,
                    "Expected " + EXPECTED_COUNT + " features, found " + array.size());

            // Vor dem GeoJSON-Parsing prüfen, damit Textzahlen nicht stillschweigend
            // als Koordinaten akzeptiert und zusätzliche Dimensionen nicht verworfen werden.
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < array.size(); i++) {
                require(array.get(i).isJsonObject(), "Invalid feature at index " + i);
                JsonObject feature = array.get(i).getAsJsonObject();
                require("Feature".equals(text(feature, "type")), "Invalid feature type at index " + i);
                JsonElement rawGeometry = feature.get("geometry");
                require(rawGeometry != null && rawGeometry.isJsonObject(), "Missing geometry at index " + i);
                JsonObject geometry = rawGeometry.getAsJsonObject();
                require("Point".equals(text(geometry, "type")), "Expected Point at index " + i);
                JsonElement rawCoordinates = geometry.get("coordinates");
                require(rawCoordinates != null && rawCoordinates.isJsonArray(),
                        "Missing coordinates at index " + i);
                JsonArray coordinates = rawCoordinates.getAsJsonArray();
                require(coordinates.size() == 2, "Expected two coordinates at index " + i);
                double longitude = coordinate(coordinates.get(0), i);
                double latitude = coordinate(coordinates.get(1), i);
                require(longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90,
                        "Coordinate out of range at index " + i);
                JsonElement rawProperties = feature.get("properties");
                require(rawProperties != null && rawProperties.isJsonObject(),
                        "Missing properties at index " + i);
                JsonObject properties = rawProperties.getAsJsonObject();
                String facilityId = text(properties, "facility_id");
                require(facilityId != null && !facilityId.trim().isEmpty(),
                        "Missing facility_id at index " + i);
                require(ids.add(facilityId), "Duplicate facility_id: " + facilityId);
            }

            FeatureCollection collection = FeatureCollection.fromJson(json);
            List<Feature> features = collection.features();
            require("FeatureCollection".equals(collection.type())
                    && features != null && features.size() == EXPECTED_COUNT,
                    "Invalid parsed FeatureCollection");

            List<Toilet> toilets = new ArrayList<>(EXPECTED_COUNT);
            Map<String, Toilet> toiletsByFacilityId = new LinkedHashMap<>(EXPECTED_COUNT);
            for (Feature feature : features) {
                require(feature != null && feature.geometry() instanceof Point,
                        "Invalid parsed Point");
                Point point = (Point) feature.geometry();
                JsonObject p = feature.properties();
                require(p != null, "Missing parsed properties");
                Toilet toilet = new Toilet(
                        text(p, "facility_id"), text(p, "name"),
                        point.longitude(), point.latitude(), point.type(),
                        text(p, "address"), text(p, "operator"),
                        text(p, "access"), text(p, "fee"), text(p, "payment_method"),
                        text(p, "opening_hours"), text(p, "maintenance_status"),
                        text(p, "wheelchair"), text(p, "changing_table"), text(p, "toilet_type"),
                        text(p, "gender_access"), text(p, "indoor"), text(p, "covered"),
                        text(p, "drinking_water"), text(p, "supervised"),
                        text(p, "source"), sourceId(p), text(p, "source_url"),
                        text(p, "license"), text(p, "last_verified"),
                        text(p, "source_timestamp"), text(p, "download_date"));
                toilets.add(toilet);
                toiletsByFacilityId.put(toilet.getFacilityId(), toilet);
            }
            return new Result(toilets, toiletsByFacilityId, collection, json);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid toilet GeoJSON: " + exception.getMessage(), exception);
        }
    }

    private static double coordinate(JsonElement value, int index) {
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                "Non-numeric coordinate at index " + index);
        double coordinate = value.getAsDouble();
        require(!Double.isNaN(coordinate) && !Double.isInfinite(coordinate),
                "Non-finite coordinate at index " + index);
        return coordinate;
    }

    private static String text(JsonObject properties, String key) {
        JsonElement value = properties.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
                "Expected text or null for " + key);
        return value.getAsString();
    }

    private static String sourceId(JsonObject properties) {
        JsonElement value = properties.get("source_id");
        if (value == null || value.isJsonNull()) {
            return null;
        }
        require(value.isJsonPrimitive(), "Expected scalar source_id");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.getAsString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static final class Result {
        private final List<Toilet> toilets;
        private final Map<String, Toilet> toiletsByFacilityId;
        private final FeatureCollection featureCollection;
        private final String geoJson;

        private Result(List<Toilet> toilets, Map<String, Toilet> toiletsByFacilityId,
                FeatureCollection featureCollection, String geoJson) {
            this.toilets = Collections.unmodifiableList(new ArrayList<>(toilets));
            this.toiletsByFacilityId = Collections.unmodifiableMap(
                    new LinkedHashMap<>(toiletsByFacilityId));
            this.featureCollection = featureCollection;
            this.geoJson = geoJson;
        }

        public List<Toilet> getToilets() { return toilets; }
        public Map<String, Toilet> getToiletsByFacilityId() { return toiletsByFacilityId; }
        public FeatureCollection getFeatureCollection() { return featureCollection; }
        // FeatureCollection.toJson() rundet Koordinaten. Die Kartenquelle erhält
        // deshalb den geprüften Originaltext ohne erneute Serialisierung.
        public String getGeoJson() { return geoJson; }
    }
}
