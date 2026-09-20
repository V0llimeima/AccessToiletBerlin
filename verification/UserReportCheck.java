package de.bht.accesstoilet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable host checks for the pure Phase-8 report model and map aggregation. */
public final class UserReportCheck {
    private static int checks;

    public static void main(String[] args) {
        checkCategories();
        checkModel();
        checkValidation();
        checkMapAggregation();
        System.out.println("PASS: " + checks
                + " user-report checks; SQLite itself is verified on the emulator.");
    }

    private static void checkCategories() {
        UserReportCategory[] selectable = UserReportCategory.selectableValues();
        check(selectable.length == 7, "seven selectable categories");
        Set<String> codes = new HashSet<>();
        for (UserReportCategory category : selectable) {
            check(category.isSelectable(), "listed category selectable");
            check(codes.add(category.getCode()), "category code unique");
            check(UserReportCategory.fromCode(category.getCode()) == category,
                    "known category parses");
        }
        check(UserReportCategory.fromCode("future_code") == UserReportCategory.UNKNOWN,
                "unknown code fallback");
        check(UserReportCategory.fromCode(null) == UserReportCategory.UNKNOWN,
                "null code fallback");
        check(!UserReportCategory.UNKNOWN.isSelectable(), "unknown not selectable");
        rejects(() -> UserReport.validateNew("facility", UserReportCategory.UNKNOWN,
                null, 52, 13, 1), "unknown rejected for insert");
    }

    private static void checkModel() {
        UserReport report = report(7, "facility-a", UserReportCategory.DEFECT,
                "  Tür klemmt  ", 52.5, 13.4, 123456L);
        check(report.getId() == 7, "id retained");
        check(report.getFacilityId().equals("facility-a"), "facility retained");
        check(report.getCategory() == UserReportCategory.DEFECT, "category retained");
        check(report.getNote().equals("Tür klemmt"), "note trimmed");
        check(report.getLatitude() == 52.5, "latitude retained");
        check(report.getLongitude() == 13.4, "longitude retained");
        check(report.getCreatedAtEpochMs() == 123456L, "timestamp retained");
        check(report(1, "x", UserReportCategory.CLOSED, null, 0, 0, 1).getNote() == null,
                "null note retained");
        check(report(1, "x", UserReportCategory.CLOSED, "   ", 0, 0, 1).getNote() == null,
                "whitespace note becomes null");
        check(report(1, "x", UserReportCategory.UNKNOWN, null, 0, 0, 1)
                .getCategory() == UserReportCategory.UNKNOWN, "unknown DB category readable");
        String fiveHundred = "x".repeat(500);
        check(report(1, "x", UserReportCategory.ODOR, fiveHundred, 0, 0, 1)
                .getNote().length() == 500, "500-character note accepted");
    }

    private static void checkValidation() {
        rejects(() -> report(0, "x", UserReportCategory.DEFECT, null, 0, 0, 1),
                "zero id rejected");
        rejects(() -> report(-1, "x", UserReportCategory.DEFECT, null, 0, 0, 1),
                "negative id rejected");
        rejects(() -> report(1, "", UserReportCategory.DEFECT, null, 0, 0, 1),
                "empty facility rejected");
        rejects(() -> report(1, "   ", UserReportCategory.DEFECT, null, 0, 0, 1),
                "blank facility rejected");
        rejects(() -> report(1, null, UserReportCategory.DEFECT, null, 0, 0, 1),
                "null facility rejected");
        rejects(() -> report(1, "x", null, null, 0, 0, 1),
                "null category rejected");
        rejects(() -> report(1, "x", UserReportCategory.DEFECT,
                "x".repeat(501), 0, 0, 1), "long note rejected");
        rejects(() -> report(1, "x", UserReportCategory.DEFECT, null,
                Double.NaN, 0, 1), "NaN latitude rejected");
        rejects(() -> report(1, "x", UserReportCategory.DEFECT, null,
                0, Double.POSITIVE_INFINITY, 1), "infinite longitude rejected");
        rejects(() -> report(1, "x", UserReportCategory.DEFECT, null,
                90.0001, 0, 1), "latitude range enforced");
        rejects(() -> report(1, "x", UserReportCategory.DEFECT, null,
                0, -180.0001, 1), "longitude range enforced");
        rejects(() -> report(1, "x", UserReportCategory.DEFECT, null,
                0, 0, 0), "timestamp rejected");
        UserReport.validateNew("x", UserReportCategory.BARRIER, null, -90, -180, 1);
        checks++;
        UserReport.validateNew("x", UserReportCategory.BARRIER, null, 90, 180, 1);
        checks++;
    }

