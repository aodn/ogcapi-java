package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.synonyms.GetSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.SynonymRuleRead;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Knows which acronyms es-indexer pushed into the ES synonyms set (e.g. "soop => ships of opportunity"), so a
 * search keyword is only scored against the *.synonyms sub-fields when it actually contains an acronym.
 * Without this gate, plain words pass through the synonym analyser unchanged and are scored twice.
 * The expansion itself is still done by ES at search time; this class only answers "does the keyword contain one?".
 */
@Slf4j
public class AcronymLookup {

    /** GET _synonyms defaults to 10 rules per page; a set is capped at 10000, so ask for them all. */
    protected static final int MAX_SYNONYM_RULES = 10000;

    protected final ElasticsearchClient client;
    protected final String synonymSetName;

    // null means unknown (never loaded), in which case every keyword may expand
    protected volatile Set<String> acronyms;

    public AcronymLookup(ElasticsearchClient client, String synonymSetName) {
        this.client = client;
        this.synonymSetName = synonymSetName;
    }

    /**
     * Load at startup so the first search already has the list, then reload every hour. The rules only change
     * when es-indexer reindexes or syncs acronyms, so a new acronym is not gated in for up to an hour.
     */
    @PostConstruct
    @Scheduled(initialDelay = 60 * 60 * 1000, fixedRate = 60 * 60 * 1000)
    public void refresh() {
        try {
            acronyms = client.synonyms()
                    .getSynonym(GetSynonymRequest.of(g -> g.id(synonymSetName).size(MAX_SYNONYM_RULES)))
                    .synonymsSet()
                    .stream()
                    .map(SynonymRuleRead::synonyms)
                    .filter(rule -> rule.contains("=>"))
                    // "aad, aadx => australian antarctic division" -> ["aad", "aadx"]
                    .flatMap(rule -> Arrays.stream(rule.substring(0, rule.indexOf("=>")).split(",")))
                    .map(AcronymLookup::normalise)
                    .filter(acronym -> !acronym.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
            log.info("Loaded {} acronyms from synonyms set '{}'", acronyms.size(), synonymSetName);
        }
        catch (ElasticsearchException e) {
            if (e.status() == 404) {
                // The set does not exist yet, so nothing can expand
                acronyms = Set.of();
                log.info("Synonyms set '{}' does not exist, no acronym will expand", synonymSetName);
            }
            else {
                log.warn("Could not load acronyms from synonyms set '{}', keeping previous list: {}", synonymSetName, e.getMessage());
            }
        }
        catch (Exception e) {
            log.warn("Could not load acronyms from synonyms set '{}', keeping previous list: {}", synonymSetName, e.getMessage());
        }
    }

    /**
     * @param term - A search keyword, e.g. "soop temperature"
     * @return true if the keyword contains a known acronym, or if the acronym list could not be loaded
     */
    public boolean mayExpand(String term) {
        Set<String> known = acronyms;
        if (known == null) {
            return true;
        }
        String padded = " " + normalise(term) + " ";
        return known.stream().anyMatch(acronym -> padded.contains(" " + acronym + " "));
    }

    /**
     * Close enough to the ES standard tokenizer plus lowercase for acronym matching, so a multi-token acronym
     * still matches as a sequence.
     * in: "SOOP's (AAD) data" -> "soop s aad data"
     */
    protected static String normalise(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
