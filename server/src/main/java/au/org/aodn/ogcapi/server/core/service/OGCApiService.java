package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
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

    public <R> ResponseEntity<R> getCollectionList(List<String> keywords,
                                                   String filter,
                                                   OGCMediaTypeMapper f,
                                                   CQLCrsType coor,
                                                   Function<List<StacCollectionModel>, R> converter,
                                                   List<String> properties) {
        try {
            switch (f) {
                case json -> {
                    List<StacCollectionModel> result = search.searchByParameters(keywords, filter, coor, properties);

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

    public String processDatetimeParameter(String datetime, String filter) {

        // TODO: the AND operator yet supported, when it is, append the datetime input to existing filter after with the AND prefix

        // TODO. should support "anyinteracts"? otherwise need to have a proper method to find correct operator without hackaround with string processing,
        //  e.g how to know if it is before or after if ?datetime=<timestamp instant>

        // I will hack around with string processing for now
        String operator;

        // for now, assumption is that temporal is the only filter
        if (filter == null) {
            if (datetime.startsWith("../") || datetime.startsWith("/")) {
                operator = "before";
            } else if (datetime.endsWith("/..") || datetime.endsWith("/")) {
                operator = "after";
            } else if (datetime.contains("/") && !datetime.contains("..")) {
                operator = "during";
            } else {
                operator = "tequals";
            }
            filter = String.format("temporal %s %s", operator, datetime);
        }

        return filter;
    }
}
