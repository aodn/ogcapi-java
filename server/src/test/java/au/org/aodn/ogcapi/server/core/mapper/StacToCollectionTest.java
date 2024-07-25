package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.TestConfig;
import au.org.aodn.ogcapi.server.core.model.*;
import au.org.aodn.ogcapi.server.core.model.enumeration.CollectionProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SpringBootTest(classes = {TestConfig.class, Config.class, JacksonAutoConfiguration.class, CacheAutoConfiguration.class})
public class StacToCollectionTest {

    @Autowired
    protected ObjectMapper objectMapper;

    @Test
    public void verifyBBoxEmptyOrNullWorks() {
        StacToCollection stacToCollection = new StacToCollectionImpl();

        StacCollectionModel model = StacCollectionModel
                .builder()
                .extent(new ExtentModel(null, null))
                .build();

        // Should not throw null pointer
        stacToCollection.convert(model);

        model = StacCollectionModel
                .builder()
                .extent(new ExtentModel(new ArrayList<>(), null))
                .build();

        // Empty bbox no issue
        stacToCollection.convert(model);
    }

    @Test
    public void verifyAddingPropertyWorks() {
        StacToCollection stacToCollection = new StacToCollectionImpl();

        List<String> credits = Arrays.asList("credit1", "credit2");
        var address = AddressModel.builder()
                .city("city")
                .country("country")
                .postalCode("postalCode")
                .administrativeArea("administrativeArea")
                .deliveryPoint(Arrays.asList("deliveryPoint1", "deliveryPoint2"))
                .build();
        var link = LinkModel.builder().rel("rel").href("href").type("type").title("title").build();
        var contact = ContactModel.builder()
                .addresses(Collections.singletonList(address))
                .name("name")
                .organization("organization")
                .roles(Collections.singletonList("roles"))
                .emails(Arrays.asList("email1", "email2"))
                .links(Collections.singletonList(link))
                .phones(Collections.singletonList(InfoModel.builder().value("value").build())
                ).build();
        var theme = ThemeModel.builder()
                .scheme("scheme")
                .description("description")
                .title("title")
                .concepts(Collections.singletonList(ConceptModel.builder().id("id").url("url").build()))
                .build();
        var citationString = "{\"suggestedCitation\":\"this is suggested Citation\",\"useLimitations\":[\"this is useLimitations1\",\"this is useLimitations2\"],\"otherConstraints\":[\"this is otherConstraints1\",\"this is otherConstraints2\"]}";
        var statement = "This is the statement of this record";
        var license = "{\"title\":\"Attribution 4.0\",\"url\":\"https://creativecommons.org/licenses/by/4.0/\",\"licenseGraphic\":\"https://i.creativecommons.org/l/by/4.0/88x31.png\"}";

        StacCollectionModel model = StacCollectionModel
                .builder()
                .summaries(
                        SummariesModel
                                .builder()
                                .score(0)
                                .status("Completed")
                                .credits(credits)
                                .creation("creation date")
                                .revision("revision date")
                                .statement(statement)
                                .build()
                )
                .license(license)
                .contacts(Collections.singletonList(contact))
                .themes(Collections.singletonList(theme))
                .citation(citationString)
                .build();

        // Should not throw null pointer
        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model);
        Assertions.assertEquals("Completed", collection.getProperties().get(CollectionProperty.status));
        Assertions.assertEquals(credits, collection.getProperties().get(CollectionProperty.credits));
        Assertions.assertEquals(Collections.singletonList(contact), collection.getProperties().get(CollectionProperty.contacts));
        Assertions.assertEquals(Collections.singletonList(theme), collection.getProperties().get(CollectionProperty.themes));
        Assertions.assertInstanceOf(CitationModel.class, collection.getProperties().get(CollectionProperty.citation));
        var citationToCheck = (CitationModel) collection.getProperties().get(CollectionProperty.citation);
        Assertions.assertEquals("this is suggested Citation", citationToCheck.getSuggestedCitation());
        Assertions.assertEquals(Arrays.asList("this is useLimitations1", "this is useLimitations2"), citationToCheck.getUseLimitations());
        Assertions.assertEquals(Arrays.asList("this is otherConstraints1", "this is otherConstraints2"), citationToCheck.getOtherConstraints());
        Assertions.assertEquals(statement, collection.getProperties().get(CollectionProperty.statement));
        var licenseToCheck = (LicenseModel) collection.getProperties().get(CollectionProperty.license);
        Assertions.assertEquals("Attribution 4.0", licenseToCheck.getTitle());
        Assertions.assertEquals("https://creativecommons.org/licenses/by/4.0/", licenseToCheck.getUrl());
        Assertions.assertEquals("https://i.creativecommons.org/l/by/4.0/88x31.png", licenseToCheck.getLicenseGraphic());
        Assertions.assertEquals("creation date", collection.getProperties().get(CollectionProperty.creation));
        Assertions.assertEquals("revision date", collection.getProperties().get(CollectionProperty.revision));
    }
}
