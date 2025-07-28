package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.features.model.DownloadableField;
import au.org.aodn.ogcapi.server.features.service.DownloadableFieldsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service("FeaturesRestService")
public class RestServices extends OGCApiService {

    @Autowired
    protected StacToCollection StacToCollection;

    @Autowired
    protected DownloadableFieldsService downloadableFieldsService;

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

    /**
     * Get downloadable fields for a layer
     * @param wfsUrl The WFS server URL
     * @param typeName The WFS type name
     * @return List of downloadable fields
     */
    public ResponseEntity<List<DownloadableField>> getDownloadableFields(String wfsUrl, String typeName) {
        List<DownloadableField> fields = downloadableFieldsService.getDownloadableFields(wfsUrl, typeName);
        return ResponseEntity.ok(fields);
    }
}
