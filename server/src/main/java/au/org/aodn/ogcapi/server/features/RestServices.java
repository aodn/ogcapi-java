package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("FeaturesRestService")
public class RestServices implements OGCApiService {

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/doc/IS/ogcapi-features-1/1.0.1");
    }
}
