package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.service.DasService;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadableFieldsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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
    protected DownloadableFieldsService downloadableFieldsService;

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

    /**
     * Get downloadable fields for a layer
     *
     * @param wfsUrl   The WFS server URL
     * @param typeName The WFS type name
     * @return List of downloadable fields
     */
    public ResponseEntity<List<DownloadableFieldModel>> getDownloadableFields(String wfsUrl, String typeName) {
        List<DownloadableFieldModel> fields = downloadableFieldsService.getDownloadableFields(wfsUrl, typeName);
        return ResponseEntity.ok(fields);
    }

    public ResponseEntity<?> getWaveBuoys(String collectionID, String from) {
        if (dasService.isCollectionSupported(collectionID)){
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (from == null) {
            return ResponseEntity.badRequest().body("Parameter 'datetime' is required and must be in 'from/to' format");
        }

        java.time.LocalDate localDate = java.time.LocalDate.parse(from);
        String to = localDate.plusDays(1).toString();
        try {
           return ResponseEntity
                    .ok()
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(dasService.getWaveBuoys(from,to));

        } catch (Exception e) {
            log.error("Error fetching wave buoys data: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> getWaveBuoyData(String collectionID, String datetime, String buoy) {
        if (dasService.isCollectionSupported(collectionID)){
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
                    .body(dasService.getWaveBuoyData(from,to, buoy));

        } catch (Exception e) {
            log.error("Error fetching wave buoy historical data: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
