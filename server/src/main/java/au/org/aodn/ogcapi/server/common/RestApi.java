package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.common.api.ApiApi;
import au.org.aodn.ogcapi.common.api.ConformanceApi;
import au.org.aodn.ogcapi.common.api.DefaultApi;
import au.org.aodn.ogcapi.common.model.ConfClasses;
import au.org.aodn.ogcapi.common.model.LandingPage;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.features.model.Exception;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollections;
import au.org.aodn.ogcapi.server.core.model.ErrorMessage;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFilterType;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.geotools.filter.text.cql2.CQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController("CommonRestApi")
public class RestApi implements ApiApi, DefaultApi, ConformanceApi {

    @Autowired
    @Qualifier("CommonRestService")
    protected OGCApiService commonService;

    @Autowired
    @Qualifier("TileRestService")
    protected OGCApiService tileService;

    @Autowired
    @Qualifier("FeaturesRestService")
    protected OGCApiService featuresService;

    @Autowired
    protected StacToCollections stacToCollection;

    @Override
    public ResponseEntity<Void> apiGet(String f) {
        switch(OGCMediaTypeMapper.valueOf(f.toLowerCase())) {
            case json: {
                return ResponseEntity
                        .status(HttpStatus.TEMPORARY_REDIRECT)
                        .location(URI.create("v3/api-docs"))
                        .build();
            }
            default: {
                return ResponseEntity
                        .status(HttpStatus.TEMPORARY_REDIRECT)
                        .location(URI.create("swagger-ui/index.html"))
                        .build();
            }
        }
    }

    @Override
    public ResponseEntity<LandingPage> getLandingPage(String f) {
        return null;
    }

