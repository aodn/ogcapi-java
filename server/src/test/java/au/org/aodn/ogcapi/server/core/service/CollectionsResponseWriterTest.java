package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollection;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollections;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionsResponseWriterTest {

    @Test
    void streamedJsonMatchesExtendedCollections() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(
                JsonInclude.Include.NON_NULL,
                JsonInclude.Include.USE_DEFAULTS));

        ExtendedCollection first = new ExtendedCollection();
        first.setId("a");
        first.setTitle("alpha");
        ExtendedCollection second = new ExtendedCollection();
        second.setId("b");
        second.setTitle("beta");

        ExtendedCollections expected = new ExtendedCollections();
        expected.setCollections(List.of(first, second));
        expected.setTotal(2L);
        expected.setSearchAfter(List.of("1.0", "str:b"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var generator = CollectionsResponseWriter.open(output, mapper)) {
            for (Collection collection : List.of(first, second)) {
                generator.writeObject(collection);
            }
            CollectionsResponseWriter.finish(generator, 2L, List.of("1.0", "str:b"));
        }

        assertEquals(mapper.writeValueAsString(expected), output.toString());
    }
}
