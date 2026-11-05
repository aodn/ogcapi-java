package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.model.enumeration.DasSiteProduct;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Each DAS feature id must reach the service with its own product and site parameter. No Spring
 * context, so this runs without the Elasticsearch container RestApiTest needs.
 */
public class RestApiDasRoutingTest {

    private static final String START = "2024-01-01T00:00:00Z";
    private static final String END = "2024-01-02T00:00:00Z";

    private RestApi restApi;

    @BeforeEach
    public void setUp() {
        restApi = new RestApi();
        restApi.featuresService = mock(RestServices.class);
    }

    private static FeatureRequest request() {
        return FeatureRequest.builder().datetime(START + "/" + END).waveBuoy("BUOY-1").mooring("MOORING-1").build();
    }

    @ParameterizedTest
    @CsvSource({
            "wave_buoys_between_dates, WAVE_BUOY",
            "moorings_between_dates, MOORING",
            "mooring_estuarine_water_quality_between_dates, MOORING_ESTUARINE_WATER_QUALITY"
    })
    public void testBetweenDatesRouting(String featureId, DasSiteProduct product) {
        restApi.getFeature("any", featureId, request());

        verify(restApi.featuresService).getSitesBetweenDates(product, START, END);
    }

    @ParameterizedTest
    @CsvSource({
            "wave_buoys_latest_available_date, WAVE_BUOY",
            "moorings_latest_available_date, MOORING",
            "mooring_estuarine_water_quality_latest_available_date, MOORING_ESTUARINE_WATER_QUALITY"
    })
    public void testLatestAvailableDateRouting(String featureId, DasSiteProduct product) {
        restApi.getFeature("any", featureId, request());

        verify(restApi.featuresService).getLatestAvailableDate(product);
    }

    @ParameterizedTest
    @CsvSource({
            "wave_buoy_details_between_dates, WAVE_BUOY, BUOY-1",
            "mooring_details_between_dates, MOORING, MOORING-1",
            "mooring_estuarine_water_quality_details_between_dates, MOORING_ESTUARINE_WATER_QUALITY, MOORING-1"
    })
    public void testDetailsBetweenDatesRouting(String featureId, DasSiteProduct product, String site) {
        restApi.getFeature("any", featureId, request());

        verify(restApi.featuresService).getSiteDetailsBetweenDates(product, START, END, site);
    }
}
