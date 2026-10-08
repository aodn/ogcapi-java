package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.server.core.exception.CustomException;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureId;
import au.org.aodn.ogcapi.server.core.parser.stac.CQLToStacFilterFactory;
import au.org.aodn.ogcapi.server.tile.RestApi;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.opengis.filter.Filter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;

/**
 *
 */
public abstract class OGCApiService {

    protected Logger logger = LoggerFactory.getLogger(RestApi.class);

    @Autowired
    protected Search search;

    @Autowired
    protected ObjectMapper mapper;

    /**
     * You can find conformance id
     * <a href="https://docs.ogc.org/is/19-072/19-072.html#ats_core">here</a>
     * @return List of string contains conformance
     */
    public abstract List<String> getConformanceDeclaration();

    public ResponseEntity<FeatureCollectionGeoJSON> getFeature(String collectionId,
                                                               FeatureId fid,
                                                               List<String> properties,
                                                               String filter) {
        switch(fid) {
            default -> {
                // Individual item
                return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
            }
        }
    }
    /**
     * This function is used to generate the collection list but Streaming the output, this will lower the use of memory
     * because we do not create the whole collection in memory before writing to client.
     */
    public ResponseEntity<StreamingResponseBody> writeCollectionList(List<String> keywords,
                                                                    String filter,
                                                                    List<String> properties,
                                                                    String sortBy,
                                                                    CQLCrsType coor,
                                                                    BiFunction<StacCollectionModel, Filter, Collection> toCollection) {
        try {
            search.validateByParameters(keywords, filter, properties, sortBy, coor);
        }
        catch (IllegalArgumentException iae) {
            throw iae;
        }
        catch (Exception e) {
            throw new CustomException(e.getMessage(), e);
        }

        Filter cql = null;
        try {
            if (filter != null) {
                CQLToStacFilterFactory factory = CQLToStacFilterFactory.builder()
                        .cqlCrsType(coor)
                        .build();
                cql = CompilerUtil.parseFilter(Language.CQL, filter, factory);
            }
        }
        catch (Exception ex) {
            // Do nothing, same as getCollectionList
        }
        Filter itemFilter = cql;

        StreamingResponseBody body = output -> {
            try (JsonGenerator generator = CollectionsResponseWriter.open(output, mapper)) {
                ElasticSearchBase.SearchResult<StacCollectionModel> meta;
                try {
                    meta = search.visitByParameters(
                            keywords, filter, properties, sortBy, coor, model -> {
                                try {
                                    generator.writeObject(toCollection.apply(model, itemFilter));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                List<String> searchAfter = meta.getSortValues() == null
                        ? null
                        : meta.getSortValues().stream().map(String::valueOf).toList();
                CollectionsResponseWriter.finish(generator, meta.getTotal(), searchAfter);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
    /**
     * Rewrite the datetime parameter to CQL filter as this can be handled by CQL directly.
     *
     * @param datetime - In come datetime parameter of ogcapi
     * @param filter - Any existing filter
     * @return - A combined filter with datetime rewrite.
     */
    public static String processDatetimeParameter(String fieldName, String datetime, String filter) {

        // TODO: How to handle this? e.g how to know if it is before or after if ?datetime=<timestamp instant>

        // I will hack around with string processing for now
        String operator = null;
        String d = null;
        String f = null;

        // for now, assumption is that temporal is the only filter
        if (datetime.startsWith("../") || datetime.startsWith("/")) {
            operator = "before";
            d = datetime.split("/")[1];
        }
        else if (datetime.endsWith("/..") || datetime.endsWith("/")) {
            operator = "after";
            d = datetime.split("/")[0];
        }
        else if (datetime.contains("/") && !datetime.contains("..")) {
            operator = "during";
            d = datetime;
        }

        if(d != null) {
            f = String.format("%s %s %s", fieldName, operator, d);
        }

        if((filter == null || filter.isEmpty())) {
            return f;
        }
        else {
            if(f == null) {
                return filter;
            }
            else {
                return String.join(" AND ", filter, f);
            }
        }
    }
    /**
     * Convert the bbox parameter to CQL
     * @param bbox - Bounding box
     * @param filter - CQL filter string
     * @return - String format as cql
     */
    public static String processBBoxParameter(String fieldName, List<BigDecimal> bbox, String filter) {
        String f = null;
        if(bbox.size() == 4) {
            // 2D
            f = String.format("BBOX(%s,%s,%s,%s,%s)", fieldName, bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
        }
        else if(bbox.size() == 6) {
            // 3D
            f = String.format("BBOX(%s,%s,%s,%s,%s,%s,%s)", fieldName, bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3), bbox.get(4), bbox.get(5));
        }

        if(f == null) {
            return filter;
        }
        else {
            return String.join(" AND ", filter, f);
        }
    }
}
