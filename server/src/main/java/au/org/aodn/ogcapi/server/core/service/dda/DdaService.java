package au.org.aodn.ogcapi.server.core.service.dda;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

public class DdaService implements ApplicationInfo {
    protected final InfoCache appInfo;

    public DdaService(DdaProperties properties, RestTemplate template) {
        this.appInfo = new InfoCache(() -> queryInfo(template, properties.host(), properties.infoPath()));
    }

    @Override
    public Map<String, Map<?,?>> getAppInfo() {
        return appInfo.get();
    }
}
