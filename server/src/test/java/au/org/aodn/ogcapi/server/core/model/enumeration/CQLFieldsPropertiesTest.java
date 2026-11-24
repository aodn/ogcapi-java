package au.org.aodn.ogcapi.server.core.model.enumeration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CQLFieldsPropertiesTest {

    // The properties requested by the portal SEO bulk request
    private static final List<String> SEO_PROPERTIES = List.of(
            "id", "title", "description", "bbox", "temporal", "themes", "creation",
            "revision", "citation", "license", "dataset_provider", "links",
            "statement", "contacts", "ai_description");

    @Test
    public void seoPropertiesAreAllValid() {
        assertTrue(CQLFields.findInvalidEnum(SEO_PROPERTIES).isEmpty());
    }

    @Test
    public void unknownPropertyIsInvalid() {
        assertEquals(List.of("not_a_field"),
                CQLFields.findInvalidEnum(List.of("contacts", "not_a_field")));
    }

    @Test
    public void contactsAndAiDescriptionMapToStacSourceFields() {
        assertEquals(List.of("contacts"), CQLFields.contacts.getDisplayField());
        assertEquals(List.of("summaries.ai:description"), CQLFields.ai_description.getDisplayField());
    }
}
