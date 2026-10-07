package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.synonyms.ElasticsearchSynonymsClient;
import co.elastic.clients.elasticsearch.synonyms.GetSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.GetSynonymResponse;
import co.elastic.clients.elasticsearch.synonyms.SynonymRuleRead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AcronymLookupTest {

    private ElasticsearchSynonymsClient synonymsClient;
    private AcronymLookup lookup;

    @BeforeEach
    public void setUp() {
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        synonymsClient = mock(ElasticsearchSynonymsClient.class);
        when(client.synonyms()).thenReturn(synonymsClient);
        lookup = new AcronymLookup(client, "portal-acronyms-test");
    }

    @Test
    public void ruleLeftSidesBecomeAcronyms() throws IOException {
        givenRules("soop => ships of opportunity", "aad, AADX => australian antarctic division");
        lookup.refresh();

        assertEquals(3, lookup.acronyms.size());
        assertTrue(lookup.acronyms.containsAll(List.of("soop", "aad", "aadx")));
    }

    @Test
    public void keywordWithAcronymMayExpand() throws IOException {
        givenRules("soop => ships of opportunity", "aad => australian antarctic division");
        lookup.refresh();

        assertTrue(lookup.mayExpand("soop"));
        assertTrue(lookup.mayExpand("SOOP temperature"), "an acronym among other words still expands");
        assertTrue(lookup.mayExpand("soop's data"), "a possessive does not hide the acronym");
        assertTrue(lookup.mayExpand("(AAD)"));
    }

    @Test
    public void keywordWithoutAcronymDoesNotExpand() throws IOException {
        givenRules("soop => ships of opportunity");
        lookup.refresh();

        assertFalse(lookup.mayExpand("ships of opportunity"), "the full name is plain text");
        assertFalse(lookup.mayExpand("coastal wave data"));
        assertFalse(lookup.mayExpand("soopy"), "only whole tokens are acronyms");
    }

    @Test
    public void missingSynonymsSetMeansNoAcronym() throws IOException {
        when(synonymsClient.getSynonym(any(GetSynonymRequest.class))).thenThrow(error(404));
        lookup.refresh();

        assertFalse(lookup.mayExpand("soop"));
    }

    @Test
    public void failedLoadKeepsEveryKeywordExpandable() throws IOException {
        when(synonymsClient.getSynonym(any(GetSynonymRequest.class)))
                .thenThrow(error(403))
                .thenThrow(new IOException("connection refused"));

        lookup.refresh();
        assertTrue(lookup.mayExpand("coastal wave data"), "unknown list must not drop acronym expansion");

        lookup.refresh();
        assertTrue(lookup.mayExpand("coastal wave data"));
    }

    @Test
    public void failedRefreshKeepsPreviousList() throws IOException {
        givenRules("soop => ships of opportunity");
        lookup.refresh();

        when(synonymsClient.getSynonym(any(GetSynonymRequest.class))).thenThrow(error(503));
        lookup.refresh();

        assertTrue(lookup.mayExpand("soop"));
        assertFalse(lookup.mayExpand("coastal wave data"));
    }

    @Test
    public void normaliseFollowsStandardTokenizer() {
        assertEquals("soop s aad data", AcronymLookup.normalise("SOOP's (AAD) data"));
        assertEquals("", AcronymLookup.normalise("  ,  "));
    }

    private void givenRules(String... rules) throws IOException {
        List<SynonymRuleRead> set = Arrays.stream(rules)
                .map(rule -> new SynonymRuleRead.Builder().id(rule).synonyms(rule).build())
                .toList();
        when(synonymsClient.getSynonym(any(GetSynonymRequest.class)))
                .thenReturn(GetSynonymResponse.of(r -> r.count(set.size()).synonymsSet(set)));
    }

    private static ElasticsearchException error(int status) {
        return new ElasticsearchException("synonyms.get_synonym", ErrorResponse.of(e -> e
                .status(status)
                .error(c -> c.type("error").reason("status " + status))));
    }
}
