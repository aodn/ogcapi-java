package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.TestConfig;
import au.org.aodn.ogcapi.server.core.model.*;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.stac.model.AssetModel;
import au.org.aodn.stac.model.CitationModel;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ContactsAddressModel;
import au.org.aodn.stac.model.ContactsModel;
import au.org.aodn.stac.model.ContactsPhoneModel;
import au.org.aodn.stac.model.ExtentModel;
import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.stac.model.SummariesModel;
import au.org.aodn.stac.model.ThemesModel;
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
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static au.org.aodn.ogcapi.server.BaseTestClass.readResourceFile;

@SpringBootTest(classes = {TestConfig.class, Config.class, RestTemplateAutoConfiguration.class, JacksonAutoConfiguration.class, CacheAutoConfiguration.class})
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
    public void verifyTemporalIntervalParsing() {
        StacToCollection stacToCollection = new StacToCollectionImpl();

        // Two intervals: one bounded, one with an unbounded end (ongoing).
        // Mirrors STAC's [start, null] convention for "from start, ongoing".
        List<List<String>> temporal = Arrays.asList(
                Arrays.asList("2020-01-01T00:00:00Z", "2020-12-31T23:59:59Z"),
                Arrays.asList("2021-06-15T12:00:00Z", null)
        );

        StacCollectionModel model = StacCollectionModel
                .builder()
                .extent(new ExtentModel(Collections.singletonList(Collections.emptyList()), temporal))
                .build();

        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model, null);

        List<List<Date>> interval = collection.getExtent().getTemporal().getInterval();
        Assertions.assertNotNull(interval);
        Assertions.assertEquals(2, interval.size());

        Assertions.assertEquals(Date.from(Instant.parse("2020-01-01T00:00:00Z")), interval.get(0).get(0));
        Assertions.assertEquals(Date.from(Instant.parse("2020-12-31T23:59:59Z")), interval.get(0).get(1));

        Assertions.assertEquals(Date.from(Instant.parse("2021-06-15T12:00:00Z")), interval.get(1).get(0));
        Assertions.assertNull(interval.get(1).get(1), "Unbounded interval end must remain null");
    }

    @Test
    public void verifyTemporalIntervalParsingPreservesNullInnerList() {
        StacToCollection stacToCollection = new StacToCollectionImpl();

        // A null inner list should be preserved as null (not flattened or skipped).
        List<List<String>> temporal = new ArrayList<>();
        temporal.add(null);
        temporal.add(Arrays.asList("2022-03-01T00:00:00Z", "2022-03-31T23:59:59Z"));

        StacCollectionModel model = StacCollectionModel
                .builder()
                .extent(new ExtentModel(Collections.singletonList(Collections.emptyList()), temporal))
                .build();

        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model, null);

        List<List<Date>> interval = collection.getExtent().getTemporal().getInterval();
        Assertions.assertNotNull(interval);
        Assertions.assertEquals(2, interval.size());
        Assertions.assertNull(interval.get(0), "Null inner interval list must be preserved as null");
        Assertions.assertEquals(Date.from(Instant.parse("2022-03-01T00:00:00Z")), interval.get(1).get(0));
        Assertions.assertEquals(Date.from(Instant.parse("2022-03-31T23:59:59Z")), interval.get(1).get(1));
    }

    @Test
    public void verifyTemporalIntervalParsingNormalisesNonZOffset() {
        StacToCollection stacToCollection = new StacToCollectionImpl();

        // Instant.parse accepts RFC 3339 offsets beyond 'Z' and normalises to UTC,
        // so 00:00+10:00 lands at the previous day 14:00Z.
        List<List<String>> temporal = Collections.singletonList(
                Arrays.asList("2020-01-01T00:00:00+10:00", "2020-12-31T23:59:59+10:00")
        );

        StacCollectionModel model = StacCollectionModel
                .builder()
                .extent(new ExtentModel(Collections.singletonList(Collections.emptyList()), temporal))
                .build();

        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model, null);
        List<List<Date>> interval = collection.getExtent().getTemporal().getInterval();

        Assertions.assertEquals(Date.from(Instant.parse("2019-12-31T14:00:00Z")), interval.get(0).get(0));
        Assertions.assertEquals(Date.from(Instant.parse("2020-12-31T13:59:59Z")), interval.get(0).get(1));
    }

    @Test
    public void verifyAddingPropertyWorks() {
        StacToCollection stacToCollection = new StacToCollectionImpl();

        List<String> credits = Arrays.asList("credit1", "credit2");
        var address = ContactsAddressModel.builder()
                .city("city")
                .country("country")
                .postalCode("postalCode")
                .administrativeArea("administrativeArea")
                .deliveryPoint(Arrays.asList("deliveryPoint1", "deliveryPoint2"))
                .build();
        var link = LinkModel.builder().rel("rel").href("href").type("type").title("title").build();
        var contact = ContactsModel.builder()
                .addresses(Collections.singletonList(address))
                .name("name")
                .organization("organization")
                .roles(Collections.singletonList("roles"))
                .emails(Arrays.asList("email1", "email2"))
                .links(Collections.singletonList(link))
                .phones(Collections.singletonList(ContactsPhoneModel.builder().value("value").build()))
                .build();
        var link1 = LinkModel.builder()
                .rel("related")
                .href("https://example.com/data")
                .type("text/html")
                .title("Data Link")
                .aiGroup("data-access")
                .aiRole(List.of("download"))
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
        var theme = ThemesModel.builder()
                .scheme("scheme")
                .concepts(Collections.singletonList(
                        ConceptModel.builder().id("id").url("url").description("description").title("title").build()
                ))
                .build();
        var asset = AssetModel.builder()
                .role(AssetModel.Role.SUMMARY)
                .href("/collections/test-uuid/items/summary")
                .type("application/x-zarr")
                .title("vessel_satellite_radiance_derived_product.zarr")
                .build();
        Map<String, AssetModel> assets = new HashMap<>();
        assets.put("vessel_satellite_radiance_derived_product.zarr", asset);
        var citationString = "{\"suggestedCitation\":\"this is suggested Citation\",\"useLimitations\":[\"this is useLimitations1\",\"this is useLimitations2\"],\"otherConstraints\":[\"this is otherConstraints1\",\"this is otherConstraints2\"]}";
        var statement = "This is the statement of this record";
        var datasetGroup = List.of("group_test");
        var aiDescription = "AI-generated description for testing";

        Map<String, String> scope = new HashMap<>();
        scope.put("code", "document");
        scope.put("name", "IMOS publication");

        List<String> parameterVocabs = Arrays.asList("wave", "temperature");
        List<String> platformVocabs = Arrays.asList("vessel", "satellite");
        List<String> organisationVocabs = Arrays.asList("IMOS", "AODN");
        List<String> aiPlatformVocabs = Arrays.asList("ai-vessel", "ai-satellite");
        String datasetProvider = "IMOS";

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
                                .scope(scope)
                                .parameterVocabs(parameterVocabs)
                                .platformVocabs(platformVocabs)
                                .organisationVocabs(organisationVocabs)
                                .aiPlatformVocabs(aiPlatformVocabs)
                                .datasetProvider(datasetProvider)
                                .build()
                )
                .license("Attribution 4.0")
                .contacts(Collections.singletonList(contact))
                .assets(assets)
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
        Assertions.assertEquals("document",
                ((Map<String, String>) collection.getProperties().get(CollectionProperty.scope)).get("code"));
        Assertions.assertEquals(parameterVocabs, collection.getProperties().get(CollectionProperty.parameterVocabs));
        Assertions.assertEquals(platformVocabs, collection.getProperties().get(CollectionProperty.platformVocabs));
        Assertions.assertEquals(organisationVocabs, collection.getProperties().get(CollectionProperty.organisationVocabs));
        Assertions.assertEquals(aiPlatformVocabs, collection.getProperties().get(CollectionProperty.aiPlatformVocabs));
        Assertions.assertEquals(datasetProvider, collection.getProperties().get(CollectionProperty.datasetProvider));
        Assertions.assertNotNull(collection.getLinks());
        Assertions.assertEquals(3, collection.getLinks().size());

        ExtendedLink convertedLink1 = (ExtendedLink) collection.getLinks().stream()
                .filter(l -> "related".equals(l.getRel()))
                .findFirst()
                .orElseThrow();

        Assertions.assertEquals(List.of("download"), convertedLink1.getAiRole());
    }

    @Test
    public void verifyNewSummariesFieldsGuardsSkipEmptyAndNullValues() {
        StacToCollection stacToCollection = new StacToCollectionImpl();

        StacCollectionModel model = StacCollectionModel
                .builder()
                .summaries(
                        SummariesModel
                                .builder()
                                .platformVocabs(Collections.emptyList())
                                .organisationVocabs(null)
                                .aiPlatformVocabs(Collections.emptyList())
                                .datasetProvider(null)
                                .build()
                )
                .build();

        ExtendedCollection collection = (ExtendedCollection) stacToCollection.convert(model, null);

        Assertions.assertFalse(collection.getProperties().containsKey(CollectionProperty.platformVocabs));
        Assertions.assertFalse(collection.getProperties().containsKey(CollectionProperty.organisationVocabs));
        Assertions.assertFalse(collection.getProperties().containsKey(CollectionProperty.aiPlatformVocabs));
        Assertions.assertFalse(collection.getProperties().containsKey(CollectionProperty.datasetProvider));
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
