package au.org.aodn.ogcapi.server.core.util;

public class LinkUtils {
    public static String[] parseLinkTitleDescription(String combinedTitle) {
        // if combinedTitle is null, return null for both title and description
        if (combinedTitle == null) {
            return new String[]{null, null};
        }
        // Otherwise, find the last bracket
        int bracketCount = 0;
        for (int i = combinedTitle.length() - 1; i >= 0; i--) {
            if (combinedTitle.charAt(i) == ']') {
                bracketCount++;
            } else if (combinedTitle.charAt(i) == '[') {
                bracketCount--;
                if (bracketCount == 0) {
                    String title = combinedTitle.substring(0, i).trim();
                    String description = combinedTitle.substring(i + 1, combinedTitle.length() - 1).trim();
                    return new String[]{
                            title,
                            description.isEmpty() ? null : description
                    };
                }
            }
        }
        // return null description if no matching bracket found
        return new String[]{combinedTitle, null};
    }
}
