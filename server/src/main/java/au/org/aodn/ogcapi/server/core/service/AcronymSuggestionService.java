package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.synonyms.SynonymRuleRead;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Suggests the full name behind a typed acronym for autocomplete, e.g. "aad" -> "Australian Antarctic Division".
 * Matches by prefix against a dictionary read (cached) from the Elasticsearch synonyms set es-indexer
 * maintains — the single source of truth, so no acronym config here.
 */
@Slf4j
@Service
public class AcronymSuggestionService {

    private static final String ACRONYM_FILTER = "acronym_synonym_filter";   // see portal_records_index_schema.json
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final ElasticsearchClient esClient;
    private final RestClientTransport transport;
    private final ObjectMapper mapper;
    private final String indexName;

    private volatile Map<String, String> acronymToFullName = Map.of();
    private volatile long cacheExpiresAt = 0L;

    public AcronymSuggestionService(ElasticsearchClient esClient,
                                    RestClientTransport transport,
                                    ObjectMapper mapper,
                                    @Value("${elasticsearch.index.name}") String indexName) {
        this.esClient = esClient;
        this.transport = transport;
        this.mapper = mapper;
        this.indexName = indexName;
    }

    /** Suggest the full names whose acronym starts with the typed text, e.g. "aad" -> ["Australian Antarctic Division"]. */
    public List<String> suggestFullNames(String typedText) {
        if (typedText == null || typedText.isBlank()) {
            return List.of();
        }
        String prefix = typedText.trim().toLowerCase();
        return acronymDictionary().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> toDisplayLabel(entry.getValue()))
                .toList();
    }

    /** acronym -> full name, refreshed from Elasticsearch when the cache expires (only one thread reloads). */
    private Map<String, String> acronymDictionary() {
        if (System.currentTimeMillis() >= cacheExpiresAt) {
            synchronized (this) {
                if (System.currentTimeMillis() >= cacheExpiresAt) {   // re-check: another thread may have just reloaded
                    acronymToFullName = loadFromElasticsearch();
                    cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
                }
            }
        }
        return acronymToFullName;
    }

    /** Read es-indexer's synonyms set and parse every rule into the acronym -> full name dictionary. */
    private Map<String, String> loadFromElasticsearch() {
        Map<String, String> dictionary = new LinkedHashMap<>();
        try {
            String synonymSet = findSynonymSetName();
            if (synonymSet == null) {
                return dictionary;
            }
            var response = esClient.synonyms().getSynonym(get -> get.id(synonymSet).size(10_000));
            for (SynonymRuleRead rule : response.synonymsSet()) {
                addRuleToDictionary(rule.synonyms(), dictionary);
            }
        } catch (IOException | ElasticsearchException e) {
            log.warn("Could not load acronyms for index '{}': {}", indexName, e.getMessage());
        }
        return dictionary;
    }

    /** Which synonyms set the index uses, read from raw settings (the 8.13 client can't expose synonyms_set). */
    private String findSynonymSetName() throws IOException {
        Response settings = transport.restClient().performRequest(new Request("GET", "/" + indexName + "/_settings"));
        // { "<index>": { "settings": { "index": { "analysis": { "filter": { "acronym_synonym_filter": {...} } } } } } }
        Iterator<JsonNode> indices = mapper.readTree(settings.getEntity().getContent()).elements();
        if (!indices.hasNext()) {
            return null;
        }
        JsonNode synonymSet = indices.next()
                .path("settings").path("index").path("analysis")
                .path("filter").path(ACRONYM_FILTER).path("synonyms_set");
        return synonymSet.isMissingNode() ? null : synonymSet.asText();
    }

    /** Parse one rule and add it: "aad => australian antarctic division" becomes aad -> australian antarctic division. */
    private static void addRuleToDictionary(String rule, Map<String, String> dictionary) {
        String[] acronymAndFullName = rule.split("=>", 2);
        if (acronymAndFullName.length == 2) {
            String acronym = acronymAndFullName[0].trim().toLowerCase();
            String fullName = acronymAndFullName[1].trim();
            dictionary.put(acronym, fullName);
        }
    }

    /** Turn a dictionary value into a tidy dropdown label: "australian antarctic division" -> "Australian Antarctic Division". */
    private static String toDisplayLabel(String fullName) {
        return Arrays.stream(fullName.split(" "))
                .filter(word -> !word.isEmpty())
                .map(AcronymSuggestionService::capitaliseFirstLetter)
                .collect(Collectors.joining(" "));
    }

    /** "australian" -> "Australian" */
    private static String capitaliseFirstLetter(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
