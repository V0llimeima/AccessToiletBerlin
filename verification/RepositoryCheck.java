package de.bht.accesstoilet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.maplibre.geojson.Point;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.Map;

/** Ausführbare Repository-Prüfung ohne zusätzliche Test-Abhängigkeit. */
public final class RepositoryCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        JsonObject original = JsonParser.parseString(json).getAsJsonObject();
        ToiletRepository.Result result = ToiletRepository.parse(json);
        check(result.getToilets().size() == 509, "509 model objects");
        check(result.getFeatureCollection().features().size() == 509, "509 map features");
        Map<String, Toilet> lookup = result.getToiletsByFacilityId();
        check(lookup.size() == 509, "509 lookup entries");
        for (Toilet toilet : result.getToilets()) {
            check(lookup.get(toilet.getFacilityId()) == toilet,
                    "Lookup resolves the same toilet object: " + toilet.getFacilityId());
        }
        try {
            lookup.clear();
            throw new AssertionError("Mutable lookup map");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
        check(json.equals(result.getGeoJson()), "Exact original map source text preserved");
        for (int i = 0; i < 509; i++) {
            var parsed = result.getFeatureCollection().features().get(i);
            Point point = (Point) parsed.geometry();
            var coordinates = feature(original, i).getAsJsonObject("geometry").getAsJsonArray("coordinates");
            if (!parsed.properties().equals(properties(original, i))
                    || point.longitude() != coordinates.get(0).getAsDouble()
                    || point.latitude() != coordinates.get(1).getAsDouble()) {
                throw new AssertionError("Changed parsed geometry or properties at index " + i);
            }
        }
        checks++;
        check(result.getToilets().stream().filter(t -> t.getOpeningHours() == null).count() == 45,
                "45 nullable opening hours preserved");
        check(result.getToilets().stream().filter(t -> "limited".equals(t.getWheelchair())).count() == 38,
                "38 limited wheelchair values preserved");
        check(result.getToilets().stream().filter(t -> "unknown".equals(t.getFee())).count() == 4,
                "4 unknown fee values preserved");
        Toilet first = result.getToilets().get(0);
        check(first.getLongitude() == 13.412954 && first.getLatitude() == 52.52146304,
                "Longitude and latitude order");
        check("Mo-Sa 07:00-22:00\nSo 10:00-22:00".equals(first.getOpeningHours()),
                "Opening text unchanged");
        try {
            result.getToilets().clear();
            throw new AssertionError("Mutable result list");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }

        JsonObject numericId = original.deepCopy();
        properties(numericId, 0).addProperty("source_id", 12345);
        check("12345".equals(ToiletRepository.parse(numericId.toString()).getToilets().get(0).getSourceId()),
                "Numeric source_id converted to text");
        JsonObject nullable = original.deepCopy();
        properties(nullable, 0).remove("operator");
        properties(nullable, 0).add("name", null);
        properties(nullable, 0).add("source_id", null);
        Toilet nullableToilet = ToiletRepository.parse(nullable.toString()).getToilets().get(0);
        check(nullableToilet.getOperator() == null && nullableToilet.getName() == null
                && nullableToilet.getSourceId() == null, "Missing and null properties preserved");

        reject("Malformed JSON", "{");
        reject("Non-object root", "[]");
        rejectMutation(original, "Wrong collection type", o -> o.addProperty("type", "Feature"));
        rejectMutation(original, "Missing feature array", o -> o.remove("features"));
        rejectMutation(original, "508 features", o -> o.getAsJsonArray("features").remove(0));
        rejectMutation(original, "510 features", o -> o.getAsJsonArray("features").add(feature(o, 0).deepCopy()));
        rejectMutation(original, "Wrong feature type", o -> feature(o, 0).addProperty("type", "Point"));
        rejectMutation(original, "Missing ID", o -> properties(o, 0).remove("facility_id"));
        rejectMutation(original, "Blank ID", o -> properties(o, 0).addProperty("facility_id", "  "));
        rejectMutation(original, "Duplicate ID", o -> properties(o, 1).add("facility_id",
                properties(o, 0).get("facility_id")));
        rejectMutation(original, "Missing properties", o -> feature(o, 0).remove("properties"));
        rejectMutation(original, "Missing geometry", o -> feature(o, 0).remove("geometry"));
        rejectMutation(original, "Non-Point geometry", o -> feature(o, 0).getAsJsonObject("geometry")
                .addProperty("type", "LineString"));
        rejectCoordinates(original, "One coordinate", "[13]");
        rejectCoordinates(original, "Three coordinates", "[13,52,10]");
        rejectCoordinates(original, "Text coordinate", "[\"13\",52]");
        rejectCoordinates(original, "Null coordinate", "[13,null]");
        rejectCoordinates(original, "Boolean coordinate", "[true,52]");
        rejectCoordinates(original, "Longitude range", "[181,52]");
        rejectCoordinates(original, "Latitude range", "[13,91]");
        rejectCoordinates(original, "Non-finite coordinate", "[1e999,52]");
        System.out.println("PASS: " + checks + " repository checks; 509 real features loaded.");
    }

    private static JsonObject feature(JsonObject root, int index) {
        return root.getAsJsonArray("features").get(index).getAsJsonObject();
    }

    private static JsonObject properties(JsonObject root, int index) {
        return feature(root, index).getAsJsonObject("properties");
    }

    private static void rejectCoordinates(JsonObject original, String name, String coordinates) throws IOException {
        rejectMutation(original, name, o -> feature(o, 0).getAsJsonObject("geometry")
                .add("coordinates", JsonParser.parseString(coordinates)));
    }

    private static void rejectMutation(JsonObject original, String name,
            Consumer<JsonObject> mutation) throws IOException {
        JsonObject changed = original.deepCopy();
        mutation.accept(changed);
        reject(name, changed.toString());
    }

    private static void reject(String name, String json) throws IOException {
        try {
            ToiletRepository.parse(json);
            throw new AssertionError("Accepted invalid data: " + name);
        } catch (IOException expected) {
            check(expected.getMessage().startsWith("Invalid toilet GeoJSON:"), name);
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
        checks++;
    }
}
