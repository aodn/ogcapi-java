package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.exception.DocumentNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.IndexNotFoundException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class RestExtService {
    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabsIndexName;

    @Autowired
    protected ElasticsearchClient esClient;

    public List<JsonNode> getParameterVocabs() throws IOException {
        return this.groupVocabsByKey("parameter_vocab");
    }

    public List<JsonNode> getPlatformVocabs() throws IOException {
        return this.groupVocabsByKey("platform_vocab");
    }

    public List<JsonNode> getOrganisationVocabs() throws IOException {
        return this.groupVocabsByKey("organisation_vocab");
    }

    private long getDocumentsCount(String indexName) {
        try {
            return esClient.count(s -> s
                    .index(indexName)
            ).count();
        } catch (ElasticsearchException | IOException e) {
            throw new IndexNotFoundException("Failed to get documents count from index: " + indexName + " | " + e.getMessage());
        }
    }

    protected List<JsonNode> groupVocabsByKey(String key) throws IOException {
        List<JsonNode> vocabs = new ArrayList<>();
        log.info("Fetching {} vocabularies from {}", key, vocabsIndexName);
        try {
            long totalHits = getDocumentsCount(vocabsIndexName);
            if (totalHits == 0) {
                throw new DocumentNotFoundException("No documents found in " + vocabsIndexName);
            } else {
                SearchResponse<JsonNode> response = esClient.search(s -> s
                        .index(vocabsIndexName)
                        .size((int) totalHits), JsonNode.class
                );
                response.hits().hits().stream()
                        .map(Hit::source)
                        .map(hitSource -> hitSource != null ? hitSource.get(key) : null)
                        .filter(Objects::nonNull)
                        .forEach(vocabs::add);
            }
        } catch (ElasticsearchException | IOException e) {
            throw new IOException("Failed to get documents from " + vocabsIndexName + " | " + e.getMessage());
        }
        return vocabs;
    }
}
