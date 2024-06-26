package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.*;
import au.org.aodn.ogcapi.server.core.model.enumeration.CollectionProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class StacToCollectionTest {

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

        StacCollectionModel model = StacCollectionModel
                .builder()
                .summaries(
                        SummariesModel
                                .builder()
                                .score(0)
                                .status("Completed")
                                .credits(credits)
                                .build()
                )
                .contacts(Collections.singletonList(contact))
                .themes(Collections.singletonList(theme))
                .build();

        // Should not throw null pointer
        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model);
        Assertions.assertEquals("Completed", collection.getProperties().get(CollectionProperty.status));
        Assertions.assertEquals(credits, collection.getProperties().get(CollectionProperty.credits));
        Assertions.assertEquals(Collections.singletonList(contact), collection.getProperties().get(CollectionProperty.contacts));
        Assertions.assertEquals(Collections.singletonList(theme), collection.getProperties().get(CollectionProperty.themes));
    }
}
