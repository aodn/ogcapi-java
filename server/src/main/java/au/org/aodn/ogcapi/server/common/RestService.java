package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.service.OGCApiService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("CommonRestService")
public class RestService extends OGCApiService {

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/spec/ogcapi-common-1/1.0/conf/core");
    }
}
