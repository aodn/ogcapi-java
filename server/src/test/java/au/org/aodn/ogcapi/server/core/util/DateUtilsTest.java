package au.org.aodn.ogcapi.server.core.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static au.org.aodn.ogcapi.server.core.util.DateUtils.parseDate;
import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    @Test
    void testParseDateWithValidIsoFormat() {
        String date = "2023-10-01";
        Optional<LocalDate> result = parseDate(date);
        assertTrue(result.isPresent());
        assertEquals(LocalDate.of(2023, 10, 1), result.get());
    }

    @Test
    void testParseDateWithValidCustomFormat() {
        String date1 = "10-10-2023";
        String date2 = "10/01/2023";
        String date3 = "2023/10/01";

        Optional<LocalDate> result1 = parseDate(date1);
        Optional<LocalDate> result2 = parseDate(date2);
        Optional<LocalDate> result3 = parseDate(date3);
        assertTrue(result1.isPresent());
        assertEquals(LocalDate.of(2023, 10, 10), result1.get());
        assertTrue(result2.isPresent());
        assertEquals(LocalDate.of(2023, 1, 10), result2.get());
        assertTrue(result3.isPresent());
        assertEquals(LocalDate.of(2023, 10, 1), result3.get());
    }


    @Test
    void testParseDateWithEmptyString() {
        String date = "";
        Optional<LocalDate> result = parseDate(date);
        assertFalse(result.isPresent());
    }

    @Test
    void testParseDateWithNull() {
        String date = null;
        Optional<LocalDate> result = parseDate(date);
        assertFalse(result.isPresent());
    }

    @Test
    void testSplitDateRangeByMonth() {
        String startDate = "2000-04-15";
        String endDate = "2023-03-21";
        var result = DateUtils.splitDateRangeByMonth(startDate, endDate);
        assertEquals(276, result.size(), "Size correct");
        assertArrayEquals(new String[]{"2000-04-15", "2000-04-30"}, result.get(0), "First month");
        assertArrayEquals(new String[]{"2023-03-01", "2023-03-21"}, result.get(275), "Last month");
        assertArrayEquals(new String[]{"2000-05-01", "2000-05-31"}, result.get(1), "normal month");


    }

    @Test
    void testSplitDateRangeIntoMonths() {
        String startDate = "01-2020";
        String endDate = "03-2023";
        var result = DateUtils.splitDateRangeIntoMonths(startDate, endDate);
        assertEquals(39, result.size(), "Size correct");
        assertEquals("01-2020", result.get(0), "First month");
        assertEquals("03-2023", result.get(38), "Last month");
    }
}