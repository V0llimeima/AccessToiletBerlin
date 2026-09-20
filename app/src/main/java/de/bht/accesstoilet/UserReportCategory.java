package de.bht.accesstoilet;

/** Stable database codes for local user reports. */
public enum UserReportCategory {
    DEFECT("defect", true),
    CLOSED("closed", true),
    CLEANLINESS("cleanliness", true),
    MISSING_EQUIPMENT("missing_equipment", true),
    BARRIER("barrier", true),
    WRONG_LOCATION("wrong_location", true),
    ODOR("odor", true),
    UNKNOWN("unknown", false);

    private final String code;
    private final boolean selectable;

    UserReportCategory(String code, boolean selectable) {
        this.code = code;
        this.selectable = selectable;
    }

    public String getCode() {
        return code;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public static UserReportCategory fromCode(String code) {
        if (code != null) {
            for (UserReportCategory category : values()) {
                if (category != UNKNOWN && category.code.equals(code)) {
                    return category;
                }
            }
        }
        return UNKNOWN;
    }

    public static UserReportCategory[] selectableValues() {
        return new UserReportCategory[]{
                DEFECT, CLOSED, CLEANLINESS, MISSING_EQUIPMENT,
                BARRIER, WRONG_LOCATION, ODOR
        };
    }
}