    @Override
    public ResponseEntity<ConfClasses> getConformanceDeclaration(String f) {

        switch(OGCMediaTypeMapper.valueOf(f.toLowerCase())) {
            case json: {
                List<String> result = new ArrayList<>();

                // Support the following services
                result.addAll(commonService.getConformanceDeclaration());
                result.addAll(tileService.getConformanceDeclaration());
                result.addAll(featuresService.getConformanceDeclaration());

                return ResponseEntity.ok()
                        .contentType(OGCMediaTypeMapper.json.getMediaType())
                        .body(new ConfClasses().conformsTo(result));
            }
            default: {
                /**
                 * https://opengeospatial.github.io/ogcna-auto-review/19-072.html
                 *
                 * The OGC API — Common Standard does not mandate a specific encoding or format for
                 * representations of resources. However, both HTML and JSON are commonly used encodings for spatial
                 * data on the web. The HTML and JSON requirements classes specify the encoding of resource
                 * representations using:
                 *
                 *     HTML
                 *     JSON
                 *
                 * Neither of these encodings is mandatory. An implementer of the API-Common Standard may decide
                 * to implement other encodings instead of, or in addition to, these two.
                 */
                // TODO: html return
                return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
            }
        }
    }
    /**
     * In ogc api, it defines the same getCollections (/collections) in a couple of places, and it assumed that
     * user will extend the same function with different argument, which cannot be done with openapi directly.
     * Hence, all those getCollections are marked @Hidden and replace by this one.
     *
     * TODO: Need text/html output?
     * @return
     */
    @Operation(summary = "The collections in the dataset", description = "", tags = {"Capabilities"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "The collections shared by this API.  The dataset is organized as one or more feature collections. This resource provides information about and access to the collections.  The response contains the list of collections. For each collection, a link to the items in the collection (path `/collections/{collectionId}/items`, link relation `items`) as well as key information about the collection. This information includes:  * A local identifier for the collection that is unique for the dataset; * A list of coordinate reference systems (CRS) in which geometries may be returned by the server. The first CRS is the default coordinate reference system (the default is always WGS 84 with axis order longitude/latitude); * An optional title and description for the collection; * An optional extent that can be used to provide an indication of the spatial and temporal extent of the collection - typically derived from the data; * An optional indicator about the type of the items in the collection (the default value, if the indicator is not provided, is 'feature').",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Collections.class))),
            @ApiResponse(responseCode = "500",
                    description = "A server error occurred.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))})
    @RequestMapping(value = "/collections",
            produces = {
                    "application/json"
            //        "text/html"
            },
            method = RequestMethod.GET)
    public ResponseEntity<?> getCollections(
            @Parameter(in = ParameterIn.QUERY, description = "Only records that have a geometry that intersects the bounding box are selected. The bounding box is provided as four or six numbers, depending on whether the coordinate reference system includes a vertical axis (height or depth):  * Lower left corner, coordinate axis 1 * Lower left corner, coordinate axis 2 * Minimum value, coordinate axis 3 (optional) * Upper right corner, coordinate axis 1 * Upper right corner, coordinate axis 2 * Maximum value, coordinate axis 3 (optional)  The coordinate reference system of the values is WGS 84 long/lat (http://www.opengis.net/def/crs/OGC/1.3/CRS84) unless a different coordinate reference system is specified in the parameter `bbox-crs`.  For WGS 84 longitude/latitude the values are in most cases the sequence of minimum longitude, minimum latitude, maximum longitude and maximum latitude.  However, in cases where the box spans the antimeridian the first value (west-most box edge) is larger than the third value (east-most box edge).  If the vertical axis is included, the third and the sixth number are the bottom and the top of the 3-dimensional bounding box.  If a record has multiple spatial geometry properties, it is the decision of the server whether only a single spatial geometry property is used to determine the extent or all relevant geometries." ,schema=@Schema())
                @Valid @RequestParam(value = "bbox", required = false) List<BigDecimal> bbox,
            @Parameter(in = ParameterIn.QUERY, description = "Either a date-time or an interval, open or closed. Date and time expressions adhere to RFC 3339. Open intervals are expressed using double-dots.  Examples:  * A date-time: \"2018-02-12T23:20:50Z\" * A closed interval: \"2018-02-12T00:00:00Z/2018-03-18T12:31:12Z\" * Open intervals: \"2018-02-12T00:00:00Z/..\" or \"../2018-03-18T12:31:12Z\"  Only records that have a temporal property that intersects the value of `datetime` are selected.  It is left to the decision of the server whether only a single temporal property is used to determine the extent or all relevant temporal properties." ,schema=@Schema())
                @Valid @RequestParam(value = "datetime", required = false) String datetime,
            @Parameter(in = ParameterIn.QUERY, description = "The optional q parameter supports keyword searching.  Only records whose text fields contain one or more of the specified search terms are selected.  The specific set of text keys/fields/properties of a record to which the q operator is applied is up to the description of the server.   Implementations should, however, apply the q operator to the title, description and keywords keys/fields/properties." ,schema=@Schema())
                @Valid @RequestParam(value = "q", required = false) List<String> q,
            @Parameter(in = ParameterIn.QUERY, description = "Support CQL only, we only implemented limit support")
                @RequestParam(value = "filter-lang", required = false, defaultValue = "cql-text") String filterLang,
            @Parameter(in = ParameterIn.QUERY, description = "Coordinate system, https://epsg.io/4326 ")
                @RequestParam(value = "crs", required = false, defaultValue = "https://epsg.io/4326") String crs,
            @Parameter(in = ParameterIn.QUERY, description = "Filter expression")
                @RequestParam(value = "filter", required = false) String filter,
            @Size(min=1) @Parameter(in = ParameterIn.QUERY, description = "" ,schema=@Schema())
                @Valid @RequestParam(value = "sortby", required = false, defaultValue = "score") List<String> sortby) throws CQLException {

        // TODO: Support other CRS.
        if (CQLFilterType.convert(filterLang) == CQLFilterType.CQL && CQLCrsType.convertFromUrl(crs) == CQLCrsType.EPSG3857) {
            // TODO , transform EPSG3857 to EPSG4326
            return commonService.getCollectionList(q, filter, OGCMediaTypeMapper.json, stacToCollection::convert);
        }
        else {
            List<String> reasons = new ArrayList<>();

            if(CQLFilterType.convert(filterLang) != CQLFilterType.CQL) {
                reasons.add("Unknown filter language, support cql-text only");
            }

            if(CQLCrsType.convertFromUrl(crs) != CQLCrsType.EPSG3857) {
                reasons.add("Unknown crs, support EPSG3857 only");
            }

            ErrorMessage msg = ErrorMessage.builder()
                    .reasons(reasons)
                    .build();

            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(msg);
        }
    }
}
