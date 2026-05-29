package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.stac.model.LinkModel;

import static au.org.aodn.ogcapi.server.core.util.ConstructUtils.constructByJsonString;

public class LinkUtils {
    record TitleWithDescription(String title, String description) {}

    /**
     * The shared {@link LinkModel} from the {@code stacmodel} artifact ships with a plain Lombok
     * {@code setTitle}. Some indexed documents pack {"title": ..., "description": ...} into the
     * single title field, so call this after deserialization to split them back out.
     */
    public static void applyParsedTitle(LinkModel link) {
        if (link == null) {
            return;
        }
        String[] parsed = parseLinkTitleDescription(link.getTitle());
        link.setTitle(parsed[0]);
        if (link.getDescription() == null && parsed[1] != null) {
            link.setDescription(parsed[1]);
        }
    }

    public static String[] parseLinkTitleDescription(String combinedTitle) {
        // if combinedTitle is null or empty, return null for both title and description
        if (combinedTitle == null || combinedTitle.trim().isEmpty()) {
            return new String[]{null, null};
        }
        // Otherwise, try to parse json string
        var titleWithDescription = constructByJsonString(combinedTitle, TitleWithDescription.class);
        if (titleWithDescription.isPresent()) {
            var titleDescription = titleWithDescription.get();

            // return parsed title and description if the combined text has these fields
            if (titleDescription.description() != null) {
                String title = (titleDescription.title() == null || titleDescription.title().trim().isEmpty()) ? null : titleDescription.title().trim();
                String description = titleDescription.description().trim().isEmpty() ? null : titleDescription.description().trim();
                return new String[]{title, description};
            }
            else {
                return new String[]{combinedTitle.trim(), null};
            }
        }

        // if not match, fallback to the original combinedTitle as title with null description
        return new String[]{combinedTitle.trim(), null};
    }
}
