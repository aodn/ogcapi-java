package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.stac.model.Citation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeModel {
    protected String scheme;
    protected List<ConceptModel> concepts;

    // This is just a test to make sure the stac model can be used in this module, we can remove it when all the stac models are using the stacmodel from es-indexer
    private void testStacmodelReference() {
        Citation citation = Citation.builder().build();
    }

}
