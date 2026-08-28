package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.Search;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RestAdminService {

    @Autowired
    protected Search searchService;

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
