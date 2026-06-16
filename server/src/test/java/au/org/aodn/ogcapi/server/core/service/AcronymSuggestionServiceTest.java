package au.org.aodn.ogcapi.server.core.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static au.org.aodn.ogcapi.server.core.service.AcronymSuggestionService.addRuleToDictionary;
import static au.org.aodn.ogcapi.server.core.service.AcronymSuggestionService.matchByPrefix;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the acronym-suggestion business logic as plain input -> output (no Elasticsearch involved):
 * a dictionary of "acronym => full name" rules in, the dropdown labels out.
 */
class AcronymSuggestionServiceTest {

    private static final Map<String, String> DICTIONARY = dictionaryOf(
            "aa => aurora australis",
            "aad => australian antarctic division",
            "aadc => australian antarctic data centre");

    @Test
    void typingAnAcronymPrefixSuggestsEveryMatchingFullName() {
        assertEquals(
                List.of("Aurora Australis", "Australian Antarctic Division", "Australian Antarctic Data Centre"),
                matchByPrefix(DICTIONARY, "aa"));

        assertEquals(
                List.of("Australian Antarctic Division", "Australian Antarctic Data Centre"),
                matchByPrefix(DICTIONARY, "aad"));
    }

    @Test
    void typedAcronymMatchesRegardlessOfCase() {
        assertEquals(matchByPrefix(DICTIONARY, "aad"), matchByPrefix(DICTIONARY, "AAD"));
    }

    @Test
    void blankInputSuggestsNothing() {
        assertTrue(matchByPrefix(DICTIONARY, "").isEmpty());
        assertTrue(matchByPrefix(DICTIONARY, "   ").isEmpty());
        assertTrue(matchByPrefix(DICTIONARY, null).isEmpty());
    }

    @Test
    void aRuleWithoutTheArrowSeparatorIsIgnored() {
        Map<String, String> dictionary = dictionaryOf(
                "aad => australian antarctic division",
                "this is not a valid rule");

        assertEquals(List.of("aad"), List.copyOf(dictionary.keySet()));
    }

    /** Build the acronym -> full name dictionary from rule strings, exactly as the service does from ES. */
    private static Map<String, String> dictionaryOf(String... rules) {
        Map<String, String> dictionary = new LinkedHashMap<>();
        for (String rule : rules) {
            addRuleToDictionary(rule, dictionary);
        }
        return dictionary;
    }
}
