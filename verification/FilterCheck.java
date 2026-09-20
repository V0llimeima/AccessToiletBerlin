package de.bht.accesstoilet;

import java.nio.file.Files;
import java.nio.file.Path;

/** Executable checks for Phase-5 filter semantics without a test dependency. */
public final class FilterCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var toilets = ToiletRepository.parse(Files.readString(Path.of(args[0]))).getToilets();
        expect(toilets, ToiletFilterState.ALL, 509, "all");
        expect(toilets, state(ToiletFilterState.Wheelchair.YES, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.ALL), 359, "wheelchair yes");
        expect(toilets, state(ToiletFilterState.Wheelchair.LIMITED, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.ALL), 38, "wheelchair limited");
        expect(toilets, state(ToiletFilterState.Wheelchair.NO, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.ALL), 112, "wheelchair no");
        expect(toilets, state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.ALL), 306, "free");
        expect(toilets, state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.PAID, ToiletFilterState.ChangingTable.ALL), 199, "paid");
        expect(toilets, state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.UNKNOWN, ToiletFilterState.ChangingTable.ALL), 4, "unknown fee");
        expect(toilets, state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.YES), 138, "changing yes");
        expect(toilets, state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.NO), 371, "changing no");
        expect(toilets, state(ToiletFilterState.Wheelchair.YES, ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.ALL), 172, "yes free");
        expect(toilets, state(ToiletFilterState.Wheelchair.LIMITED, ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.ALL), 33, "limited free");
        expect(toilets, state(ToiletFilterState.Wheelchair.YES, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.YES), 118, "yes changing");
        expect(toilets, state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.YES), 77, "free changing");
        expect(toilets, state(ToiletFilterState.Wheelchair.YES, ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.YES), 64, "all three");
        expect(toilets, state(ToiletFilterState.Wheelchair.LIMITED, ToiletFilterState.Fee.PAID, ToiletFilterState.ChangingTable.NO), 0, "zero result");
        Toilet unknown = toilet(null, null, null);
        check(ToiletFilterState.ALL.matches(unknown), "all includes missing values");
        check(!state(ToiletFilterState.Wheelchair.NO, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.ALL).matches(unknown), "missing is not no");
        check(!state(ToiletFilterState.Wheelchair.YES, ToiletFilterState.Fee.ALL, ToiletFilterState.ChangingTable.ALL).matches(toilet("limited", "no", "yes")), "limited is not yes");
        check(state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.FREE, ToiletFilterState.ChangingTable.ALL).matches(toilet("yes", "no", "no")), "fee no is free");
        check(state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.PAID, ToiletFilterState.ChangingTable.ALL).matches(toilet("yes", "yes", "no")), "fee yes is paid");
        check(!state(ToiletFilterState.Wheelchair.ALL, ToiletFilterState.Fee.UNKNOWN, ToiletFilterState.ChangingTable.ALL).matches(toilet("yes", null, "no")), "unknown differs from null");
        System.out.println("PASS: " + checks + " filter checks; 509 real features evaluated.");
    }
    private static ToiletFilterState state(ToiletFilterState.Wheelchair w, ToiletFilterState.Fee f, ToiletFilterState.ChangingTable c) { return new ToiletFilterState(w, f, c); }
    private static void expect(java.util.List<Toilet> toilets, ToiletFilterState state, int expected, String name) { long count = toilets.stream().filter(state::matches).count(); check(count == expected, name); }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static Toilet toilet(String wheelchair, String fee, String changing) { return new Toilet("test", null, 0, 0, "Point", null, null, null, fee, null, null, null, wheelchair, changing, null, null, null, null, null, null, null, null, null, null, null, null, null); }
}
