package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.Search;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RestAdminService {
    @Value("${ogcapi.debug.elasticsearch-explain-enabled:false}")
    protected boolean elasticsearchExplainEnabled;

    @Autowired
    protected Search searchService;

    /**
     * Value defined in application-*.yml, set as true for dev, edge, staging, production and test.
     * The default is false, so any environment that does not set it explicitly has explain disabled.
     */
    public boolean isElasticsearchExplainEnabled() {
        return elasticsearchExplainEnabled;
    }

    public JsonNode explainByParameters(
            List<String> q,
            String filter,
            List<String> properties,
            String sortBy,
            CQLCrsType crs,
            boolean isSimplified) throws Exception {
        log.debug("Calling explain with isSimplified={}", isSimplified);
        return searchService.explainByParameters(q, filter, properties, sortBy, crs, isSimplified);
    }

    public JsonNode explainByUuid(
            String uuid,
            List<String> q,
            String filter,
            List<String> properties,
            String sortBy,
            CQLCrsType crs) throws Exception {
        log.info("Explaining search query for uuid {}", uuid);
        return searchService.explainByUuid(uuid, q, filter, properties, sortBy, crs);
    }
}
