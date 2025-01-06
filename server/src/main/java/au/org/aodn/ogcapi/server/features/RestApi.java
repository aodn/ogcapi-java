package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.api.CollectionsApi;
import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController("FeaturesRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi implements CollectionsApi {

    @Autowired
    protected RestServices featuresService;

    @Override
    public ResponseEntity<Collection> describeCollection(String collectionId) {
        return featuresService.getCollection(collectionId, null);
    }

    @Override
    public ResponseEntity<FeatureGeoJSON> getFeature(String collectionId, String featureId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

//    @RequestMapping(
//            value = {"/collections/{collectionId}/items"},
//            produces = {"application/geo+json", "text/html", "application/json"},
//            method = {RequestMethod.GET}
//    )
//    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(
//
//            @PathVariable("collectionId") String collectionId,
//            @RequestParam(value = "start_datetime", required = false) String startDate,
//            @RequestParam(value = "end_datetime", required = false) String endDate,
//            // keep these two parameters for future usage
//            @RequestParam(value= "zoom", required = false) Double zoomLevel,
//            @RequestParam(value="bbox", required = false) List<BigDecimal> bbox
//    ) {
//        return  featuresService.getSummarizedDataset(collectionId, startDate, endDate);
//    }
    /**
     *
     * @param collectionId - The collection id
     * @param limit - Limit of result return
     * @param bbox - Bounding box that bounds the result set. In case of multiple bounding box, you need to issue multiple query
     * @param datetime - Start/end time
     * @return - The data that matches the filter criteria
     */
    @Override
    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(String collectionId, Integer limit, List<BigDecimal> bbox, String datetime) {
        String filter = null;
        if (datetime != null) {
            filter = OGCApiService.processDatetimeParameter(datetime, null);
        }
        return featuresService.getSummarizedDataset(collectionId, startDate, endDate);
    }
    /**
     * Hidden effectively disable this REST point because it is common in many places and
     * should not implement it here, @Hidden disable swagger doc too
     * @return - Not implemented
     */
    @Hidden
    @Override
    public ResponseEntity<Collections> getCollections() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
