package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.ExtentModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
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
}
