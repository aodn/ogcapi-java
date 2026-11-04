package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.model.DatasetMetadata;
import au.org.aodn.ogcapi.server.core.model.enumeration.DasSiteProduct;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.FeatureTypeInfo;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsField;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.FeatureInfoResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.LayerInfo;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsFields;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsDefaultParam;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.CommonUtils;
import au.org.aodn.ogcapi.server.core.util.DatetimeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
@Service("FeaturesRestService")
public class RestServices extends OGCApiService {

    @Autowired
    protected DasService dasService;

    @Autowired
    protected StacToCollection StacToCollection;

    @Autowired
    protected WfsServer wfsServer;

    @Autowired
    protected WmsServer wmsServer;

    @Autowired
    protected WmsDefaultParam wmsDefaultParam;

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/doc/IS/ogcapi-features-1/1.0.1");
    }

    public ResponseEntity<Collection> getCollection(String id) throws NoSuchElementException {
        ElasticSearch.SearchResult<StacCollectionModel> model = search.searchCollections(id);

        if (!model.getCollections().isEmpty()) {
            if (model.getCollections().size() > 1) {
                log.error("UUID {} found in multiple records ", id);
            }

            return ResponseEntity.ok()
                    .body(StacToCollection.convert(model.getCollections().get(0), null));
        } else {
            log.error("UUID {} not found", id);
            return ResponseEntity.notFound().build();
        }
    }

    public ResponseEntity<DatasetMetadata> getDatasetMetadata(String id) {
        try {
            return dasService.getDatasetMetadata(id);
        }
        catch (Exception e) {
            log.error("Error fetching metadata of id: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<FeatureInfoResponse> getWmsMapFeature(String collectionId, FeatureRequest request) {
        try {
            FeatureInfoResponse response = wmsServer.getMapFeatures(collectionId, request);

            if (response == null) {
                log.warn("GetFeatureInfo returns null for uuid {} layer {}, popup will render nothing", collectionId, request.getLayerName());
            } else {
                log.debug("GetFeatureInfo returns uuid {} layer {} html length {} featureInfo count {}",
                        collectionId, request.getLayerName(),
                        response.getHtml() == null ? 0 : response.getHtml().length(),
                        response.getFeatureInfo() == null ? 0 : response.getFeatureInfo().size());
            }
            return ResponseEntity.ok().body(response);
        } catch (JsonProcessingException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public ResponseEntity<byte[]> getWmsMapTile(String collectionId, FeatureRequest request) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(wmsServer.getMapTile(collectionId, request));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Proxy the WMS GetLegendGraphic image (colour-bar / symbol legend) for a layer.
     *
     * @param collectionId - The uuid of the dataset
     * @param request      - Request holding the layer name
     * @return - The legend as an image/png, or 404 if no legend could be built
     */
    public ResponseEntity<byte[]> getWmsLegend(String collectionId, FeatureRequest request) {
        try {
            byte[] legend = wmsServer.getLegend(collectionId, request);
            if (legend == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    // Legends are stable per layer/style, so let the browser/CDN cache them
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                    .body(legend);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This is used to get the WFS fields given a WFS layer
     *
     * @param collectionId - The uuid of dataset
     * @param request      - Request to get a field given a WFS layer name; if no layer name provided, it will return fields for all WFS links in the collection
     * @return - The WFS fields
     */
    public ResponseEntity<?> getWfsFields(String collectionId, FeatureRequest request) {
        WfsServer.WfsFeatureRequest wfsFeatureRequest = WfsServer.WfsFeatureRequest.builder().build();
        BeanUtils.copyProperties(request, wfsFeatureRequest);
        List<WfsFields> result = wfsServer.getWFSFields(collectionId, wfsFeatureRequest);

        return result == null ?
                ResponseEntity.notFound().build() :
                ResponseEntity.ok(result);
    }
    /**
     * Get the list of values from the WFS, the FeatureRequest have predefined enum to control what can pass in for the
     * property name. You may need to update it if you want to expand the list.
     * @param collectionId - The uuid of the metadata
     * @param request - The request property you want to query
     * @return - The return value, which is sorted by desc
     */
    public ResponseEntity<?> getWfsFieldValue(String collectionId, FeatureRequest request) {
        WfsServer.WfsFeatureRequest wfsWithServer = wmsServer.createRequestFromLayerName(collectionId, request.getLayerName());
        WfsServer.WfsFeatureRequest wfsFeatureRequest = WfsServer.WfsFeatureRequest.builder().build();
        // clone value from request
        BeanUtils.copyProperties(request, wfsFeatureRequest);
        // copy enhanced value
        CommonUtils.copyIgnoringNull(wfsWithServer, wfsFeatureRequest);

        if(wfsFeatureRequest.getProperties() != null) {
            // Now check if we need to map field
            List<WfsFields> supportedFields = wfsServer.getWFSFields(collectionId, wfsFeatureRequest);
            List<String> extractedName = supportedFields.get(0).getFields().stream()
                    .map(WfsField::getName)
                    .toList();

            for (String name : wfsFeatureRequest.getProperties()) {
                if (extractedName.contains(name)) {
                    // TODO: If missing then may need map
                    log.info("Field {} need map", name);
                    //
                }
            }
        }

        String result = wfsServer.getFieldValues(collectionId, wfsFeatureRequest, new ParameterizedTypeReference<>() {});

        if(result != null) {
            FeatureJSON json = new FeatureJSON();
            try {
                @SuppressWarnings("unchecked")
                FeatureCollection<SimpleFeatureType, SimpleFeature> collection = json.readFeatureCollection(new StringReader(result));
                try (FeatureIterator<SimpleFeature> i = collection.features()) {
                    Map<String, List<Object>> results = new HashMap<>();
                    while (i.hasNext()) {
                        SimpleFeature s = i.next();
                        s.getProperties()
                                .forEach(property -> {
                                    results.computeIfAbsent(property.getName().toString(), k -> new ArrayList<>());
                                    results.get(property.getName().toString()).add(s.getAttribute(property.getName()));
                                });
                    }
                    return ResponseEntity.ok().body(results);
                }
            } catch (IOException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }
        else {
            // Field not found
            return ResponseEntity.notFound().build();
        }
    }
    /**
     * This is used to get the WMS fields from the describe wfs layer given a wms layer
     *
     * @param collectionId - The uuid of dataset
     * @param request      - Request to get field given a WMS layer name; if no layer name provided, it will return fields for all WMS links in the collection
     * @return - The WMS fields, or UNAUTHORIZED if it is not in white list
     */
    public ResponseEntity<?> getWmsFields(String collectionId, FeatureRequest request) {
        // Temp block and show only whitelist uuid, the other uuid need QA check before release.
        if (request.getEnableGeoServerWhiteList() && wmsDefaultParam.getAllowId() != null && !wmsDefaultParam.getAllowId().contains(collectionId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } else {
            List<WfsFields> result = wmsServer.getWMSFields(collectionId, request);

            return result.isEmpty() ?
                    ResponseEntity.notFound().build() :
                    ResponseEntity.ok(result);
        }
    }

    /**
     * This is used to get all available layers from WMS GetCapabilities
     *
     * @param collectionId - The uuid of dataset
     * @param request      - Request to get layers
     * @return - List of available layers with name, title, and serverUrl
     */
    public ResponseEntity<?> getWmsLayers(String collectionId, FeatureRequest request) {
        List<LayerInfo> result = wmsServer.getCapabilitiesLayers(collectionId, request);

        return result.isEmpty() ?
                ResponseEntity.notFound().build() :
                ResponseEntity.ok(result);
    }

    /**
     * This is used to get all available feature types from WFS GetCapabilities
     *
     * @param collectionId - The uuid of dataset
     * @param request      - Request to get feature types
     * @return - List of available feature types with name, title, abstract, etc.
     */
    public ResponseEntity<?> getWfsLayers(String collectionId, FeatureRequest request) {
        if (request.getEnableGeoServerWhiteList() && wmsDefaultParam.getAllowId() != null && !wmsDefaultParam.getAllowId().contains(collectionId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } else {
            List<FeatureTypeInfo> result = wfsServer.getCapabilitiesFeatureTypes(collectionId, request);

            return result.isEmpty() ?
                    ResponseEntity.notFound().build() :
                    ResponseEntity.ok(result);
        }
    }


    /**
     * Validate that startDateTime/endDateTime (when provided) are UTC ISO-8601 values and that the
     * range is ordered.
     *
     * @return a bad-request response describing the problem, or empty if the range is valid
     */
    private Optional<ResponseEntity<?>> validateUtcDateRange(String startDateTime, String endDateTime) {
        java.time.OffsetDateTime start;
        java.time.OffsetDateTime end;
        try {
            start = startDateTime == null ? null : DatetimeUtils.parseUtcDateTime(startDateTime);
            end = endDateTime == null ? null : DatetimeUtils.parseUtcDateTime(endDateTime);
        } catch (IllegalArgumentException e) {
            return Optional.of(ResponseEntity.badRequest().body(e.getMessage()));
        }

        if (start != null && end != null && start.isAfter(end)) {
            return Optional.of(ResponseEntity.badRequest().body("Parameter 'startDateTime' must not be after 'endDateTime'"));
        }
        return Optional.empty();
    }

    /**
     * Wraps a DAS response for return to the client, preserving the upstream status,
     * headers (e.g. cache/rate-limit headers), and body rather than just the body.
     */
    private ResponseEntity<?> forwardDasResponse(ResponseEntity<byte[]> dasResponse) {
        return ResponseEntity
                .status(dasResponse.getStatusCode())
                .headers(dasResponse.getHeaders())
                .body(dasResponse.getBody());
    }

    /**
     * RestTemplate throws on any non-2xx DAS response rather than returning it, so the
     * error status/headers/body have to be pulled off the exception to forward them to
     * the client instead of collapsing every DAS error into a 500.
     */
    private ResponseEntity<?> forwardDasError(HttpStatusCodeException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(e.getResponseBodyAsByteArray());
    }

    /**
     * Calls DAS and forwards its response, or its error status/headers/body if DAS rejected the call.
     *
     * @param description what is being fetched, for the log message.
     */
    private ResponseEntity<?> forwardDas(String description, Supplier<ResponseEntity<byte[]>> dasCall) {
        try {
            return forwardDasResponse(dasCall.get());

        } catch (HttpStatusCodeException e) {
            log.error("DAS returned an error fetching {}: {}", description, e.getStatusCode());
            return forwardDasError(e);
        } catch (Exception e) {
            log.error("Error fetching {}: {}", description, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Returns the sites of a DAS product recorded within the given UTC date range.
     *
     * @param startDateTime,endDateTime must be UTC or null, since the underlying DAS service expects UTC only.
     */
    public ResponseEntity<?> getSitesBetweenDates(DasSiteProduct product, String startDateTime, String endDateTime) {
        Optional<ResponseEntity<?>> validationError = validateUtcDateRange(startDateTime, endDateTime);
        if (validationError.isPresent()) {
            return validationError.get();
        }

        return forwardDas(product.label + "s data", () -> dasService.getSitesBetweenDates(product, startDateTime, endDateTime));
    }

    /**
     * Returns detailed data for a single site of a DAS product within the given UTC date range.
     *
     * @param startDateTime,endDateTime must be UTC or null, since the underlying DAS service expects UTC only.
     * @param site the site identifier; required.
     */
    public ResponseEntity<?> getSiteDetailsBetweenDates(DasSiteProduct product, String startDateTime, String endDateTime, String site) {
        Optional<ResponseEntity<?>> validationError = validateUtcDateRange(startDateTime, endDateTime);
        if (validationError.isPresent()) {
            return validationError.get();
        }
        if (site == null) {
            return ResponseEntity.badRequest().body("Parameter '" + product.siteParameter + "' is required");
        }

        return forwardDas(product.label + " historical data", () -> dasService.getSiteDetailsBetweenDates(product, startDateTime, endDateTime, site));
    }

    /**
     * Returns the latest date for which data of a DAS product is available.
     */
    public ResponseEntity<?> getLatestAvailableDate(DasSiteProduct product) {
        return forwardDas(product.label + "s latest date", () -> dasService.getLatestAvailableDate(product));
    }
}
