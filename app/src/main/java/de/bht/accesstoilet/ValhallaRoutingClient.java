package de.bht.accesstoilet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One-shot HTTP client for the fixed public Valhalla demo endpoint. */
public final class ValhallaRoutingClient {
    public static final String ENDPOINT = "https://valhalla1.openstreetmap.de/route";
    public static final String CLIENT_ID = "de.bht.accesstoilet.courseproject";
    static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    static final int READ_TIMEOUT_MILLIS = 15_000;
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final Object connectionLock = new Object();
    private HttpURLConnection activeConnection;

    public RouteResult requestRoute(
            double startLatitude,
            double startLongitude,
            double destinationLatitude,
            double destinationLongitude) throws RoutingException {
        GeoDistance.requireCoordinate(startLatitude, startLongitude);
        GeoDistance.requireCoordinate(destinationLatitude, destinationLongitude);
        HttpURLConnection connection = null;
        try {
            byte[] requestBytes = createRequest(startLatitude, startLongitude,
                    destinationLatitude, destinationLongitude)
                    .getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            synchronized (connectionLock) {
                activeConnection = connection;
            }
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setDoOutput(true);
            connection.setRequestProperty("X-Client-Id", CLIENT_ID);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setFixedLengthStreamingMode(requestBytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
            }
            ensureNotInterrupted();
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw statusException(status);
            }
            String response;
            try (InputStream input = connection.getInputStream()) {
                response = readLimited(input, connection.getContentLengthLong());
            }
            ensureNotInterrupted();
            try {
                return RouteResponseParser.parse(response);
            } catch (RouteResponseParser.RouteParseException exception) {
                throw new RoutingException(Reason.INVALID_RESPONSE, -1,
                        "Valhalla response validation failed", exception);
            }
        } catch (RoutingException exception) {
            throw exception;
        } catch (SocketTimeoutException exception) {
            throw new RoutingException(Reason.TIMEOUT, -1, "Valhalla request timed out", exception);
        } catch (InterruptedIOException exception) {
            Thread.currentThread().interrupt();
            throw new RoutingException(Reason.CANCELLED, -1, "Valhalla request was cancelled", exception);
        } catch (IOException exception) {
            Reason reason = Thread.currentThread().isInterrupted() ? Reason.CANCELLED : Reason.NETWORK;
            throw new RoutingException(reason, -1, "Valhalla request failed", exception);
        } catch (IllegalArgumentException exception) {
            throw new RoutingException(Reason.INVALID_REQUEST, -1,
                    "Cannot create Valhalla request", exception);
        } finally {
            if (connection != null) connection.disconnect();
            synchronized (connectionLock) {
                if (activeConnection == connection) activeConnection = null;
            }
        }
    }

    public void cancelActiveRequest() {
        synchronized (connectionLock) {
            if (activeConnection != null) activeConnection.disconnect();
        }
    }

    static String createRequest(double startLatitude, double startLongitude,
            double destinationLatitude, double destinationLongitude) {
        JsonArray locations = new JsonArray();
        locations.add(location(startLatitude, startLongitude));
        locations.add(location(destinationLatitude, destinationLongitude));
        JsonObject directions = new JsonObject();
        directions.addProperty("units", "kilometers");
        directions.addProperty("language", "de-DE");
        JsonObject request = new JsonObject();
        request.add("locations", locations);
        request.addProperty("costing", "pedestrian");
        request.addProperty("units", "kilometers");
        request.addProperty("shape_format", "geojson");
        request.add("directions_options", directions);
        return request.toString();
    }

    private static JsonObject location(double latitude, double longitude) {
        JsonObject location = new JsonObject();
        location.addProperty("lat", latitude);
        location.addProperty("lon", longitude);
        return location;
    }

    private static String readLimited(InputStream input, long declaredLength)
            throws IOException, RoutingException {
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw new RoutingException(Reason.RESPONSE_TOO_LARGE, -1,
                    "Valhalla response exceeds limit", null);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                declaredLength > 0 ? (int) declaredLength : 16_384);
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            ensureNotInterrupted();
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new RoutingException(Reason.RESPONSE_TOO_LARGE, -1,
                        "Valhalla response exceeds limit", null);
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void ensureNotInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Interrupted");
        }
    }

    private static RoutingException statusException(int status) {
        if (status == 400) return new RoutingException(Reason.BAD_REQUEST, status, "HTTP 400", null);
        if (status == 429) return new RoutingException(Reason.RATE_LIMIT, status, "HTTP 429", null);
        if (status >= 500) return new RoutingException(Reason.SERVER, status, "HTTP server error", null);
        return new RoutingException(Reason.HTTP, status, "Unexpected HTTP status", null);
    }

    public enum Reason {
        NETWORK, TIMEOUT, BAD_REQUEST, RATE_LIMIT, SERVER, HTTP,
        INVALID_REQUEST, INVALID_RESPONSE, RESPONSE_TOO_LARGE, CANCELLED
    }

    public static final class RoutingException extends Exception {
        private final Reason reason;
        private final int statusCode;

        RoutingException(Reason reason, int statusCode, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
            this.statusCode = statusCode;
        }

        public Reason getReason() { return reason; }
        public int getStatusCode() { return statusCode; }
    }
}
