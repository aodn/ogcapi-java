package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.core.exception.CustomException;
import au.org.aodn.ogcapi.tile.model.TileMatrixSets;
import org.opengis.filter.Filter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;

@Service("TileRestService")
public class RestService extends OGCApiService {

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/spec/ogcapi-tiles-1/1.0");
    }

    @SuppressWarnings("unchecked")
    public <T, R> ResponseEntity<?> getVectorTileOfCollection(
            TileMatrixSets coordinateSystem,
            List<String> ids,
            Integer tileMatrix,
            Integer tileRow,
            Integer tileCol,
            BiFunction<T, Filter, R> converter) {

        // TODO: Implements additional filters
        try {
            switch (coordinateSystem) {
                case WEBMERCATORQUAD -> {
                    return ResponseEntity.ok()
                            .contentType(OGCMediaTypeMapper.mapbox.getMediaType())
                            .body(converter.apply((T) search.searchCollectionVectorTile(ids, tileMatrix, tileRow, tileCol), null));
                }
                default -> {
                    // We support WEBMERCATORQUAD at the moment, so if it isn't return empty set.
                    throw new IllegalArgumentException("Unknown coordinate system");
                }
            }
        }
        catch(IOException e) {
            throw new CustomException(e.getMessage());
        }
    }

    public <R> ResponseEntity<R> getTileSetsListOfCollection(List<String> id, String sortBy, OGCMediaTypeMapper f,
                                                             BiFunction<ElasticSearch.SearchResult, Filter, R> converter) {
        try {
            switch (f) {
                case json -> {
                    ElasticSearch.SearchResult result = (id == null) ?
                            search.searchAllCollectionsWithGeometry(sortBy) :
                            search.searchCollectionWithGeometry(id, sortBy);

                    return ResponseEntity.ok()
                            .body(converter.apply(result, null));
                }
                default -> {
                    /*
                     * https://opengeospatial.github.io/ogcna-auto-review/19-072.html
                     * The OGC API — Common Standard does not mandate a specific encoding or format for
                     * representations of resources. However, both HTML and JSON are commonly used encodings for spatial
                     * data on the web. The HTML and JSON requirements classes specify the encoding of resource
                     * representations using:
                     *     HTML
                     *     JSON
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
