package de.bht.accesstoilet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Executable Phase-7 checks; candidate searches are local and perform no network I/O. */
public final class CandidateSearchCheck {
    private static final double BHT_LATITUDE = 52.5451;
    private static final double BHT_LONGITUDE = 13.3547;
    private static int checks;

    public static void main(String[] args) throws Exception {
        checkSyntheticSearches();
        checkValidationAndImmutability();
        checkRealData(Path.of(args[0]));
        System.out.println("PASS: " + checks
                + " candidate-search checks; all searches stayed local.");
    }

    private static void checkSyntheticSearches() {
        Toilet close = toilet("close", 0.0, 0.001, "yes", "no", "yes");
        Toilet medium = toilet("medium", 0.0, 0.002, "limited", "unknown", "no");
        Toilet far = toilet("far", 0.0, 0.003, "no", "yes", "no");
        List<Toilet> basic = List.of(far, close, medium);
        List<NearbyToiletCandidate> sorted = find(basic, ToiletFilterState.ALL, 0, 0, 5);
        check(ids(sorted).equals(List.of("close", "medium", "far")),
                "unfiltered Haversine order");
        check(sorted.get(0).getDistanceMeters() < sorted.get(1).getDistanceMeters(),
                "full distances ascend");

        ArrayList<Toilet> seven = new ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            seven.add(toilet("id-" + index, 0, index * 0.001, "yes", "no", "yes"));
        }
        check(find(seven, ToiletFilterState.ALL, 0, 0, 5).size() == 5,
                "limit five");
        check(find(basic, ToiletFilterState.ALL, 0, 0, 20).size() == 3,
                "limit above result count");

        check(ids(find(basic, state(ToiletFilterState.Wheelchair.YES,
                ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.ALL), 0, 0, 5))
                .equals(List.of("close")), "wheelchair yes exact");
        check(ids(find(basic, state(ToiletFilterState.Wheelchair.LIMITED,
                ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.ALL), 0, 0, 5))
                .equals(List.of("medium")), "wheelchair limited exact");
        check(ids(find(basic, state(ToiletFilterState.Wheelchair.ALL,
                ToiletFilterState.Fee.UNKNOWN, ToiletFilterState.ChangingTable.ALL), 0, 0, 5))
                .equals(List.of("medium")), "unknown fee exact");
        check(ids(find(basic, state(ToiletFilterState.Wheelchair.ALL,
                ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.YES), 0, 0, 5))
                .equals(List.of("close")), "changing table exact");
        check(ids(find(basic, state(ToiletFilterState.Wheelchair.YES,
                ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.YES), 0, 0, 5))
                .equals(List.of("close")), "combined filters use AND");
        check(find(basic, state(ToiletFilterState.Wheelchair.LIMITED,
                ToiletFilterState.Fee.PAID, ToiletFilterState.ChangingTable.YES), 0, 0, 5)
                .isEmpty(), "zero-result logic");

        Toilet tieB = toilet("b", 1, 1, "yes", "no", "yes");
        Toilet tieA = toilet("a", 1, 1, "yes", "no", "yes");
        check(ids(find(List.of(tieB, tieA), ToiletFilterState.ALL, 0, 0, 5))
                .equals(List.of("a", "b")), "distance tie uses facility id");

        Toilet exact = toilet("exact", 0, 0, "yes", "no", "yes");
        List<NearbyToiletCandidate> zero = find(List.of(close, exact),
                ToiletFilterState.ALL, 0, 0, 5);
        check(zero.get(0).getToilet() == exact, "zero-distance candidate first");
        check(zero.get(0).getDistanceMeters() == 0.0, "zero distance retained");

        Toilet roundedFarther = toilet("a-farther", 0, 0.001002, "yes", "no", "yes");
        Toilet roundedCloser = toilet("z-closer", 0, 0.001000, "yes", "no", "yes");
        List<NearbyToiletCandidate> unrounded = find(
                List.of(roundedFarther, roundedCloser), ToiletFilterState.ALL, 0, 0, 5);
        check(unrounded.get(0).getToilet() == roundedCloser,
                "unrounded distance precedes facility id");
        check(Math.round(unrounded.get(0).getDistanceMeters())
                        == Math.round(unrounded.get(1).getDistanceMeters()),
                "unrounded regression uses equal displayed meters");
        check(sorted.get(0).getToilet() == close, "candidate keeps toilet reference");

