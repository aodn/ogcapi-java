package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.ExtendedCollection;
import au.org.aodn.ogcapi.server.core.model.ExtentModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.SummariesModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CollectionProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;


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

        StacCollectionModel model = StacCollectionModel
                .builder()
                .summaries(new SummariesModel(0, "Completed"))
                .build();

        // Should not throw null pointer
        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model);
        Assertions.assertEquals("Completed", collection.getProperties().get(CollectionProperty.STATUS));
    }
}
