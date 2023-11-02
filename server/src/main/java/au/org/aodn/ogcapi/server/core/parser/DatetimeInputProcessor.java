package au.org.aodn.ogcapi.server.core.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/* leave there, may need later for calling the endpoint using datetime param e.g ?datetime=2020-01-01T00:00:00Z/2020-01-02T00:00:00Z */
public class DatetimeInputProcessor {
    public static String[] parseDateOrInterval(String input) {
        String[] result = new String[2];

        String dateTimeString;

        if (input.contains(" ")) {
            dateTimeString = input.replace(" ", "+");
        } else {
            dateTimeString = input;
        }

        if (dateTimeString.contains("/")) {
            // It's an interval
            String[] parts = dateTimeString.split("/");
            if (parts[0].equals("..")) {
                // Open interval with open start
                result[0] = "1870-01-01T00:00:00Z";
                result[1] = parts[1];
            } else if (parts[1].equals("..")) {
                // Open interval with open end
                result[0] = parts[0];
                result[1] = getCurrentDateTime();
            } else {
                // Closed interval
                result[0] = parts[0];
                result[1] = parts[1];
            }
        } else {
            // It's a single date-time
            result[0] = dateTimeString;
            result[1] = dateTimeString;
        }

        return result;
    }

    private static String getCurrentDateTime() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }
}
