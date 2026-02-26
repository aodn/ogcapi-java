package au.org.aodn.ogcapi.server.core.util;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class DatetimeUtils {
    private static final Pattern MM_YYYY_PATTERN = Pattern.compile("^(\\d{2})-(\\d{4})$");
    private static final Pattern YYYY_MM_DD_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final String NON_SPECIFIED_DATE = "non-specified";

    private DatetimeUtils() {
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
}
