package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.api.CollectionsApi;
import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController("FeaturesRestApi")
public class RestApi implements CollectionsApi {

    @Override
    public ResponseEntity<Collection> describeCollection(String collectionId) {
        return null;
    }

    @Override
    public ResponseEntity<FeatureGeoJSON> getFeature(String collectionId, String featureId) {
        return null;
    }

    @Override
    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(String collectionId, Integer limit, List<BigDecimal> bbox, String datetime) {
        return null;
    }
    /**
     * Bean always found, this effectively disable this REST point because it is common in many places and
     * should not implement it here, @Hiden disable swagger doc
     * @return
     */
    @Hidden
    @Override
    public ResponseEntity<Collections> getCollections() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
