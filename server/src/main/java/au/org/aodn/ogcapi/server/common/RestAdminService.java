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
     * Value defined in application-*.yml, for dev and test, this value is set as true,
     * for other environments, use default false value.
     */
    public boolean isElasticsearchExplainEnabled() {
        return !elasticsearchExplainEnabled;
    }

    public JsonNode explainByParameters(
            List<String> q,
            String filter,
            List<String> properties,
            String sortBy,
            CQLCrsType crs) throws Exception {
        log.info("Explaining search query");
        return searchService.explainByParameters(q, filter, properties, sortBy, crs);
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
