package au.org.aodn.ogcapi.server.core.service.dda;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

public class DdaService implements ApplicationInfo {
    protected final DdaProperties properties;
    protected final RestTemplate template;

    public DdaService(DdaProperties properties, RestTemplate template) {
        this.properties = properties;
        this.template = template;
    }

    @Override
    public Map<String, Map<?,?>> getAppInfo() {
        return queryInfo(template, properties.host(), properties.infoPath());
    }
}
