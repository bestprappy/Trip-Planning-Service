package com.navio.tripplanningservice.support;

/**
 * Column length guards for {@code trip.block_item}.
 *
 * <p>Charger and place metadata is supplied by external providers and is not length bounded at the
 * source, so every write path clamps it here. Without this a single long provider string fails the
 * insert with a {@code DataIntegrityViolationException} and loses the whole planner autosave.
 */
public final class BlockItemTextLimits {

    public static final int TITLE = 255;
    public static final int NAME = 255;
    public static final int ADDRESS = 500;
    public static final int OPENING_HOURS = 255;
    public static final int PRICE = 255;
    public static final int OPERATOR_NAME = 255;
    public static final int PLACE_ID = 255;
    public static final int STATION_ID = 255;

    private BlockItemTextLimits() {
    }

    /**
     * Returns {@code value} shortened to {@code maxLength} characters, or {@code null} when the
     * value is absent. Truncation is silent because a slightly clipped label is always preferable
     * to failing the user's save.
     */
    public static String clamp(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
