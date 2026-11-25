package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.stac.model.CitationModel;
import au.org.aodn.stac.model.StacCollectionModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DownloadCitationServiceTest {

    private static final String UUID = "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc";
    private static final String TITLE = "Test Ocean Data Collection";
    private static final String METADATA_LINK = "https://portal.example.test/details/" + UUID;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Search search;

    private DownloadCitationService service;

    @BeforeEach
    public void setUp() {
        // 19 Nov in Sydney, still 18 Nov in UTC
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T23:30:00Z"), ZoneOffset.UTC);
        service = new DownloadCitationService(search, clock);
    }

    @Test
    public void testLookupFillsCitationPlaceholders() throws JsonProcessingException {
        // Placeholders are case-insensitive, and both year spellings are filled
        String citation = "IMOS [year-of-data-download], [Title], [data-access-URL], accessed [date-of-access]. "
                + "Data downloaded [YEAR-OF-DATA-DOWNLOADED].";
        givenRecord(StacCollectionModel.builder().uuid(UUID).title(TITLE)
                .citation(citationJson(citation, null, null)).build());

        DownloadCitationService.DownloadCitation result = service.lookup(UUID, METADATA_LINK);

        assertEquals("IMOS 2026, Test Ocean Data Collection, "
                        + METADATA_LINK + "?tab=summary, accessed 18-Nov-2026. "
                        + "Data downloaded 2026.",
                result.suggestedCitation());
    }

    @Test
    public void testLookupFillsUrlAndDownloadDateInBothAttributionBlocks() throws JsonProcessingException {
        String citation = "Before 1 July 2024: UNSW Sydney [year-of-data-download]. "
                + "Downloaded from [url] on [date-of-download].\n\n"
                + "After 1 July 2024: UNSW Sydney; IMOS [year-of-data-download]. "
                + "Downloaded from [URL] on [Date-of-Download].";
        givenRecord(StacCollectionModel.builder().uuid(UUID).title(TITLE)
                .citation(citationJson(citation, null, null)).build());

        String url = METADATA_LINK + "?tab=summary";
        assertEquals("Before 1 July 2024: UNSW Sydney 2026. "
                        + "Downloaded from " + url + " on 18-Nov-2026.\n\n"
                        + "After 1 July 2024: UNSW Sydney; IMOS 2026. "
                        + "Downloaded from " + url + " on 18-Nov-2026.",
                service.lookup(UUID, METADATA_LINK).suggestedCitation());
    }

    @Test
    public void testLookupWithoutMetadataLinkLeavesAccessUrlEmpty() throws JsonProcessingException {
        givenRecord(StacCollectionModel.builder().uuid(UUID).title(TITLE)
                .citation(citationJson("Accessed at [data-access-url].", null, null)).build());

        assertEquals("Accessed at .", service.lookup(UUID, null).suggestedCitation());
    }

    @Test
    public void testLookupKeepsSpecialCharactersInTitle() throws JsonProcessingException {
        // A $ or \ in the title is plain text, not a regex group reference
        String title = "Price $1 and C:\\data";
        givenRecord(StacCollectionModel.builder().uuid(UUID).title(title)
                .citation(citationJson("[title]", null, null)).build());

        assertEquals(title, service.lookup(UUID, METADATA_LINK).suggestedCitation());
    }

    @Test
    public void testLookupWithoutCitationGivesEmptyValues() {
        givenRecord(StacCollectionModel.builder().uuid(UUID).title(TITLE).license("CC BY 4.0").build());

        DownloadCitationService.DownloadCitation result = service.lookup(UUID, METADATA_LINK);

        // An empty citation is sent as "Suggested Citation not available" by RestServices
        assertEquals("", result.suggestedCitation());
        assertEquals(List.of(), result.usageConstraints());
    }

    @Test
    public void testLookupDropsEmptyLicenceAndDuplicateConstraints() throws JsonProcessingException {
        List<String> useLimitations = Arrays.asList("Use with care", "", "   ", null);
        List<String> otherConstraints = List.of(
                " Creative  Commons Attribution 4.0 ",
                "use  WITH care ",
                "Credit CSIRO",
                "credit csiro");
        givenRecord(StacCollectionModel.builder().uuid(UUID).title(TITLE)
                .license("creative commons attribution 4.0")
                .citation(citationJson("A citation", useLimitations, otherConstraints)).build());

        // Use limitations come first, the first of each duplicate is kept as written
        assertEquals(List.of("Use with care", "Credit CSIRO"), service.lookup(UUID, METADATA_LINK).usageConstraints());
    }

    @Test
    public void testLookupWithoutLicenceKeepsAllConstraints() throws JsonProcessingException {
        givenRecord(StacCollectionModel.builder().uuid(UUID).title(TITLE)
                .citation(citationJson(null, List.of("Use with care"), List.of("Credit CSIRO"))).build());

        assertEquals(List.of("Use with care", "Credit CSIRO"), service.lookup(UUID, METADATA_LINK).usageConstraints());
    }

    @Test
    public void testLookupUuidNotFoundThrows() {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of());
        when(search.searchCollections(UUID)).thenReturn(result);

        assertThrows(NoSuchElementException.class, () -> service.lookup(UUID, METADATA_LINK));
    }

    @Test
    public void testLookupMissingUuidThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.lookup(null, METADATA_LINK));
        assertThrows(IllegalArgumentException.class, () -> service.lookup(" ", METADATA_LINK));
    }

    private void givenRecord(StacCollectionModel model) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of(model));
        when(search.searchCollections(UUID)).thenReturn(result);
    }

    // Elasticsearch stores sci:citation as a JSON string
    private String citationJson(String suggestedCitation, List<String> useLimitations, List<String> otherConstraints)
            throws JsonProcessingException {
        return objectMapper.writeValueAsString(new CitationModel(suggestedCitation, useLimitations, otherConstraints));
    }
}
