package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.ogcapi.server.core.model.ErrorMessage;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service("FeaturesRestService")
public class RestServices extends OGCApiService {

    @Autowired
    protected StacToCollection StacToCollection;

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/doc/IS/ogcapi-features-1/1.0.1");
    }

    public <R> ResponseEntity<R> getCollection(String id) {
        try {
            List<StacCollectionModel> model = search.searchCollections(List.of(id));

            if (model.size() == 1) {
                return ResponseEntity.ok()
                        .body((R) StacToCollection.convert(model.get(0)));
            } else {
                ErrorMessage msg = ErrorMessage.builder()
                        .reasons(List.of(String.format("Found more then 1 record for the uuid", id)))
                        .build();

                return ResponseEntity
                        .of(Optional.of(msg))
                        .status(HttpStatus.NOT_FOUND)
                        .build();
            }
        }
        catch (Exception e) {
            ErrorMessage msg = ErrorMessage.builder()
                    .reasons(List.of(e.getMessage()))
                    .build();

            return ResponseEntity
                    .of(Optional.of(msg))
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }
}
