package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.tile.RestApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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

    public <R> ResponseEntity<R> getTileSetsList(String id, String f, Function<List<StacCollectionModel>, R> converter) {
        try {
            switch (f == null ? OGCMediaTypeMapper.json : OGCMediaTypeMapper.valueOf(f.toLowerCase())) {
                case json: {
                    List<StacCollectionModel> result = (id == null) ?
                            search.searchAllCollectionsWithGeometry() :
                            search.searchCollectionWithGeometry(id);

                    return ResponseEntity.ok()
                            .body(converter.apply(result));
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
        catch(Exception e) {
            logger.error("Error during request", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public <R> ResponseEntity<R> getCollectionList(List<String> targets, String f, Function<List<StacCollectionModel>, R> converter) {
        try {
            switch (f == null ? OGCMediaTypeMapper.json : OGCMediaTypeMapper.valueOf(f.toLowerCase())) {
                case json: {
                    List<StacCollectionModel> result = search.searchByTitleDescKeywords(targets);

                    return ResponseEntity.ok()
                            .body(converter.apply(result));
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
        catch(Exception e) {
            logger.error("Error during request", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
