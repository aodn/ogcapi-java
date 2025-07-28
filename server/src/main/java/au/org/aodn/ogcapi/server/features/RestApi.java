package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.api.CollectionsApi;
import au.org.aodn.ogcapi.features.model.*;
import au.org.aodn.ogcapi.features.model.Exception;
import au.org.aodn.ogcapi.server.core.mapper.StacToFeatureCollection;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureId;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.features.model.DownloadableField;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
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
    protected StacToFeatureCollection stacToFeatureCollection;

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
            description = "fetch the feature with id `featureId` in the feature collection with id `collectionId`",
            content = {@Content(
                    mediaType = "application/geo+json",
                    schema = @Schema(
                            implementation = FeatureGeoJSON.class
                    )
            )}
    ), @ApiResponse(
            responseCode = "404",
            description = "The requested resource does not exist on the server. For example, a path parameter had an incorrect value."
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
            @Parameter(in = ParameterIn.PATH,description = "local identifier of a collection",required = true,schema = @Schema)
            @PathVariable("collectionId") String collectionId,
            @Parameter(in = ParameterIn.PATH,description = "local identifier of a feature",required = true,schema = @Schema)
            @PathVariable("featureId") String featureId,
            @Parameter(in = ParameterIn.QUERY, description = "Property to be return" ,schema=@Schema())
            @Valid @RequestParam(value = "properties", required = false) List<String> properties,
            @Parameter(in = ParameterIn.QUERY, description = "Only records that have a geometry that intersects the bounding box are selected. The bounding box is provided as four or six numbers, depending on whether the coordinate reference system includes a vertical axis (height or depth):  * Lower left corner, coordinate axis 1 * Lower left corner, coordinate axis 2 * Minimum value, coordinate axis 3 (optional) * Upper right corner, coordinate axis 1 * Upper right corner, coordinate axis 2 * Maximum value, coordinate axis 3 (optional)  The coordinate reference system of the values is WGS 84 long/lat (http://www.opengis.net/def/crs/OGC/1.3/CRS84) unless a different coordinate reference system is specified in the parameter `bbox-crs`.  For WGS 84 longitude/latitude the values are in most cases the sequence of minimum longitude, minimum latitude, maximum longitude and maximum latitude.  However, in cases where the box spans the antimeridian the first value (west-most box edge) is larger than the third value (east-most box edge).  If the vertical axis is included, the third and the sixth number are the bottom and the top of the 3-dimensional bounding box.  If a record has multiple spatial geometry properties, it is the decision of the server whether only a single spatial geometry property is used to determine the extent or all relevant geometries." ,schema=@Schema())
            @Valid @RequestParam(value = "bbox", required = false) List<BigDecimal> bbox,
            @Parameter(in = ParameterIn.QUERY, description = "Either a date-time or an interval, open or closed. Date and time expressions adhere to RFC 3339. Open intervals are expressed using double-dots.  Examples:  * A date-time: \"2018-02-12T23:20:50Z\" * A closed interval: \"2018-02-12T00:00:00Z/2018-03-18T12:31:12Z\" * Open intervals: \"2018-02-12T00:00:00Z/..\" or \"../2018-03-18T12:31:12Z\"  Only records that have a temporal property that intersects the value of `datetime` are selected.  It is left to the decision of the server whether only a single temporal property is used to determine the extent or all relevant temporal properties." ,schema=@Schema())
            @Valid @RequestParam(value = "datetime", required = false) String datetime,
            @Parameter(in = ParameterIn.QUERY, description = "WFS server URL (required when featureId is 'downloadableFields')" ,schema=@Schema())
            @Valid @RequestParam(value = "serverUrl", required = false) String serverUrl,
            @Parameter(in = ParameterIn.QUERY, description = "WFS type name (required when featureId is 'downloadableFields')" ,schema=@Schema())
            @Valid @RequestParam(value = "layerName", required = false) String layerName) {

        String filter = null;
        if (datetime != null) {
            filter = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(), datetime, filter);
        }

        if (bbox != null) {
            filter = OGCApiService.processBBoxParameter(CQLFields.geometry.name(), bbox, filter);
        }

        // Handle special case for downloadableFields
        if ("downloadableFields".equals(featureId)) {
            if (serverUrl == null || layerName == null) {
                return ResponseEntity.badRequest().build();
            }
            
            return featuresService.getDownloadableFields(serverUrl, layerName);
        }

        try {
            FeatureId fid = FeatureId.valueOf(FeatureId.class, featureId);

            return featuresService.getFeature(
                    collectionId,
                    fid,
                    properties,
                    filter != null ? "filter=" + filter : null
            );
        }
        catch(java.lang.Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
    }

    /**
     *
     * @param collectionId - The collection id
     * @param limit - Limit of result return
     * @param bbox - Bounding box that bounds the result set. In case of multiple bounding box, you need to issue multiple query
     * @param datetime - Start/end time
     * @return - The data that matches the filter criteria
     */
    @Override
    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(String collectionId, Integer limit, List<BigDecimal> bbox, String datetime) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
    /**
     * Hidden effectively disable this REST point because it is common in many places and
     * should not implement it here, @Hidden disable swagger doc too
     * @return - Not implemented
     */
    @Hidden
    @Override
    public ResponseEntity<Collections> getCollections() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
