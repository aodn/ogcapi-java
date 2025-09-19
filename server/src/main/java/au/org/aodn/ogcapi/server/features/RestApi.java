package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.api.CollectionsApi;
import au.org.aodn.ogcapi.features.model.*;
import au.org.aodn.ogcapi.features.model.Exception;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureId;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController("FeaturesRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi implements CollectionsApi {

    @Autowired
    protected RestServices featuresService;

    @Autowired
    protected WmsServer wmsServer;

    @Override
    public ResponseEntity<Collection> describeCollection(String collectionId) {
        return featuresService.getCollection(collectionId, null);
    }

    @Hidden
    @Override
    public ResponseEntity<FeatureGeoJSON> getFeature(String collectionId, String featureId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Operation(
            summary = "fetch a single feature",
            description = "Fetch the feature with id `featureId` in the feature collection with id `collectionId`. Use content negotiation to request HTML or GeoJSON. ",
            tags = {"Data"}
    )
    @ApiResponses({@ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved feature data. Response type depends on featureId: for 'downloadableFields' returns list of available fields, for 'summary' returns GeoJSON feature.",
            content = {
                    @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(implementation = FeatureGeoJSON.class)
                    ),
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Object.class, description = "List of DownloadableFieldModel for downloadableFields requests")
                    )
            }
    ), @ApiResponse(
            responseCode = "400",
            description = "Bad request. Missing required parameters (serverUrl or layerName) when featureId is 'downloadableFields'."
    ), @ApiResponse(
            responseCode = "403",
            description = "Forbidden. Access to the specified WFS server is not authorized."
    ), @ApiResponse(
            responseCode = "404",
            description = "The requested resource does not exist on the server. For example, a path parameter had an incorrect value or no downloadable fields found."
    ), @ApiResponse(
            responseCode = "500",
            description = "A server error occurred.",
            content = {@Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = Exception.class
                    )
            )}
    )})
    @RequestMapping(
            value = {"/collections/{collectionId}/items/{featureId}"},
            produces = {"application/geo+json", "text/html", "application/json"},
            method = {RequestMethod.GET}
    )
    ResponseEntity<?> getFeature(
            @Parameter(in = ParameterIn.PATH, description = "local identifier of a collection", required = true, schema = @Schema)
            @PathVariable("collectionId") String collectionId,
            @Parameter(in = ParameterIn.PATH, description = "local identifier of a feature", required = true, schema = @Schema)
            @PathVariable("featureId") String featureId,
            @ParameterObject @Valid FeatureRequest request) {
        FeatureId fid = FeatureId.valueOf(FeatureId.class, featureId);
        switch (fid) {
            case downloadableFields -> {
                if (request.getServerUrl() == null || request.getLayerName() == null) {
                    return ResponseEntity.badRequest().build();
                }
                return featuresService.getDownloadableFields(request.getServerUrl(), request.getLayerName());
            }
            case summary -> {
                String filter = null;

                if (request.getDatetime() != null) {
                    filter = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(), request.getDatetime(), filter);
                }

                if (request.getBbox() != null) {
                    filter = OGCApiService.processBBoxParameter(CQLFields.geometry.name(), request.getBbox(), filter);
                }

                try {
                    return featuresService.getFeature(
                            collectionId,
                            fid,
                            request.getProperties(),
                            filter != null ? "filter=" + filter : null
                    );
                } catch (java.lang.Exception e) {
                    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
                }
            }
            case first_data_available -> {
                return featuresService.getWaveBuoys(collectionId, request.getDatetime());
            }
            case timeseries -> {
                return  featuresService.getWaveBuoyData(collectionId, request.getDatetime(), request.getWaveBuoy());
            }
            case wms_map_feature -> {
                String result = wmsServer.getMapFeatures(collectionId, request);
                return ResponseEntity.ok().body(result);
            }
            default -> {
                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
            }
        }
    }

    /**
     * @param collectionId - The collection id
     * @param limit        - Limit of result return
     * @param bbox         - Bounding box that bounds the result set. In case of multiple bounding box, you need to issue multiple query
     * @param datetime     - Start/end time
     * @return - The data that matches the filter criteria
     */
    @Override
    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(String collectionId, Integer limit, List<BigDecimal> bbox, String datetime) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * Hidden effectively disable this REST point because it is common in many places and
     * should not implement it here, @Hidden disable swagger doc too
     *
     * @return - Not implemented
     */
    @Hidden
    @Override
    public ResponseEntity<Collections> getCollections() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
