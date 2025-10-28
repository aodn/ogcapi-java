package au.org.aodn.ogcapi.server.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatetimeUtils
 */
public class DatetimeUtilsTest {

    @Test
    public void testValidateAndFormatDate_ValidYYYY_MM_DD() {
        // Test valid YYYY-MM-DD format
        String result = DatetimeUtils.validateAndFormatDate("2023-01-15", true);
        assertEquals("2023-01-15", result);
    }

    @Test
    public void testValidateAndFormatDate_ValidMM_YYYY_StartDate() {
        // Test MM-YYYY format for start date (should return first day of month)
        String result = DatetimeUtils.validateAndFormatDate("01-2023", true);
        assertEquals("2023-01-01", result);
    }

    @Test
    public void testValidateAndFormatDate_ValidMM_YYYY_EndDate() {
        // Test MM-YYYY format for end date (should return last day of month)
        String result = DatetimeUtils.validateAndFormatDate("02-2023", false);
        assertEquals("2023-02-28", result);
    }

    @Test
    public void testValidateAndFormatDate_LeapYear() {
        // Test February in leap year
        String result = DatetimeUtils.validateAndFormatDate("02-2024", false);
        assertEquals("2024-02-29", result);
    }

    @Test
    public void testValidateAndFormatDate_Month31Days() {
        // Test month with 31 days
        String result = DatetimeUtils.validateAndFormatDate("01-2023", false);
        assertEquals("2023-01-31", result);
    }

    @Test
    public void testValidateAndFormatDate_Month30Days() {
        // Test month with 30 days
        String result = DatetimeUtils.validateAndFormatDate("04-2023", false);
        assertEquals("2023-04-30", result);
    }

    @Test
    public void testValidateAndFormatDate_NullDate() {
        // Test null date returns null
        String result = DatetimeUtils.validateAndFormatDate(null, true);
        assertNull(result, "Null date should return null");
    }

    @Test
    public void testValidateAndFormatDate_EmptyString() {
        // Test empty string returns null
        String result = DatetimeUtils.validateAndFormatDate("", true);
        assertNull(result, "Empty string should return null");
    }

    @Test
    public void testValidateAndFormatDate_WhitespaceString() {
        // Test whitespace string returns null
        String result = DatetimeUtils.validateAndFormatDate("   ", false);
        assertNull(result, "Whitespace string should return null");
    }

    @Test
    public void testValidateAndFormatDate_NonSpecified() {
        // Test "non-specified" returns null (case-insensitive)
        String result1 = DatetimeUtils.validateAndFormatDate(DatetimeUtils.NON_SPECIFIED_DATE, true);
        assertNull(result1, "non-specified should return null");

        String result2 = DatetimeUtils.validateAndFormatDate(DatetimeUtils.NON_SPECIFIED_DATE.toUpperCase(), false);
        assertNull(result2, "NON-SPECIFIED should return null");

        String result3 = DatetimeUtils.validateAndFormatDate("Non-Specified", true);
        assertNull(result3, "Non-Specified (mixed case) should return null");
    }

    @Test
    public void testValidateAndFormatDate_InvalidFormat() {
        // Test invalid format throws exception
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DatetimeUtils.validateAndFormatDate("2023/01/15", true);
        });
        assertTrue(exception.getMessage().contains("Date must be in MM-YYYY or YYYY-MM-DD format"));
    }

    @Test
    public void testValidateAndFormatDate_InvalidMonth() {
        // Test invalid month throws exception
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DatetimeUtils.validateAndFormatDate("13-2023", true);
        });
        assertTrue(exception.getMessage().contains("Invalid month in date"));
    }

    @Test
    public void testValidateAndFormatDate_InvalidFormat_Slashes() {
        // Test date with slashes instead of dashes
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DatetimeUtils.validateAndFormatDate("2023/02/15", true);
        });
        assertTrue(exception.getMessage().contains("Date must be in MM-YYYY or YYYY-MM-DD format"));
    }

    @Test
    public void testValidateAndFormatDate_IncompleteDate() {
        // Test incomplete date format
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DatetimeUtils.validateAndFormatDate("2023-01", true);
        });
        assertTrue(exception.getMessage().contains("Date must be in MM-YYYY or YYYY-MM-DD format"));
    }

    @Test
    public void testValidateAndFormatDate_RandomString() {
        // Test random string throws exception
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DatetimeUtils.validateAndFormatDate("random-text", true);
        });
        assertTrue(exception.getMessage().contains("Date must be in MM-YYYY or YYYY-MM-DD format"));
    }
}
