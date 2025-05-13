package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.enumeration.DateFormatEnum;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DateUtils {

    public static Optional<LocalDate> parseDate(String date) {
        List<String> dateFormats = List.of(
                DateFormatEnum.YYYY_MM_DD_HYPHEN.getValue(),
                DateFormatEnum.DD_MM_YYYY_HYPHEN.getValue(),
                DateFormatEnum.YYYY_MM_DD_SLASH.getValue(),
                DateFormatEnum.DD_MM_YYYY_SLASH.getValue()
                );

        for (String format : dateFormats) {
            try {
                return Optional.of(LocalDate.parse(date, DateTimeFormatter.ofPattern(format)));
            } catch (Exception ignored) {}
        }

        return Optional.empty();
    }


    public static Map<Integer, String[]> splitDateRangeByMonth(String startDateStr, String endDateStr) {
        Optional<LocalDate> start = parseDate(startDateStr);
        Optional<LocalDate> end = parseDate(endDateStr);

        if (start.isPresent() && end.isPresent()) {
            LocalDate startDate = start.get();
            LocalDate endDate = end.get();

            int startYear = startDate.getYear();
            int endYear = endDate.getYear();

            Map<Integer, String[]> dateRanges = new HashMap<>();
            Integer index = 0;

            var formatter = DateTimeFormatter.ofPattern(DateFormatEnum.YYYY_MM_DD_HYPHEN.getValue());
            for (int year = startYear; year <= endYear; year++) {
                for (int month = 1; month <= 12; month++) {

                    // ignore months outside the range
                    if (year == startYear && month < startDate.getMonthValue()) {
                        continue;
                    }
                    if (year == endYear && month > endDate.getMonthValue()) {
                        continue;
                    }

                    // deal with the first and last month
                    if (year == startYear && month == startDate.getMonthValue()) {
                        var childStartDate = startDate.format(formatter);
                        var childEndDate = YearMonth.of(year, month).atEndOfMonth().format(formatter);
                        dateRanges.put(index, new String[]{childStartDate, childEndDate});
                        index++;
                        continue;
                    }
                    if (year == endYear && month == endDate.getMonthValue()) {
                        var childStartDate = YearMonth.of(year, month).atDay(1).format(formatter);
                        var childEndDate = endDate.format(formatter);
                        dateRanges.put(index, new String[]{childStartDate, childEndDate});
                        index++;
                        continue;
                    }

                    // deal with the rest of the months
                    YearMonth yearMonth = YearMonth.of(year, month);
                    var childStartDate = yearMonth.atDay(1).format(formatter);
                    var childEndDate = yearMonth.atEndOfMonth().format(formatter);
                    dateRanges.put(index, new String[]{childStartDate, childEndDate});
                    index++;
                }
            }

            return dateRanges;
        }

        return Map.of();
    }

}