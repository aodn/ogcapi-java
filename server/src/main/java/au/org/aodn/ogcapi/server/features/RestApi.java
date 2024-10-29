package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.api.CollectionsApi;
import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
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

    /**
     * Hidden because we want to have a more functional implementation
     */
    @Hidden
    @Override
    public ResponseEntity<FeatureGeoJSON> getFeature(String collectionId, String featureId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // Deprecated for now because we are using getFeatures to return dataset. May be useful in the future
    @Deprecated
    @RequestMapping(
            value = {"/collections/{collectionId}/items/{featureId}"},
            produces = {"application/geo+json", "text/html", "application/json"},
            method = {RequestMethod.GET}
    )
    public ResponseEntity<FeatureGeoJSON> getFeature(

            @PathVariable("collectionId") String collectionId,
            @PathVariable("featureId") String featureId,
            @RequestParam(value = "start_datetime", required = false) String startDate,
            @RequestParam(value = "end_datetime", required = false) String endDate
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }


    @RequestMapping(
            value = {"/collections/{collectionId}/items"},
            produces = {"application/geo+json", "text/html", "application/json"},
            method = {RequestMethod.GET}
    )
    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(

            @PathVariable("collectionId") String collectionId,
            @RequestParam(value = "start_datetime", required = false) String startDate,
            @RequestParam(value = "end_datetime", required = false) String endDate
    ) {
        return  featuresService.getDataset(collectionId, startDate, endDate);
    }



    /**
     * Hidden because we want to have a more functional implementation
     */
    @Hidden
    @Override
    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(String collectionId, Integer limit, List<BigDecimal> bbox, String datetime) {
        return null;
    }
    /**
     * @Hidden effectively disable this REST point because it is common in many places and
     * should not implement it here, @Hidden disable swagger doc too
     * @return
     */
    @Hidden
    @Override
    public ResponseEntity<Collections> getCollections() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
