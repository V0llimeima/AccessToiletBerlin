package de.bht.accesstoilet;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.PointF;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.sources.GeoJsonSource;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final String TAG = "AccessToiletData";
    private static final String TOILET_SOURCE_ID = "toilet-source";
    private static final String TOILET_LAYER_ID = "toilet-layer";
    private static final String ROUTE_SOURCE_ID = "route-source";
    private static final String ROUTE_LAYER_ID = "route-layer";
    private static final String USER_REPORT_SOURCE_ID = "user-report-source";
    private static final String USER_REPORT_LAYER_ID = "user-report-layer";
    private static final String ROUTING_TAG = "AccessToiletRoute";
    private static final String REPORT_TAG = "AccessToiletReports";
    private static final String MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    private static final LatLng BHT_CENTER = new LatLng(52.5451, 13.3547);
    private static final double INITIAL_ZOOM = 14.0;
    private static final float TOUCH_TOLERANCE_DP = 18f;
    private static final float TOUCH_SAMPLE_STEP_DP = 4f;
    private static final double LOCATION_ZOOM = 15.0;
    private static final long MINIMUM_REQUEST_INTERVAL_MILLIS = 1_000L;
    private static final String STATE_LOCATION_DISPLAY_REQUESTED =
            "location_display_requested";
    private static final String STATE_FILTER_WHEELCHAIR = "filter_wheelchair";
    private static final String STATE_FILTER_FEE = "filter_fee";
    private static final String STATE_FILTER_CHANGING_TABLE = "filter_changing_table";

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private LocationManager locationManager;
    private LocationComponent locationComponent;
    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService reportExecutor = Executors.newSingleThreadExecutor();
    private final ValhallaRoutingClient routingClient = new ValhallaRoutingClient();
    private final RoutingSession routingSession = new RoutingSession();
    // Diese Zustände werden ausschließlich auf dem Main Thread gelesen/geschrieben.
    private Style mapStyle;
    private ToiletRepository.Result toiletData;
    private UserReportRepository userReportRepository;
    private List<UserReport> userReports = List.of();
    private ReportLoadState reportLoadState = ReportLoadState.LOADING;
    private final Set<String> loggedOrphanReportFacilities = new HashSet<>();
    private boolean destroyed;
    private boolean loadMessageShown;
    private boolean locationDisplayRequested;
    private boolean locationUpdatesRunning;
    private boolean centerOnNextLocation;
    private Location lastKnownLocation;
    private ToiletFilterState filterState = ToiletFilterState.ALL;
    private MaterialButton filterButton;
    private View routePanel;
    private TextView routeSummary;
    private Future<?> routeFuture;
    private long lastRouteRequestElapsedRealtime = Long.MIN_VALUE;
    private int routingGeneration;
    private androidx.appcompat.app.AlertDialog detailDialog;
    private androidx.appcompat.app.AlertDialog nearbyDialog;
    private androidx.appcompat.app.AlertDialog reportDialog;
    private androidx.appcompat.app.AlertDialog reportEditDialog;

    private enum ReportLoadState { LOADING, LOADED, ERROR }

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) {
                    locationDisplayRequested = true;
                    centerOnNextLocation = true;
                    enableLocationDisplay();
                } else if (!destroyed && !isFinishing()) {
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            locationDisplayRequested = savedInstanceState.getBoolean(
                    STATE_LOCATION_DISPLAY_REQUESTED, false);
            filterState = restoreFilterState(savedInstanceState);
        }
        mapView = findViewById(R.id.mapView);
        filterButton = findViewById(R.id.filterButton);
        routePanel = findViewById(R.id.routePanel);
        routeSummary = findViewById(R.id.routeSummary);
        filterButton.setOnClickListener(view -> showFilterDialog());
        findViewById(R.id.nearbyButton).setOnClickListener(view -> showNearbyToilets());
        findViewById(R.id.locationButton).setOnClickListener(view -> onLocationButtonClicked());
        findViewById(R.id.removeRouteButton).setOnClickListener(view -> removeRoute());
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        userReportRepository = new UserReportRepository(getApplicationContext());
        loadUserReports();
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(map -> {
            mapLibreMap = map;
            map.addOnMapClickListener(this::onMapClicked);
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE_URL),
                style -> {
                    if (destroyed || isFinishing()) {
                        return;
                    }
                    mapStyle = style;
                    enableLocationDisplay();
                    // Eine wiederhergestellte Kamera nach einer Drehung beibehalten.
                    if (savedInstanceState == null) {
                        map.setCameraPosition(new CameraPosition.Builder()
                                .target(BHT_CENTER)
                                .zoom(INITIAL_ZOOM)
                                .build());
                    }
                    showToiletsWhenReady();
                });
        });
        loadToilets();
    }

    private void loadToilets() {
        AssetManager assets = getApplicationContext().getAssets();
        dataExecutor.execute(() -> {
            try {
                ToiletRepository.Result result = new ToiletRepository().load(assets);
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) {
                        return;
                    }
                    toiletData = result;
                    showToiletsWhenReady();
                    if (!loadMessageShown) {
                        loadMessageShown = true;
                        Toast.makeText(this, getString(R.string.toilets_loaded,
                                result.getToilets().size()), Toast.LENGTH_SHORT).show();
                    }
                    Log.i(TAG, "Loaded " + result.getToilets().size() + " toilets from asset");
                });
            } catch (IOException exception) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                Log.e(TAG, "Cannot load " + ToiletRepository.ASSET_NAME, exception);
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing()) {
                        Toast.makeText(this, R.string.toilets_load_error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showToiletsWhenReady() {
        if (destroyed || mapStyle == null || toiletData == null) {
            return;
        }
        if (mapStyle.getSource(TOILET_SOURCE_ID) == null) {
            mapStyle.addSource(new GeoJsonSource(TOILET_SOURCE_ID, toiletData.getGeoJson()));
        }
        if (mapStyle.getLayer(TOILET_LAYER_ID) == null) {
            CircleLayer layer = new CircleLayer(TOILET_LAYER_ID, TOILET_SOURCE_ID);
            layer.setProperties(
                    PropertyFactory.circleRadius(6.5f),
                    PropertyFactory.circleColor("#007EAD"),
                    PropertyFactory.circleOpacity(0.9f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(1.5f));
            mapStyle.addLayer(layer);
            Log.i(TAG, "Added toilet layer with " + toiletData.getToilets().size() + " points");
        }
        applyFilterToMap();
        refreshUserReportSource();
        filterButton.setEnabled(true);
        updateFilterButton();
    }

    private ToiletFilterState restoreFilterState(Bundle state) {
        try {
            return new ToiletFilterState(
                    ToiletFilterState.Wheelchair.values()[state.getInt(STATE_FILTER_WHEELCHAIR)],
                    ToiletFilterState.Fee.values()[state.getInt(STATE_FILTER_FEE)],
                    ToiletFilterState.ChangingTable.values()[state.getInt(STATE_FILTER_CHANGING_TABLE)]);
        } catch (RuntimeException ignored) {
            return ToiletFilterState.ALL;
        }
    }

    private void applyFilterToMap() {
        if (mapStyle == null || toiletData == null) return;
        CircleLayer layer = (CircleLayer) mapStyle.getLayer(TOILET_LAYER_ID);
        if (layer == null) return;
        if (filterState.isAll()) {
            // MapLibre 11.13.1 dereferences a null expression; true leaves all features visible.
            layer.setFilter(Expression.literal(true));
            return;
        }
        java.util.ArrayList<Expression> criteria = new java.util.ArrayList<>();
        if (filterState.getWheelchair() != ToiletFilterState.Wheelchair.ALL) {
            String value = filterState.getWheelchair() == ToiletFilterState.Wheelchair.YES ? "yes"
                    : filterState.getWheelchair() == ToiletFilterState.Wheelchair.LIMITED ? "limited" : "no";
            criteria.add(Expression.eq(Expression.get("wheelchair"), Expression.literal(value)));
        }
        if (filterState.getFee() != ToiletFilterState.Fee.ALL) {
            String value = filterState.getFee() == ToiletFilterState.Fee.FREE ? "no"
                    : filterState.getFee() == ToiletFilterState.Fee.PAID ? "yes" : "unknown";
            criteria.add(Expression.eq(Expression.get("fee"), Expression.literal(value)));
        }
        if (filterState.getChangingTable() != ToiletFilterState.ChangingTable.ALL) {
            criteria.add(Expression.eq(Expression.get("changing_table"), Expression.literal(
                    filterState.getChangingTable() == ToiletFilterState.ChangingTable.YES ? "yes" : "no")));
        }
        layer.setFilter(Expression.all(criteria.toArray(new Expression[0])));
    }

    private int filteredCount(ToiletFilterState state) {
        if (toiletData == null) return 0;
        int count = 0;
        for (Toilet toilet : toiletData.getToilets()) if (state.matches(toilet)) count++;
        return count;
    }

    private void updateFilterButton() {
        if (filterButton == null || toiletData == null) return;
        if (filterState.isAll()) {
            filterButton.setText(R.string.filter_button);
        } else {
            filterButton.setText(getString(R.string.filter_button_active, filteredCount(filterState)));
        }
    }

    private void showFilterDialog() {
        if (toiletData == null) {
            Toast.makeText(this, R.string.filter_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, 0, padding, 0);
        RadioGroup wheelchair = addFilterGroup(content, R.string.filter_wheelchair,
                new int[]{R.string.filter_all, R.string.value_yes, R.string.value_limited, R.string.value_no},
                filterState.getWheelchair().ordinal());
        RadioGroup fee = addFilterGroup(content, R.string.filter_fee,
                new int[]{R.string.filter_all, R.string.value_free, R.string.value_paid, R.string.value_unknown},
                filterState.getFee().ordinal());
        RadioGroup changingTable = addFilterGroup(content, R.string.filter_changing_table,
                new int[]{R.string.filter_all, R.string.value_yes, R.string.value_no},
                filterState.getChangingTable().ordinal());
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_title)
                .setView(scrollView)
                .setNegativeButton(R.string.filter_cancel, null)
                .setNeutralButton(R.string.filter_reset, (dialog, which) -> applyFilter(ToiletFilterState.ALL))
                .setPositiveButton(R.string.filter_apply, (dialog, which) -> applyFilter(new ToiletFilterState(
                        ToiletFilterState.Wheelchair.values()[checkedIndex(wheelchair)],
                        ToiletFilterState.Fee.values()[checkedIndex(fee)],
                        ToiletFilterState.ChangingTable.values()[checkedIndex(changingTable)])))
                .show();
    }

    private void showNearbyToilets() {
        if (toiletData == null) {
            Toast.makeText(this, R.string.nearby_data_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        Location location = lastKnownLocation == null ? null : new Location(lastKnownLocation);
        if (location == null || !isValidCoordinate(
                location.getLatitude(), location.getLongitude())) {
            Toast.makeText(this, R.string.nearby_location_required, Toast.LENGTH_LONG).show();
            return;
        }
        List<NearbyToiletCandidate> candidates = NearbyToiletFinder.findNearest(
                toiletData.getToilets(), filterState,
                location.getLatitude(), location.getLongitude(), 5);
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.nearby_no_results, Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] items = new CharSequence[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            NearbyToiletCandidate candidate = candidates.get(index);
            items[index] = getString(R.string.nearby_candidate_item,
                    candidateLabel(candidate.getToilet()),
                    RouteTextFormatter.airDistance(this, candidate.getDistanceMeters()));
        }
        nearbyDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nearby_title)
                .setItems(items, (dialog, which) -> showToiletDetails(
                        candidates.get(which).getToilet()))
                .setNegativeButton(R.string.details_close, null)
                .create();
        nearbyDialog.setOnDismissListener(dialog -> nearbyDialog = null);
        nearbyDialog.show();
    }

    private String candidateLabel(Toilet toilet) {
        if (toilet.getAddress() != null && !toilet.getAddress().trim().isEmpty()) {
            return toilet.getAddress().trim();
        }
        if (toilet.getName() != null && !toilet.getName().trim().isEmpty()) {
            return toilet.getName().trim();
        }
        return getString(R.string.nearby_fallback_name, toilet.getFacilityId());
    }

    private RadioGroup addFilterGroup(LinearLayout parent, int titleRes, int[] optionResources, int selected) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(18);
        parent.addView(title);
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        for (int index = 0; index < optionResources.length; index++) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(optionResources[index]);
            group.addView(option);
            if (index == selected) group.check(option.getId());
        }
        parent.addView(group);
        return group;
    }

    private int checkedIndex(RadioGroup group) {
        int checkedId = group.getCheckedRadioButtonId();
        for (int index = 0; index < group.getChildCount(); index++) {
            if (group.getChildAt(index).getId() == checkedId) return index;
        }
        return 0;
    }

    private void applyFilter(ToiletFilterState state) {
        cancelRoutingAndRemoveRoute();
        filterState = state;
        applyFilterToMap();
        refreshUserReportSource();
        updateFilterButton();
        int count = filteredCount(state);
        if (count == 0) {
            Toast.makeText(this, R.string.filter_no_results, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.filter_result, count,
                    toiletData.getToilets().size()), Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocationButtonClicked() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_SHORT).show();
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
            return;
        }
        locationDisplayRequested = true;
        centerOnNextLocation = true;
        enableLocationDisplay();
        if (lastKnownLocation != null) {
            updateLocation(lastKnownLocation);
        } else {
            Toast.makeText(this, R.string.location_locating, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocationDisplay() {
        if (destroyed || !locationDisplayRequested || !hasLocationPermission()
                || mapLibreMap == null || mapStyle == null) {
            return;
        }
        locationComponent = mapLibreMap.getLocationComponent();
        if (!locationComponent.isLocationComponentActivated()) {
            LocationComponentActivationOptions options = LocationComponentActivationOptions
                    .builder(this, mapStyle)
                    .useDefaultLocationEngine(false)
                    .build();
            locationComponent.activateLocationComponent(options);
        }
        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setRenderMode(RenderMode.NORMAL);
        locationComponent.setCameraMode(CameraMode.NONE);
        if (lastKnownLocation != null) {
            locationComponent.forceLocationUpdate(lastKnownLocation);
        }
        if (hasWindowFocus()) {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (locationUpdatesRunning || locationManager == null || !hasLocationPermission() || destroyed) {
            return;
        }
        Set<String> providers = new LinkedHashSet<>();
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                providers.add(LocationManager.GPS_PROVIDER);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                providers.add(LocationManager.NETWORK_PROVIDER);
            }
            for (String provider : providers) {
                Location known = locationManager.getLastKnownLocation(provider);
                if (known != null) {
                    updateLocation(known);
                }
                locationManager.requestLocationUpdates(provider, 2_000L, 5f, this);
            }
            locationUpdatesRunning = !providers.isEmpty();
            if (providers.isEmpty()) {
                Toast.makeText(this, R.string.location_services_disabled, Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "Location permission changed while starting updates", exception);
        }
    }

    private void stopLocationUpdates() {
        if (!locationUpdatesRunning || locationManager == null) {
            return;
        }
        locationManager.removeUpdates(this);
        locationUpdatesRunning = false;
    }

    private void updateLocation(@NonNull Location location) {
        if (destroyed) {
            return;
        }
        lastKnownLocation = new Location(location);
        if (locationComponent != null && locationComponent.isLocationComponentActivated()) {
            locationComponent.forceLocationUpdate(lastKnownLocation);
        }
        if (centerOnNextLocation && mapLibreMap != null) {
            centerOnNextLocation = false;
            mapLibreMap.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()))
                    .zoom(LOCATION_ZOOM)
                    .build());
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateLocation(location);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        if (!destroyed) {
            Toast.makeText(this, R.string.location_services_disabled, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean onMapClicked(@NonNull LatLng location) {
        if (destroyed || mapLibreMap == null || mapStyle == null || toiletData == null
                || mapStyle.getLayer(TOILET_LAYER_ID) == null) {
            return false;
        }
        PointF tapPoint = mapLibreMap.getProjection().toScreenLocation(location);
        Feature selectedFeature = closestToiletFeature(tapPoint);
        if (selectedFeature == null || selectedFeature.properties() == null
                || !selectedFeature.properties().has("facility_id")) {
            return false;
        }
        String facilityId;
        try {
            facilityId = selectedFeature.properties().get("facility_id").getAsString();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Invalid facility_id in rendered toilet feature", exception);
            Toast.makeText(this, R.string.details_lookup_error, Toast.LENGTH_SHORT).show();
            return true;
        }
        Toilet toilet = toiletData.getToiletsByFacilityId().get(facilityId);
        if (toilet == null) {
            Log.e(TAG, "Unknown facility_id in rendered toilet feature: " + facilityId);
            Toast.makeText(this, R.string.details_lookup_error, Toast.LENGTH_SHORT).show();
            return true;
        }
        showToiletDetails(toilet);
        return true;
    }

    private Feature closestToiletFeature(PointF tapPoint) {
        float density = getResources().getDisplayMetrics().density;
        float radius = TOUCH_TOLERANCE_DP * density;
        float step = TOUCH_SAMPLE_STEP_DP * density;
        Feature closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        Set<String> inspectedFeatureIds = new HashSet<>();
        for (float offsetX = -radius; offsetX <= radius; offsetX += step) {
            for (float offsetY = -radius; offsetY <= radius; offsetY += step) {
                if (offsetX * offsetX + offsetY * offsetY > radius * radius) {
                    continue;
                }
                List<Feature> features = mapLibreMap.queryRenderedFeatures(
                        new PointF(tapPoint.x + offsetX, tapPoint.y + offsetY), TOILET_LAYER_ID);
                for (Feature feature : features) {
                    if (!(feature.geometry() instanceof Point) || feature.properties() == null
                            || !feature.properties().has("facility_id")) {
                        continue;
                    }
                    String facilityId;
                    try {
                        facilityId = feature.properties().get("facility_id").getAsString();
                    } catch (RuntimeException exception) {
                        Log.e(TAG, "Invalid facility_id in rendered toilet feature", exception);
                        continue;
                    }
                    if (!inspectedFeatureIds.add(facilityId)) {
                        continue;
                    }
                    Point point = (Point) feature.geometry();
                    PointF featurePoint = mapLibreMap.getProjection().toScreenLocation(
                            new LatLng(point.latitude(), point.longitude()));
                    double distanceX = featurePoint.x - tapPoint.x;
                    double distanceY = featurePoint.y - tapPoint.y;
                    double distanceSquared = distanceX * distanceX + distanceY * distanceY;
                    if (distanceSquared <= radius * radius && distanceSquared < closestDistanceSquared) {
                        closest = feature;
                        closestDistanceSquared = distanceSquared;
                    }
                }
            }
        }
        return closest;
    }

    private void showToiletDetails(Toilet toilet) {
        if (destroyed || isFinishing()) {
            return;
        }
        TextView content = new TextView(this);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        String distanceText = getString(R.string.distance_location_unavailable);
        if (lastKnownLocation != null) {
            try {
                double distance = GeoDistance.meters(
                        lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(),
                        toilet.getLatitude(), toilet.getLongitude());
                distanceText = RouteTextFormatter.airDistance(this, distance);
            } catch (IllegalArgumentException exception) {
                Log.w(ROUTING_TAG, "Cannot calculate air-line distance", exception);
            }
        }
        content.setText(ToiletDetailsFormatter.content(this, toilet, distanceText));
        content.setTextIsSelectable(true);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detailDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(ToiletDetailsFormatter.title(this, toilet))
                .setView(scrollView)
                .setNegativeButton(R.string.details_route_show, null)
                .setNeutralButton(R.string.details_reports, null)
                .setPositiveButton(R.string.details_close, null)
                .create();
        detailDialog.setOnDismissListener(dialog -> detailDialog = null);
        detailDialog.show();
        detailDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener(view -> requestRoute(toilet));
        detailDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view -> {
                    detailDialog.dismiss();
                    showUserReports(toilet);
                });
    }

    private void loadUserReports() {
        reportExecutor.execute(() -> {
            try {
                List<UserReport> loaded = userReportRepository.loadAll();
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    userReports = loaded;
                    reportLoadState = ReportLoadState.LOADED;
                    refreshUserReportSource();
                });
            } catch (RuntimeException exception) {
                Log.e(REPORT_TAG, "Cannot load local user reports", exception);
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    reportLoadState = ReportLoadState.ERROR;
                    Toast.makeText(this, R.string.reports_load_error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showUserReports(Toilet toilet) {
        if (destroyed || isFinishing()) return;
        if (reportLoadState == ReportLoadState.LOADING) {
            Toast.makeText(this, R.string.reports_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        if (reportLoadState == ReportLoadState.ERROR) {
            Toast.makeText(this, R.string.reports_load_error, Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<UserReport> reports = new ArrayList<>();
        for (UserReport report : userReports) {
            if (toilet.getFacilityId().equals(report.getFacilityId())) reports.add(report);
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        if (reports.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.reports_empty);
            empty.setTextSize(16);
            empty.setPadding(0, padding, 0, padding);
            content.addView(empty);
        } else {
            for (UserReport report : reports) {
                MaterialButton entry = new MaterialButton(this);
                entry.setAllCaps(false);
                entry.setText(reportListText(report));
                entry.setOnClickListener(view -> {
                    dismissReportDialog();
                    showUserReportDetail(toilet, report);
                });
                content.addView(entry, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(reports.isEmpty() ? getString(R.string.reports_title)
                        : getString(R.string.reports_title_count, reports.size()))
                .setView(scroll)
                .setPositiveButton(R.string.reports_new,
                        (ignored, which) -> showUserReportCategories(toilet))
                .setNegativeButton(R.string.details_close, null)
                .create();
        reportDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (reportDialog == dialog) reportDialog = null;
        });
        dialog.show();
    }

    private void showUserReportCategories(Toilet toilet) {
        UserReportCategory[] categories = UserReportCategory.selectableValues();
        CharSequence[] labels = new CharSequence[categories.length];
        for (int index = 0; index < categories.length; index++) {
            labels[index] = reportCategoryLabel(categories[index]);
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.report_category_title)
                .setItems(labels, (ignored, which) -> showUserReportNoteDialog(
                        toilet, categories[which]))
                .setNegativeButton(R.string.report_cancel, null)
                .create();
        reportEditDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (reportEditDialog == dialog) reportEditDialog = null;
        });
        dialog.show();
    }

    private void showUserReportNoteDialog(Toilet toilet, UserReportCategory category) {
        View content = getLayoutInflater().inflate(R.layout.dialog_user_report_note, null);
        TextView categoryText = content.findViewById(R.id.reportCategoryText);
        EditText noteInput = content.findViewById(R.id.reportNoteInput);
        categoryText.setText(reportCategoryLabel(category));
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reports_new)
                .setView(content)
                .setPositiveButton(R.string.report_save, null)
                .setNegativeButton(R.string.report_cancel, null)
                .create();
        reportEditDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (reportEditDialog == dialog) reportEditDialog = null;
        });
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String note = noteInput.getText() == null
                            ? null : noteInput.getText().toString();
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                            .setEnabled(false);
                    dialog.dismiss();
                    insertUserReport(toilet, category, note);
                });
    }

    private void insertUserReport(Toilet toilet, UserReportCategory category, String note) {
        if (destroyed) return;
        long createdAt = System.currentTimeMillis();
        reportExecutor.execute(() -> {
            try {
                userReportRepository.insert(toilet.getFacilityId(), category, note,
                        toilet.getLatitude(), toilet.getLongitude(), createdAt);
                List<UserReport> loaded = userReportRepository.loadAll();
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    userReports = loaded;
                    reportLoadState = ReportLoadState.LOADED;
                    refreshUserReportSource();
                    Toast.makeText(this, R.string.report_saved, Toast.LENGTH_SHORT).show();
                    showUserReports(toilet);
                });
            } catch (RuntimeException exception) {
                Log.e(REPORT_TAG, "Cannot store local user report", exception);
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing()) {
                        Toast.makeText(this, R.string.report_save_error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showUserReportDetail(Toilet toilet, UserReport report) {
        TextView content = new TextView(this);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        content.setText(getString(R.string.report_detail_text,
                reportCategoryLabel(report.getCategory()),
                formatReportTime(report.getCreatedAtEpochMs()),
                report.getNote() == null ? getString(R.string.report_no_note) : report.getNote(),
                getString(R.string.report_local_detail_notice)));
        content.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(reportCategoryLabel(report.getCategory()))
                .setView(scroll)
                .setNegativeButton(R.string.report_delete,
                        (ignored, which) -> showDeleteUserReportConfirmation(toilet, report))
                .setPositiveButton(R.string.report_back,
                        (ignored, which) -> showUserReports(toilet))
                .create();
        reportDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (reportDialog == dialog) reportDialog = null;
        });
        dialog.show();
    }

    private void showDeleteUserReportConfirmation(Toilet toilet, UserReport report) {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.report_delete_title)
                .setMessage(R.string.report_delete_message)
                .setPositiveButton(R.string.report_delete,
                        (ignored, which) -> deleteUserReport(toilet, report))
                .setNegativeButton(R.string.report_cancel,
                        (ignored, which) -> showUserReportDetail(toilet, report))
                .create();
        reportEditDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (reportEditDialog == dialog) reportEditDialog = null;
        });
        dialog.show();
    }

    private void deleteUserReport(Toilet toilet, UserReport report) {
        if (destroyed) return;
        reportExecutor.execute(() -> {
            try {
                if (!userReportRepository.deleteById(report.getId())) {
                    throw new IllegalStateException("Report was not deleted");
                }
                List<UserReport> loaded = userReportRepository.loadAll();
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    userReports = loaded;
                    reportLoadState = ReportLoadState.LOADED;
                    refreshUserReportSource();
                    Toast.makeText(this, R.string.report_deleted, Toast.LENGTH_SHORT).show();
                    showUserReports(toilet);
                });
            } catch (RuntimeException exception) {
                Log.e(REPORT_TAG, "Cannot delete local user report", exception);
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing()) {
                        Toast.makeText(this, R.string.report_delete_error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private CharSequence reportListText(UserReport report) {
        String category = reportCategoryLabel(report.getCategory());
        String time = formatReportTime(report.getCreatedAtEpochMs());
        return report.getNote() == null
                ? getString(R.string.report_list_item_without_note, category, time)
                : getString(R.string.report_list_item, category, time, report.getNote());
    }

    private String formatReportTime(long timestamp) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.GERMANY)
                .format(new Date(timestamp));
    }

    private String reportCategoryLabel(UserReportCategory category) {
        switch (category) {
            case DEFECT: return getString(R.string.report_category_defect);
            case CLOSED: return getString(R.string.report_category_closed);
            case CLEANLINESS: return getString(R.string.report_category_cleanliness);
            case MISSING_EQUIPMENT: return getString(R.string.report_category_missing_equipment);
            case BARRIER: return getString(R.string.report_category_barrier);
            case WRONG_LOCATION: return getString(R.string.report_category_wrong_location);
            case ODOR: return getString(R.string.report_category_odor);
            default: return getString(R.string.report_category_unknown);
        }
    }

    private void refreshUserReportSource() {
        if (destroyed || mapStyle == null || toiletData == null
                || reportLoadState != ReportLoadState.LOADED) return;
        for (UserReport report : userReports) {
            if (!toiletData.getToiletsByFacilityId().containsKey(report.getFacilityId())
                    && loggedOrphanReportFacilities.add(report.getFacilityId())) {
                Log.w(REPORT_TAG, "Skipping report for unknown facility_id: "
                        + report.getFacilityId());
            }
        }
        List<UserReportMapBuilder.Marker> markers = UserReportMapBuilder.build(
                userReports, toiletData.getToiletsByFacilityId(), filterState);
        ArrayList<Feature> features = new ArrayList<>(markers.size());
        for (UserReportMapBuilder.Marker marker : markers) {
            Feature feature = Feature.fromGeometry(Point.fromLngLat(
                    marker.getLongitude(), marker.getLatitude()));
            feature.addStringProperty("facility_id", marker.getFacilityId());
            feature.addNumberProperty("report_count", marker.getReportCount());
            features.add(feature);
        }
        FeatureCollection collection = FeatureCollection.fromFeatures(features);
        GeoJsonSource source = mapStyle.getSourceAs(USER_REPORT_SOURCE_ID);
        if (source == null) {
            mapStyle.addSource(new GeoJsonSource(USER_REPORT_SOURCE_ID, collection));
        } else {
            source.setGeoJson(collection);
        }
        if (mapStyle.getLayer(USER_REPORT_LAYER_ID) == null) {
            CircleLayer layer = new CircleLayer(USER_REPORT_LAYER_ID, USER_REPORT_SOURCE_ID);
            layer.setProperties(
                    PropertyFactory.circleRadius(11.0f),
                    PropertyFactory.circleColor("#FFB300"),
                    PropertyFactory.circleOpacity(0.68f),
                    PropertyFactory.circleStrokeColor("#6D4C00"),
                    PropertyFactory.circleStrokeWidth(2.5f));
            if (mapStyle.getLayer(TOILET_LAYER_ID) != null) {
                mapStyle.addLayerBelow(layer, TOILET_LAYER_ID);
            } else {
                mapStyle.addLayer(layer);
            }
        }
    }

    private void dismissReportDialog() {
        if (reportDialog != null) {
            reportDialog.dismiss();
            reportDialog = null;
        }
    }

    private void requestRoute(Toilet toilet) {
        if (routingSession.isRequestRunning()) {
            Toast.makeText(this, R.string.route_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        Location start = lastKnownLocation == null ? null : new Location(lastKnownLocation);
        if (start == null || !isValidCoordinate(start.getLatitude(), start.getLongitude())) {
            Toast.makeText(this, R.string.route_location_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (!hasNetworkConnection()) {
            Toast.makeText(this, R.string.route_no_network, Toast.LENGTH_LONG).show();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastRouteRequestElapsedRealtime != Long.MIN_VALUE
                && now - lastRouteRequestElapsedRealtime < MINIMUM_REQUEST_INTERVAL_MILLIS) {
            Toast.makeText(this, R.string.route_request_too_soon, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!routingSession.beginRequest(true)) {
            Toast.makeText(this, R.string.route_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        lastRouteRequestElapsedRealtime = now;
        int requestGeneration = ++routingGeneration;
        Toast.makeText(this, R.string.route_in_progress, Toast.LENGTH_SHORT).show();
        if (detailDialog != null) detailDialog.dismiss();
        routeFuture = routeExecutor.submit(() -> {
            try {
                RouteResult result = routingClient.requestRoute(
                        start.getLatitude(), start.getLongitude(),
                        toilet.getLatitude(), toilet.getLongitude());
                runOnUiThread(() -> finishRouteRequest(requestGeneration, result));
            } catch (ValhallaRoutingClient.RoutingException exception) {
                runOnUiThread(() -> failRouteRequest(requestGeneration, exception));
            }
        });
    }

    private void finishRouteRequest(int requestGeneration, RouteResult result) {
        if (destroyed || requestGeneration != routingGeneration || isFinishing()) return;
        routeFuture = null;
        try {
            displayRoute(result);
            routingSession.complete(result);
            Toast.makeText(this, R.string.route_accessibility_notice, Toast.LENGTH_LONG).show();
        } catch (RuntimeException exception) {
            routingSession.fail();
            Log.e(ROUTING_TAG, "Cannot display validated route", exception);
            Toast.makeText(this, R.string.route_error_response, Toast.LENGTH_LONG).show();
        }
    }

    private void failRouteRequest(
            int requestGeneration, ValhallaRoutingClient.RoutingException exception) {
        if (destroyed || requestGeneration != routingGeneration || isFinishing()) return;
        routeFuture = null;
        routingSession.fail();
        if (exception.getReason() == ValhallaRoutingClient.Reason.CANCELLED) return;
        Log.w(ROUTING_TAG, "Routing failed: " + exception.getReason()
                + ", status=" + exception.getStatusCode(), exception);
        Toast.makeText(this, routeErrorMessage(exception.getReason()), Toast.LENGTH_LONG).show();
    }

    private int routeErrorMessage(ValhallaRoutingClient.Reason reason) {
        switch (reason) {
            case TIMEOUT: return R.string.route_error_timeout;
            case RATE_LIMIT: return R.string.route_error_rate_limit;
            case SERVER: return R.string.route_error_server;
            case BAD_REQUEST:
            case INVALID_REQUEST: return R.string.route_error_request;
            case INVALID_RESPONSE:
            case RESPONSE_TOO_LARGE: return R.string.route_error_response;
            default: return R.string.route_error_network;
        }
    }

    private void displayRoute(RouteResult result) {
        if (mapStyle == null || mapLibreMap == null) {
            throw new IllegalStateException("Map style is unavailable");
        }
        ArrayList<Point> points = new ArrayList<>(result.getCoordinates().size());
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (RouteResult.Coordinate coordinate : result.getCoordinates()) {
            points.add(Point.fromLngLat(coordinate.getLongitude(), coordinate.getLatitude()));
            bounds.include(new LatLng(coordinate.getLatitude(), coordinate.getLongitude()));
        }
        LineString line = LineString.fromLngLats(points);
        GeoJsonSource source = mapStyle.getSourceAs(ROUTE_SOURCE_ID);
        if (source == null) {
            mapStyle.addSource(new GeoJsonSource(ROUTE_SOURCE_ID, line));
            LineLayer layer = new LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID);
            layer.setProperties(
                    PropertyFactory.lineColor("#D81B60"),
                    PropertyFactory.lineWidth(6.0f),
                    PropertyFactory.lineOpacity(0.92f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));
            mapStyle.addLayerBelow(layer, TOILET_LAYER_ID);
        } else {
            source.setGeoJson(line);
        }
        routeSummary.setText(RouteTextFormatter.routeSummary(this, result));
        routePanel.setVisibility(View.VISIBLE);
        int padding = Math.round(88 * getResources().getDisplayMetrics().density);
        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding));
    }

    private void removeRoute() {
        cancelActiveRouteRequest();
        routingSession.clear();
        removeRouteFromMap();
    }

    private void cancelRoutingAndRemoveRoute() {
        cancelActiveRouteRequest();
        routingSession.clearForFilterChange();
        removeRouteFromMap();
    }

    private void cancelActiveRouteRequest() {
        routingGeneration++;
        if (routeFuture != null) {
            routeFuture.cancel(true);
            routeFuture = null;
        }
        routingClient.cancelActiveRequest();
        routingSession.fail();
    }

    private void removeRouteFromMap() {
        if (mapStyle != null) {
            if (mapStyle.getLayer(ROUTE_LAYER_ID) != null) mapStyle.removeLayer(ROUTE_LAYER_ID);
            if (mapStyle.getSource(ROUTE_SOURCE_ID) != null) mapStyle.removeSource(ROUTE_SOURCE_ID);
        }
        if (routeSummary != null) routeSummary.setText("");
        if (routePanel != null) routePanel.setVisibility(View.GONE);
    }

    private boolean hasNetworkConnection() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static boolean isValidCoordinate(double latitude, double longitude) {
        try {
            GeoDistance.requireCoordinate(latitude, longitude);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (locationDisplayRequested) {
            enableLocationDisplay();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && locationDisplayRequested) {
            // Der Kartenstil kann erst nach onResume() fertig laden.
            enableLocationDisplay();
        }
    }

    @Override
    protected void onPause() {
        stopLocationUpdates();
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        mapView.onStop();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Nur die vom Nutzer bewusst aktivierte Anzeige merken, keine Standortkoordinaten.
        outState.putBoolean(STATE_LOCATION_DISPLAY_REQUESTED, locationDisplayRequested);
        outState.putInt(STATE_FILTER_WHEELCHAIR, filterState.getWheelchair().ordinal());
        outState.putInt(STATE_FILTER_FEE, filterState.getFee().ordinal());
        outState.putInt(STATE_FILTER_CHANGING_TABLE, filterState.getChangingTable().ordinal());
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        cancelActiveRouteRequest();
        routingSession.clear();
        removeRouteFromMap();
        stopLocationUpdates();
        if (locationComponent != null && locationComponent.isLocationComponentActivated()) {
            locationComponent.setLocationComponentEnabled(false);
        }
        if (detailDialog != null) {
            detailDialog.dismiss();
            detailDialog = null;
        }
        if (nearbyDialog != null) {
            nearbyDialog.dismiss();
            nearbyDialog = null;
        }
        dismissReportDialog();
        if (reportEditDialog != null) {
            reportEditDialog.dismiss();
            reportEditDialog = null;
        }
        if (userReportRepository != null) {
            reportExecutor.execute(userReportRepository::close);
        }
        reportExecutor.shutdown();
        dataExecutor.shutdownNow();
        routeExecutor.shutdownNow();
        mapStyle = null;
        mapLibreMap = null;
        toiletData = null;
        userReports = List.of();
        mapView.onDestroy();
        super.onDestroy();
    }
}
