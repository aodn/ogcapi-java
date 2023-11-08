package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.common.RestApi;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
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

    protected static Logger logger = LoggerFactory.getLogger(OGCApiService.class);

    @Autowired
    protected Search search;

    /**
     * You can find conformance id here https://docs.ogc.org/is/19-072/19-072.html#ats_core
     * @return
     */
    public abstract List<String> getConformanceDeclaration();

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

    public static String processDatetimeParameter(String datetime, String filter) {
        /* this method for translating datetime parameter to filter parameter to use with CQL expression */

        // TODO: the AND operator yet supported, when it is, append the datetime input to existing filter after with the AND prefix

        // TODO. should support "anyinteracts"? otherwise need to have a proper method to find correct operator without hackaround with string processing,
        //  e.g how to know if it is before or after if ?datetime=<timestamp instant>

        // I will hack around with string processing for now
        String operator;

        // for now, assumption is that temporal is the only filter
        if (filter == null) {
            // Remove the leading "../" or "/" for 'before' operator
            if (datetime.startsWith("../") || datetime.startsWith("/")) {
                operator = "before";
                datetime = datetime.substring(datetime.lastIndexOf("/") + 1);
            }
            // Remove the trailing "/.." or "/" for 'after' operator
            else if (datetime.endsWith("/..") || datetime.endsWith("/")) {
                operator = "after";
                // TODO: should allow or NOT "/" without ".." at the end to avoid confusion with the usual "/" at the end of an url
                // e.g localhost:8082/collections?datetime=2003-11-10T00:00:00.000Z/ will return different results from
                // localhost:8082/collections?datetime=2003-11-10T00:00:00.000Z
                datetime = datetime.substring(0, datetime.indexOf("/"));
            }
            // Check for 'during' operator which is indicated by a single "/"
            else if (datetime.contains("/") && !datetime.startsWith("/") && !datetime.endsWith("/")) {
                String[] parts = datetime.split("/");
                // Ensure that the parts before and after the "/" are not the same
                if (!parts[0].equals(parts[1])) {
                    operator = "during";
                } else {
                    // if don't make it "tequals", GeoTools will throw exception
                    operator = "tequals";
                    datetime = parts[0];
                }
            }
            // Default operator is 'equals'
            // not implemented yet, will return all records
            else {
                operator = "tequals";
            }
            // Format the filter string

            filter = String.format("temporal %s %s", operator, datetime);
            logger.debug("CQL filter: " + filter);
        }
        return filter;
    }
}
