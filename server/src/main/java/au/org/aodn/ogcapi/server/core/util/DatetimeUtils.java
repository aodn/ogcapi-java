package au.org.aodn.ogcapi.server.core.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public class DatetimeUtils {
    private static final Pattern MM_YYYY_PATTERN = Pattern.compile("^(\\d{2})-(\\d{4})$");
    private static final Pattern YYYY_MM_DD_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MM-yyyy");
    // Email display format with an explicit UTC indicator; English keeps month names stable across locales.
    private static final DateTimeFormatter DISPLAY_UTC_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss 'UTC'", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);
    public static final String NON_SPECIFIED_DATE = "non-specified";
    // Frontend default lower bound (dateDefault.min); its default upper bound is "now"
    public static final String DEFAULT_MIN_DATE = "1970-01-01";

    private DatetimeUtils() {
    }

    /**
     * A lower bound is "open" (no real start) when it is null/empty/non-specified,
     * or equals the frontend default minimum (1970-01-01).
     */
    public static boolean isDefaultLowerBound(String date) {
        if (date == null || date.trim().isEmpty() || NON_SPECIFIED_DATE.equalsIgnoreCase(date.trim())) {
            return true;
        }
        return DEFAULT_MIN_DATE.equals(date.trim());
    }

    /**
     * An upper bound is "open" (no real end) when it is null/empty/non-specified,
     * or falls on today or later (the frontend default maximum is "now").
     * Avoids an exact today-equality check so it is not fragile across timezones/midnight.
     */
    public static boolean isOpenUpperBound(String date) {
        if (date == null || date.trim().isEmpty() || NON_SPECIFIED_DATE.equalsIgnoreCase(date.trim())) {
            return true;
        }
        try {
            return !LocalDate.parse(date.trim(), ISO_DATE_FORMAT).isBefore(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Format a download/subsetting boundary in UTC for display in notification emails.
     * A date-only value is the inclusive UTC day processed by the backend, displayed
     * at whole-second precision from 00:00:00 UTC through 23:59:59 UTC. A month-only
     * value uses the equivalent boundaries for its first and last days. An ISO-8601
     * timestamp with an offset is converted to the same instant in UTC. This method is
     * deliberately specific to download filters; Tiler date keys are opaque calendar
     * keys and must not be passed through it.
     *
     * @return an explicit UTC timestamp, an empty string for an unspecified value, or
     *         the unmodified input when it is not a supported temporal value
     */
    public static String toDisplayUtcDownloadBoundary(String value, boolean endBoundary) {
        if (value == null || value.trim().isEmpty() || NON_SPECIFIED_DATE.equalsIgnoreCase(value.trim())) {
            return "";
        }

        String trimmed = value.trim();
        try {
            LocalDate date = LocalDate.parse(trimmed, ISO_DATE_FORMAT);
            return DISPLAY_UTC_DATE_TIME_FORMAT.format(endBoundary
                    ? date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                    : date.atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // It may be a month or an ISO-8601 timestamp with an explicit offset.
        }

        try {
            YearMonth month = YearMonth.parse(trimmed, MONTH_YEAR_FORMAT);
            return DISPLAY_UTC_DATE_TIME_FORMAT.format(endBoundary
                    ? month.atEndOfMonth().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                    : month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // It may already be an ISO-8601 timestamp with an explicit offset.
        }

        try {
            return DISPLAY_UTC_DATE_TIME_FORMAT.format(OffsetDateTime.parse(trimmed).toInstant());
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    public static String formatOGCDateTime(String startDate, String endDate) {
        if(startDate == null || startDate.trim().isEmpty()) {
            startDate = "..";
        }

        if(endDate == null || endDate.trim().isEmpty()) {
            endDate = "..";
        }
        return String.format("%s/%s", startDate, endDate);
    }
    /**
     * Validate and format date for WFS CQL filter
     * Handles MM-YYYY to YYYY-MM-DD conversion for temporal queries
     *
     * @param dateInput   Input date string (supports MM-YYYY or YYYY-MM-DD formats)
     * @param isStartDate true for start date (first day of month), false for end date (last day of month)
     * @return Formatted date string in YYYY-MM-DD format, or null if date is not specified
     * @throws IllegalArgumentException if date format is invalid
     */
    public static String validateAndFormatDate(String dateInput, boolean isStartDate) {
        // Handle null, empty, or "non-specified" dates
        if (dateInput == null || dateInput.trim().isEmpty() || NON_SPECIFIED_DATE.equalsIgnoreCase(dateInput.trim())) {
            return null;
        }

        if (MM_YYYY_PATTERN.matcher(dateInput).matches()) {
            String[] parts = dateInput.split("-");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);

            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("Invalid month in date: " + dateInput);
            }

            // Determine the day based on whether it's a start or end date
            int day = isStartDate ? 1 : java.time.YearMonth.of(year, month).lengthOfMonth();

            return String.format("%04d-%02d-%02d", year, month, day);
        } else if (YYYY_MM_DD_PATTERN.matcher(dateInput).matches()) {
            // Validate the date format by attempting to parse it
            try {
                java.time.LocalDate.parse(dateInput, ISO_DATE_FORMAT);
                return dateInput; // Already in correct format
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date format: " + dateInput);
            }
        } else {
            throw new IllegalArgumentException("Date must be in MM-YYYY or YYYY-MM-DD format: " + dateInput);
        }
    }

    /**
     * Parse an ISO-8601 date-time and verify it is at zero offset from UTC — accepts both the 'Z'
     * designator and an explicit "+00:00"/"-00:00" offset, since they are equivalent instants.
     *
     * @param dateTime ISO-8601 date-time string, e.g. "2026-06-16T00:00:00Z" or "2026-06-16T00:00:00+00:00"
     * @return the parsed date-time
     * @throws IllegalArgumentException if the value is not a valid ISO-8601 date-time or is not at zero UTC offset
     */
    public static java.time.OffsetDateTime parseUtcDateTime(String dateTime) {
        java.time.OffsetDateTime parsed;
        try {
            parsed = java.time.OffsetDateTime.parse(dateTime);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Date-time must be a valid ISO-8601 UTC value (e.g. '2026-06-16T00:00:00Z'): " + dateTime);
        }

        if (!parsed.getOffset().equals(java.time.ZoneOffset.UTC)) {
            throw new IllegalArgumentException("Date-time must be at zero UTC offset (e.g. 'Z' or '+00:00'): " + dateTime);
        }
        return parsed;
    }
}
