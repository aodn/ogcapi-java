package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.exception.GeoserverFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.FeatureTypeInfo;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.FeatureInfoResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.LayerInfo;
import au.org.aodn.ogcapi.server.core.service.DasService;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WFSFieldModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.wms.WmsDefaultParam;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.List;
import java.util.NoSuchElementException;

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

    public ResponseEntity<FeatureInfoResponse> getWmsMapFeature(String collectionId, FeatureRequest request) {
        try {
            return ResponseEntity.ok()
                    .body(wmsServer.getMapFeatures(collectionId, request));
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
     * This is used to get the downloadable fields from wfs where layer name is not mentioned in wms
     *
     * @param collectionId - The uuid of dataset
     * @param request      - Request to get field given a layer name
     * @return - The downloadable field name
     */
    public ResponseEntity<?> getWfsDownloadableFields(String collectionId, FeatureRequest request) {

        if (request.getLayerName() == null || request.getLayerName().isEmpty()) {
            return ResponseEntity.badRequest().body("Layer name cannot be null or empty");
        }

        WFSFieldModel result = wfsServer.getDownloadableFields(collectionId, request, null);

        return result == null ?
                ResponseEntity.notFound().build() :
                ResponseEntity.ok(result);
    }

    /**
     * This is used to get the WMS fields from the describe wfs layer given a wms layer
     *
     * @param collectionId - The uuid of dataset
     * @param request      - Request to get field given a WMS layer name; if no layer name provided, it will return fields for all WMS links in the collection
     * @return - The WMS fields, or UNAUTHORIZED if it is not in white list
     */
    public ResponseEntity<?> getWmsFields(String collectionId, FeatureRequest request) {
        // Temp block and show only white list uuid, the other uuid need QA check before release.
        if (request.getEnableGeoServerWhiteList() && wmsDefaultParam.getAllowId() != null && !wmsDefaultParam.getAllowId().contains(collectionId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } else {
            List<WFSFieldModel> result = wmsServer.getWMSFields(collectionId, request);

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
        List<FeatureTypeInfo> result = wfsServer.getCapabilitiesFeatureTypes(collectionId, request);

        return result.isEmpty() ?
                ResponseEntity.notFound().build() :
                ResponseEntity.ok(result);
    }

    /**
     * @param collectionID - uuid
     * @param from         -
     * @return -
     */
    public ResponseEntity<?> getWaveBuoys(String collectionID, String from) {
        if (!dasService.isCollectionSupported(collectionID)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (from == null) {
            return ResponseEntity.badRequest().body("Parameter 'datetime' is required and must be in 'from/to' format");
        }

        java.time.ZonedDateTime fromDateTime = java.time.ZonedDateTime.parse(from);
        String to = fromDateTime.plusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'"));
        try {
            return ResponseEntity
                    .ok()
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(dasService.getWaveBuoys(from, to));

        } catch (Exception e) {
            log.error("Error fetching wave buoys data: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> getWaveBuoysLatestDate(String collectionID) {
        if (!dasService.isCollectionSupported(collectionID)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }

        try {
            return ResponseEntity
                    .ok()
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(dasService.getWaveBuoysLatestDate());

        } catch (Exception e) {
            log.error("Error fetching wave buoys latest date: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> getWaveBuoyData(String collectionID, String datetime, String buoy) {
        if (!dasService.isCollectionSupported(collectionID)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (datetime == null) {
            return ResponseEntity.badRequest().body("Parameter 'datetime' is required and must be in 'from/to' format");
        }
        if (!datetime.contains("/")) {
            return ResponseEntity.badRequest().body("Parameter 'datetime' must be in 'from/to' format");
        }
        if (buoy == null) {
            return ResponseEntity.badRequest().body("Parameter 'waveBuoy' is required");
        }

        try {
            String[] parts = datetime.split("/", 2);
            String from = parts[0];
            String to = parts[1];

            return ResponseEntity
                    .ok()
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(dasService.getWaveBuoyData(from, to, buoy));

        } catch (Exception e) {
            log.error("Error fetching wave buoy historical data: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
