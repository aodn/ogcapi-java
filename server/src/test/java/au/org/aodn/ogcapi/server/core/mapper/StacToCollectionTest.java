package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.TestConfig;
import au.org.aodn.ogcapi.server.core.model.*;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CollectionProperty;
import au.org.aodn.ogcapi.server.core.parser.stac.CQLToStacFilterFactory;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static au.org.aodn.ogcapi.server.BaseTestClass.readResourceFile;

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
        stacToCollection.convert(model, null);

        model = StacCollectionModel
                .builder()
                .extent(new ExtentModel(new ArrayList<>(), null))
                .build();

        // Empty bbox no issue
        stacToCollection.convert(model, null);
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
        var link1 = LinkModel.builder()
                .rel("related")
                .href("https://example.com/data")
                .type("text/html")
                .title("Data Link")
                .aiGroup("data-access")
                .description("description")
                .build();
        var link2 = LinkModel.builder()
                .rel("self")
                .href("https://example.com/self")
                .type("application/json")
                .title("Self Link")
                .aiGroup("ai-group")
                .description("description")
                .build();
        var theme = ThemeModel.builder()
                .scheme("scheme")
                .concepts(Collections.singletonList(
                        ConceptModel.builder().id("id").url("url").description("description").title("title").build()
                ))
                .build();
        var citationString = "{\"suggestedCitation\":\"this is suggested Citation\",\"useLimitations\":[\"this is useLimitations1\",\"this is useLimitations2\"],\"otherConstraints\":[\"this is otherConstraints1\",\"this is otherConstraints2\"]}";
        var statement = "This is the statement of this record";
        var datasetGroup = "group_test";
        var aiDescription = "AI-generated description for testing";

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
                                .datasetGroup(datasetGroup)
                                .aiDescription(aiDescription)
                                .build()
                )
                .license("Attribution 4.0")
                .contacts(Collections.singletonList(contact))
                .links(Arrays.asList(link1, link2))
                .themes(Collections.singletonList(theme))
                .citation(citationString)
                .build();

        // Should not throw null pointer
        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model, null);
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
        Assertions.assertEquals("Attribution 4.0", collection.getProperties().get(CollectionProperty.license));
        Assertions.assertEquals("creation date", collection.getProperties().get(CollectionProperty.creation));
        Assertions.assertEquals("revision date", collection.getProperties().get(CollectionProperty.revision));
        Assertions.assertEquals(datasetGroup, collection.getProperties().get(CollectionProperty.datasetGroup));
        Assertions.assertEquals(aiDescription, collection.getProperties().get(CollectionProperty.aiDescription));
        Assertions.assertNotNull(collection.getLinks());
        Assertions.assertEquals(2, collection.getLinks().size());
    }

    @Test
    public void verifyConvertWorks1() throws IOException, CQLException {
        String json = readResourceFile("classpath:databag/0c681199-06cd-435c-9468-be6998799b1f.json");
        StacCollectionModel model = objectMapper.readValue(json, StacCollectionModel.class);
        StacToCollectionsImpl impl = new StacToCollectionsImpl();

        CQLToStacFilterFactory factory = CQLToStacFilterFactory.builder()
                .cqlCrsType(CQLCrsType.EPSG4326)
                .build();

        ElasticSearch.SearchResult<StacCollectionModel> result = new ElasticSearch.SearchResult<>();
        result.setCollections(List.of(model));

        // Should not throw any exception
        impl.convert(
                result,
                CompilerUtil.parseFilter(Language.CQL, "score>=1.5 AND INTERSECTS(geometry,POLYGON ((104 -43, 163 -43, 163 -8, 104 -8, 104 -43)))", factory)
        );
    }
}
