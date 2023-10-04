package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.InternalService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("TileRestService")
public class RestService implements InternalService {

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/spec/ogcapi-tiles-1/1.0");
    }
}
