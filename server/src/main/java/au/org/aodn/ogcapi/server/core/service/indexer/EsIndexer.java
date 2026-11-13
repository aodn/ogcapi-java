package au.org.aodn.ogcapi.server.core.service.indexer;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

public class EsIndexer implements ApplicationInfo {

    protected final IndexerProperties properties;
    protected final RestTemplate template;

    public EsIndexer(IndexerProperties properties, RestTemplate template) {
        this.properties = properties;
        this.template = template;
    }

    @Override
    public Map<String, Map<?,?>> getAppInfo() {
        return queryInfo(template, properties.host(), properties.infoPath());
    }
}
