package au.org.aodn.ogcapi.server.core.service.dda;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import org.springframework.web.client.RestTemplate;
import java.util.Collections;
import java.util.Map;

public class DdaService implements ApplicationInfo {
    protected final Map<String, Map<?,?>> appInfo;

    public DdaService(DdaProperties properties, RestTemplate template) {
        this.appInfo = queryInfo(template, properties.host(), properties.infoPath());
    }

    @Override
    public String getName() {
        Object name = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("name", null);
        return name != null ? name.toString() : null;
    }

    @Override
    public String getVersion() {
        Object version = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("version", null);
        return version != null ? version.toString() : null;
    }

    @Override
    public String getDescription() {
        Object description = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("description", null);
        return description != null ? description.toString() : null;
    }
}