        Toilet west = toilet("west", -179.95, 0, "yes", "no", "yes");
        Toilet eastFar = toilet("east-far", 179.0, 0, "yes", "no", "yes");
        check(find(List.of(eastFar, west), ToiletFilterState.ALL, 0, 179.9, 2)
                .get(0).getToilet() == west, "antimeridian uses GeoDistance");
    }

    private static void checkValidationAndImmutability() {
        ArrayList<Toilet> mutableInput = new ArrayList<>(List.of(
                toilet("two", 0, 0.002, "yes", "no", "yes"),
                toilet("one", 0, 0.001, "yes", "no", "yes")));
        List<Toilet> before = List.copyOf(mutableInput);
        List<NearbyToiletCandidate> result = find(
                mutableInput, ToiletFilterState.ALL, 0, 0, 5);
        check(mutableInput.equals(before), "input order unchanged");
        check(mutableInput.get(0) == before.get(0), "input objects unchanged");
        rejectsUnsupported(() -> result.add(result.get(0)), "result list immutable");
        rejects(() -> NearbyToiletFinder.findNearest(mutableInput,
                ToiletFilterState.ALL, Double.NaN, 0, 5), "invalid user NaN");
        rejects(() -> NearbyToiletFinder.findNearest(mutableInput,
                ToiletFilterState.ALL, 91, 0, 5), "invalid user latitude");
        rejects(() -> NearbyToiletFinder.findNearest(List.of(
                toilet("invalid", 181, 0, "yes", "no", "yes")),
                ToiletFilterState.ALL, 0, 0, 5), "invalid toilet coordinate");
        rejects(() -> NearbyToiletFinder.findNearest(List.of(
                toilet(null, 0, 0, "yes", "no", "yes")),
                ToiletFilterState.ALL, 0, 0, 5), "missing facility id");
        rejects(() -> NearbyToiletFinder.findNearest(mutableInput,
                ToiletFilterState.ALL, 0, 0, 0), "zero limit");
        rejects(() -> NearbyToiletFinder.findNearest(mutableInput,
                ToiletFilterState.ALL, 0, 0, -1), "negative limit");
        rejects(() -> new NearbyToiletCandidate(mutableInput.get(0), Double.NaN),
                "candidate rejects invalid distance");
    }

    private static void checkRealData(Path asset) throws Exception {
        ToiletRepository.Result repository = ToiletRepository.parse(Files.readString(asset));
        List<Toilet> toilets = repository.getToilets();
        check(toilets.size() == 509, "real feature count");
        List<Toilet> before = List.copyOf(toilets);
        List<NearbyToiletCandidate> nearest = find(
                toilets, ToiletFilterState.ALL, BHT_LATITUDE, BHT_LONGITUDE, 5);
        check(nearest.size() == 5, "real all-filter limit five");
        check(toilets.equals(before), "real list unchanged");
        checkSorted(nearest);
        Set<String> ids = new HashSet<>();
        for (NearbyToiletCandidate candidate : nearest) {
            check(Double.isFinite(candidate.getDistanceMeters())
                    && candidate.getDistanceMeters() >= 0, "real finite distance");
            String id = candidate.getToilet().getFacilityId();
            check(ids.add(id), "real unique result id");
            check(repository.getToiletsByFacilityId().get(id) == candidate.getToilet(),
                    "real result in repository lookup");
        }
        List<NearbyToiletCandidate> repeated = find(
                toilets, ToiletFilterState.ALL, BHT_LATITUDE, BHT_LONGITUDE, 5);
        check(ids(nearest).equals(ids(repeated)), "real search deterministic ids");
        for (int index = 0; index < nearest.size(); index++) {
            check(nearest.get(index).getDistanceMeters() == repeated.get(index).getDistanceMeters(),
                    "real search deterministic distance");
        }

        ToiletFilterState zeroFilter = state(ToiletFilterState.Wheelchair.LIMITED,
                ToiletFilterState.Fee.PAID, ToiletFilterState.ChangingTable.NO);
        check(find(toilets, zeroFilter, BHT_LATITUDE, BHT_LONGITUDE, 5).isEmpty(),
                "real known zero combination");

        ToiletFilterState combined = state(ToiletFilterState.Wheelchair.YES,
                ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.YES);
        List<NearbyToiletCandidate> allCombined = find(
                toilets, combined, BHT_LATITUDE, BHT_LONGITUDE, 600);
        long filterCount = toilets.stream().filter(combined::matches).count();
        check(allCombined.size() == filterCount, "finder and filter candidate set agree");
        check(filterCount == 64, "real combined filter regression");

        StringBuilder top = new StringBuilder("REAL_TOP5:");
        for (NearbyToiletCandidate candidate : nearest) {
            top.append(' ').append(candidate.getToilet().getFacilityId())
                    .append('=').append(Math.round(candidate.getDistanceMeters())).append("m");
        }
        System.out.println(top);
    }

    private static void checkSorted(List<NearbyToiletCandidate> candidates) {
        for (int index = 1; index < candidates.size(); index++) {
            NearbyToiletCandidate previous = candidates.get(index - 1);
            NearbyToiletCandidate current = candidates.get(index);
            int distanceOrder = Double.compare(
                    previous.getDistanceMeters(), current.getDistanceMeters());
            check(distanceOrder < 0 || (distanceOrder == 0
                    && previous.getToilet().getFacilityId().compareTo(
                    current.getToilet().getFacilityId()) <= 0), "real sorted order");
        }
    }

    private static List<NearbyToiletCandidate> find(List<Toilet> toilets,
            ToiletFilterState state, double latitude, double longitude, int limit) {
        return NearbyToiletFinder.findNearest(toilets, state, latitude, longitude, limit);
    }

    private static ToiletFilterState state(ToiletFilterState.Wheelchair wheelchair,
            ToiletFilterState.Fee fee, ToiletFilterState.ChangingTable changingTable) {
        return new ToiletFilterState(wheelchair, fee, changingTable);
    }

    private static List<String> ids(List<NearbyToiletCandidate> candidates) {
        return candidates.stream().map(candidate -> candidate.getToilet().getFacilityId()).toList();
    }

    private static Toilet toilet(String id, double longitude, double latitude,
            String wheelchair, String fee, String changingTable) {
        return new Toilet(id, null, longitude, latitude, "Point", null, null, null,
                fee, null, null, null, wheelchair, changingTable, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
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
