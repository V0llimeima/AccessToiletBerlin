package de.bht.accesstoilet;

/** Unveränderliches Toilettenobjekt; null, unknown und limited bleiben unterscheidbar. */
public final class Toilet {

    private final String facilityId;
    private final String name;
    private final double longitude;
    private final double latitude;
    private final String geometryType;
    private final String address;
    private final String operator;
    private final String access;
    private final String fee;
    private final String paymentMethod;
    private final String openingHours;
    private final String maintenanceStatus;
    private final String wheelchair;
    private final String changingTable;
    private final String toiletType;
    private final String genderAccess;
    private final String indoor;
    private final String covered;
    private final String drinkingWater;
    private final String supervised;
    private final String source;
    private final String sourceId;
    private final String sourceUrl;
    private final String license;
    private final String lastVerified;
    private final String sourceTimestamp;
    private final String downloadDate;

    public Toilet(
            String facilityId,
            String name,
            double longitude,
            double latitude,
            String geometryType,
            String address,
            String operator,
            String access,
            String fee,
            String paymentMethod,
            String openingHours,
            String maintenanceStatus,
            String wheelchair,
            String changingTable,
            String toiletType,
            String genderAccess,
            String indoor,
            String covered,
            String drinkingWater,
            String supervised,
            String source,
            String sourceId,
            String sourceUrl,
            String license,
            String lastVerified,
            String sourceTimestamp,
            String downloadDate) {
        this.facilityId = facilityId;
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
        this.geometryType = geometryType;
        this.address = address;
        this.operator = operator;
        this.access = access;
        this.fee = fee;
        this.paymentMethod = paymentMethod;
        this.openingHours = openingHours;
        this.maintenanceStatus = maintenanceStatus;
        this.wheelchair = wheelchair;
        this.changingTable = changingTable;
        this.toiletType = toiletType;
        this.genderAccess = genderAccess;
        this.indoor = indoor;
        this.covered = covered;
        this.drinkingWater = drinkingWater;
        this.supervised = supervised;
        this.source = source;
        this.sourceId = sourceId;
        this.sourceUrl = sourceUrl;
        this.license = license;
        this.lastVerified = lastVerified;
        this.sourceTimestamp = sourceTimestamp;
        this.downloadDate = downloadDate;
    }

    public String getFacilityId() { return facilityId; }
    public String getName() { return name; }
    public double getLongitude() { return longitude; }
    public double getLatitude() { return latitude; }
    public String getGeometryType() { return geometryType; }
    public String getAddress() { return address; }
    public String getOperator() { return operator; }
    public String getAccess() { return access; }
    public String getFee() { return fee; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getOpeningHours() { return openingHours; }
    public String getMaintenanceStatus() { return maintenanceStatus; }
    public String getWheelchair() { return wheelchair; }
    public String getChangingTable() { return changingTable; }
    public String getToiletType() { return toiletType; }
    public String getGenderAccess() { return genderAccess; }
    public String getIndoor() { return indoor; }
    public String getCovered() { return covered; }
    public String getDrinkingWater() { return drinkingWater; }
    public String getSupervised() { return supervised; }
    public String getSource() { return source; }
    public String getSourceId() { return sourceId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getLicense() { return license; }
    public String getLastVerified() { return lastVerified; }
    public String getSourceTimestamp() { return sourceTimestamp; }
    public String getDownloadDate() { return downloadDate; }
}
