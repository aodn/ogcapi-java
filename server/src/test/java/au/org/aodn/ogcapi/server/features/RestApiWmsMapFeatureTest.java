package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.configuration.CustomMvcRegistrations;
import au.org.aodn.ogcapi.server.core.exception.GlobalExceptionHandler;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.FeatureInfoResponse;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestApiWmsMapFeatureTest {

    private static final String COLLECTION_ID = "0145df96-3847-474b-8b63-a66f0e03ff54";

    @Mock
    private WmsServer wmsServer;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestServices featuresService = new RestServices();
        ReflectionTestUtils.setField(featuresService, "wmsServer", wmsServer);
        RestApi restApi = new RestApi();
        ReflectionTestUtils.setField(restApi, "featuresService", featuresService);
        mockMvc = MockMvcBuilders.standaloneSetup(restApi)
                .setCustomHandlerMapping(() -> new CustomMvcRegistrations().getRequestMappingHandlerMapping())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapFeatureStaysJsonWhenTheClientAcceptsHtml() throws Exception {
        when(wmsServer.getMapFeatures(eq(COLLECTION_ID), any())).thenReturn(
                FeatureInfoResponse.builder().html("<div class=\"feature\">reef</div>").build());

        mockMvc.perform(get("/api/v1/ogc/collections/{collectionId}/items/wms_map_feature", COLLECTION_ID)
                        .param("layerName", "SeamapAus_VIC_statewide_habitats_2023")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.html").value("<div class=\"feature\">reef</div>"));
    }
}
