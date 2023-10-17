package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.ErrorMessage;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.tile.RestApi;
import au.org.aodn.ogcapi.tile.model.TileMatrixSets;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Point;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 *
 */
public abstract class OGCApiService {

    protected Logger logger = LoggerFactory.getLogger(RestApi.class);

    @Autowired
    protected Search search;

    /**
     * You can find conformance id here https://docs.ogc.org/is/19-072/19-072.html#ats_core
     * @return
     */
    public abstract List<String> getConformanceDeclaration();

    public <T, R> ResponseEntity<?> getVectorTileOfCollection(TileMatrixSets coordinateSystem, List<String> ids, Integer tileMatrix, Integer tileRow, Integer tileCol, Function<T, R> converter) {
        try {
            // TODO: Implements additional filters
            switch (coordinateSystem) {
                case WEBMERCATORQUAD -> {
                    return ResponseEntity.ok()
                            .contentType(OGCMediaTypeMapper.mapbox.getMediaType())
                            .body(converter.apply((T) search.searchCollectionVectorTile(ids, tileMatrix, tileRow, tileCol)));
                }
                default -> {
                    // We support WEBMERCATORQUAD at the moment, so if it isn't return empty set.
                    ErrorMessage message = ErrorMessage.builder()
                            .reasons(List.of("Unknown coordinate system"))
                            .build();

                    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(message);
                }
            }
        }
        catch(IOException ioe) {
            ErrorMessage errorMessage = ErrorMessage.builder()
                    .reasons(List.of(ioe.getMessage()))
                    .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorMessage);
        }
    }

    public <R> ResponseEntity<R> getTileSetsListOfCollection(List<String> id, OGCMediaTypeMapper f, Function<List<StacCollectionModel>, R> converter) {
        try {
            switch (f) {
                case json -> {
                    List<StacCollectionModel> result = (id == null) ?
                            search.searchAllCollectionsWithGeometry() :
                            search.searchCollectionWithGeometry(id);

                    return ResponseEntity.ok()
                            .body(converter.apply(result));
                }
                default -> {
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
        catch(Exception e) {
            logger.error("Error during request", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public <R> ResponseEntity<R> getCollectionList(List<String> keywords,
                                                   String filter, OGCMediaTypeMapper f,
                                                   CQLCrsType coor, Function<List<StacCollectionModel>, R> converter) {
        try {
            switch (f) {
                case json -> {
                    List<StacCollectionModel> result = search.searchByParameters(keywords, filter, coor);

                    return ResponseEntity.ok()
                            .body(converter.apply(result));
                }
                default -> {
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
        catch(Exception e) {
            logger.error("Error during request", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
