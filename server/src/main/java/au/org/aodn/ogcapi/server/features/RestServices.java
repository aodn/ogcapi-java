package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.ogcapi.server.core.model.DataSearchResult;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.StacItemModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureId;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service("FeaturesRestService")
@Slf4j
public class RestServices extends OGCApiService {

    @Autowired
    protected StacToCollection StacToCollection;

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/doc/IS/ogcapi-features-1/1.0.1");
    }

    public ResponseEntity<Collection> getCollection(String id, String sortBy) throws NoSuchElementException {
        ElasticSearch.SearchResult<StacCollectionModel> model = search.searchCollections(List.of(id), sortBy);

        if (!model.getCollections().isEmpty()) {
            if(model.getCollections().size() > 1) {
                log.error("UUID {} found in multiple records ", id);
            }

            return ResponseEntity.ok()
                    .body(StacToCollection.convert(model.getCollections().get(0), null));
        } else {
            log.error("UUID {} not found", id);
            return ResponseEntity.notFound().build();
        }
    }

    public ResponseEntity<?> getFeature(String collectionId,
                                                               FeatureId fid,
                                                               List<String> properties,
                                                               String filter) throws Exception {
        switch(fid) {
            case summary -> {
                ElasticSearch.SearchResult<StacItemModel> result = search.searchFeatureSummary(collectionId, properties, filter);
                return ResponseEntity.ok()
                        .body(result.getCollections());
            }
            default -> {
                // Individual item
                return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
            }
        }
    }


}
