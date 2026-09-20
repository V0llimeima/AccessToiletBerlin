package de.bht.accesstoilet;

/** Main-thread state that rejects duplicate requests and atomically accepts complete results. */
public final class RoutingSession {
    private boolean requestRunning;
    private RouteResult activeRoute;

    public boolean beginRequest(boolean hasValidLocation) {
        if (!hasValidLocation || requestRunning) return false;
        requestRunning = true;
        return true;
    }

    public boolean isRequestRunning() { return requestRunning; }
    public RouteResult getActiveRoute() { return activeRoute; }

    public void complete(RouteResult result) {
        if (!requestRunning) return;
        activeRoute = java.util.Objects.requireNonNull(result);
        requestRunning = false;
    }

    public void fail() {
        requestRunning = false;
    }

    public boolean clearForFilterChange() {
        boolean hadRoute = activeRoute != null;
        requestRunning = false;
        activeRoute = null;
        return hadRoute;
    }

    public void clear() {
        requestRunning = false;
        activeRoute = null;
    }
}