    private static void checkMapAggregation() {
        Toilet toiletA = toilet("a", 13.1, 52.1, "yes", "no", "yes");
        Toilet toiletB = toilet("b", 13.2, 52.2, "no", "yes", "no");
        Map<String, Toilet> lookup = new LinkedHashMap<>();
        lookup.put("a", toiletA);
        lookup.put("b", toiletB);
        UserReport newestA = report(3, "a", UserReportCategory.DEFECT,
                "new", 52.101, 13.101, 300);
        UserReport olderA = report(2, "a", UserReportCategory.CLOSED,
                null, 52.099, 13.099, 200);
        UserReport reportB = report(1, "b", UserReportCategory.ODOR,
                null, 52.2, 13.2, 100);
        ArrayList<UserReport> input = new ArrayList<>(List.of(newestA, olderA, reportB));
        List<UserReport> before = List.copyOf(input);
        List<UserReportMapBuilder.Marker> markers = UserReportMapBuilder.build(
                input, lookup, ToiletFilterState.ALL);
        check(markers.size() == 2, "two facilities make two markers");
        check(markers.get(0).getFacilityId().equals("a"), "first facility stable");
        check(markers.get(0).getReportCount() == 2, "same facility aggregated");
        check(markers.get(0).getLatitude() == 52.101, "newest latitude used");
        check(markers.get(0).getLongitude() == 13.101, "newest longitude used");
        check(markers.get(1).getReportCount() == 1, "second facility count");
        check(input.equals(before), "report input not mutated");
        check(input.get(0) == before.get(0), "report references unchanged");
        rejectsUnsupported(() -> markers.add(markers.get(0)), "marker result immutable");

        ToiletFilterState accessibleFreeChanging = new ToiletFilterState(
                ToiletFilterState.Wheelchair.YES,
                ToiletFilterState.Fee.FREE,
                ToiletFilterState.ChangingTable.YES);
        List<UserReportMapBuilder.Marker> filtered = UserReportMapBuilder.build(
                input, lookup, accessibleFreeChanging);
        check(filtered.size() == 1, "filtered toilet has no marker");
        check(filtered.get(0).getFacilityId().equals("a"), "matching toilet keeps marker");
        check(filtered.get(0).getReportCount() == 2, "filter does not change report count");

        ArrayList<UserReport> withOrphan = new ArrayList<>(input);
        withOrphan.add(0, report(4, "orphan", UserReportCategory.BARRIER,
                null, 52.3, 13.3, 400));
        List<UserReportMapBuilder.Marker> withoutOrphan = UserReportMapBuilder.build(
                withOrphan, lookup, ToiletFilterState.ALL);
        check(withoutOrphan.size() == 2, "orphan has no regular marker");
        check(withoutOrphan.stream().noneMatch(marker ->
                marker.getFacilityId().equals("orphan")), "orphan omitted");
        check(UserReportMapBuilder.build(List.of(), lookup, ToiletFilterState.ALL).isEmpty(),
                "empty reports make empty collection");
    }

    private static UserReport report(long id, String facilityId, UserReportCategory category,
            String note, double latitude, double longitude, long createdAt) {
        return new UserReport(id, facilityId, category, note,
                latitude, longitude, createdAt);
    }

    private static Toilet toilet(String id, double longitude, double latitude,
            String wheelchair, String fee, String changingTable) {
        return new Toilet(id, null, longitude, latitude, "Point", null, null, null,
                fee, null, null, null, wheelchair, changingTable, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private static void rejects(Runnable runnable, String name) {
        try {
            runnable.run();
            throw new AssertionError(name);
        } catch (IllegalArgumentException | NullPointerException expected) {
            checks++;
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

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
