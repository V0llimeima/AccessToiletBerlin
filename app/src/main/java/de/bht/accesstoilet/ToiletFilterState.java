package de.bht.accesstoilet;

import java.util.Objects;

/** Immutable filter choices; concrete choices deliberately match only exact raw values. */
public final class ToiletFilterState {
    public enum Wheelchair { ALL, YES, LIMITED, NO }
    public enum Fee { ALL, FREE, PAID, UNKNOWN }
    public enum ChangingTable { ALL, YES, NO }

    public static final ToiletFilterState ALL = new ToiletFilterState(
            Wheelchair.ALL, Fee.ALL, ChangingTable.ALL);

    private final Wheelchair wheelchair;
    private final Fee fee;
    private final ChangingTable changingTable;

    public ToiletFilterState(Wheelchair wheelchair, Fee fee, ChangingTable changingTable) {
        this.wheelchair = Objects.requireNonNull(wheelchair);
        this.fee = Objects.requireNonNull(fee);
        this.changingTable = Objects.requireNonNull(changingTable);
    }

    public Wheelchair getWheelchair() { return wheelchair; }
    public Fee getFee() { return fee; }
    public ChangingTable getChangingTable() { return changingTable; }
    public boolean isAll() { return wheelchair == Wheelchair.ALL && fee == Fee.ALL && changingTable == ChangingTable.ALL; }

    public boolean matches(Toilet toilet) {
        return matchesWheelchair(toilet.getWheelchair()) && matchesFee(toilet.getFee())
                && matchesChangingTable(toilet.getChangingTable());
    }

    private boolean matchesWheelchair(String value) {
        return wheelchair == Wheelchair.ALL || (wheelchair == Wheelchair.YES && "yes".equals(value))
                || (wheelchair == Wheelchair.LIMITED && "limited".equals(value))
                || (wheelchair == Wheelchair.NO && "no".equals(value));
    }

    private boolean matchesFee(String value) {
        return fee == Fee.ALL || (fee == Fee.FREE && "no".equals(value))
                || (fee == Fee.PAID && "yes".equals(value))
                || (fee == Fee.UNKNOWN && "unknown".equals(value));
    }

    private boolean matchesChangingTable(String value) {
        return changingTable == ChangingTable.ALL || (changingTable == ChangingTable.YES && "yes".equals(value))
                || (changingTable == ChangingTable.NO && "no".equals(value));
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ToiletFilterState)) return false;
        ToiletFilterState that = (ToiletFilterState) other;
        return wheelchair == that.wheelchair && fee == that.fee && changingTable == that.changingTable;
    }
    @Override public int hashCode() { return Objects.hash(wheelchair, fee, changingTable); }
}
