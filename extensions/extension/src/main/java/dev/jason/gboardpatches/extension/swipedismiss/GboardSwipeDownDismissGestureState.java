package dev.jason.gboardpatches.extension.swipedismiss;

final class GboardSwipeDownDismissGestureState {
    static final float TRIGGER_DISTANCE_DP = 48f;
    static final float HORIZONTAL_REJECTION_DP = 24f;
    static final float DIRECTION_RATIO = 1.2f;

    private boolean tracking;
    private boolean consumed;
    private float startX;
    private float startY;

    void begin(boolean inHeader, float x, float y) {
        tracking = inHeader;
        consumed = false;
        startX = x;
        startY = y;
    }

    boolean move(float x, float y, float density) {
        if (!tracking || consumed) {
            return false;
        }
        float dx = x - startX;
        float dy = y - startY;
        float horizontal = Math.abs(dx);
        float safeDensity = density > 0f ? density : 1f;
        if (horizontal >= HORIZONTAL_REJECTION_DP * safeDensity && horizontal > Math.max(0f, dy)) {
            tracking = false;
            return false;
        }
        if (dy >= TRIGGER_DISTANCE_DP * safeDensity && dy >= horizontal * DIRECTION_RATIO) {
            consumed = true;
            return true;
        }
        return false;
    }

    boolean isConsumed() {
        return consumed;
    }

    void reset() {
        tracking = false;
        consumed = false;
    }
}
