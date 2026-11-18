package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.util.ConstructUtils;
import au.org.aodn.stac.model.CitationModel;
import au.org.aodn.stac.model.StacCollectionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the suggested citation and usage constraints of a download from the Elasticsearch
 * record, so the request body carries no long metadata text.
 * The portal download dialog builds the same citation and constraints (CitationUtils.ts in
 * aodn-portal-v2). Keep the two rules the same.
 */
@Service
public class DownloadCitationService {

    private static final String SUMMARY_TAB = "?tab=summary";

    private static final Pattern YEAR_PLACEHOLDER =
            Pattern.compile("\\[year-of-data-download(?:ed)?]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PLACEHOLDER =
            Pattern.compile("\\[title]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCESS_URL_PLACEHOLDER =
            Pattern.compile("\\[data-access-url]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCESS_DATE_PLACEHOLDER =
            Pattern.compile("\\[date-of-access]", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter ACCESS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    // Same as the JavaScript \s the portal uses, which also matches non-breaking spaces
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final Search search;
    // UTC, the same time zone the portal uses for the year and the date of access
    private final Clock clock;

    @Autowired
    public DownloadCitationService(Search search) {
        this(search, Clock.systemUTC());
    }

    DownloadCitationService(Search search, Clock clock) {
        this.search = search;
        this.clock = clock;
    }

    /**
     * The values sent to AWS Batch as suggested_citation and usage_constraints.
     */
    public record DownloadCitation(
            String suggestedCitation,
            List<String> usageConstraints
    ) {
    }

    /**
     * Look up the dataset by uuid and build its citation and constraints.
     * fullMetadataLink is the portal page link from the request, used for [data-access-url].
     * Throws NoSuchElementException when the uuid is not in Elasticsearch.
     */
    public DownloadCitation lookup(String uuid, String fullMetadataLink) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("Missing uuid");
        }

        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(uuid);
        if (result == null || result.getCollections() == null || result.getCollections().isEmpty()) {
            throw new NoSuchElementException("No collection found for uuid " + uuid);
        }

        StacCollectionModel model = result.getCollections().get(0);
        String title = model.getTitle() == null ? "" : model.getTitle();
        CitationModel citation = parseCitation(model.getCitation());
        // The portal built the same url: its details page link plus the summary tab
        String dataAccessUrl = fullMetadataLink == null || fullMetadataLink.isEmpty()
                ? ""
                : fullMetadataLink + SUMMARY_TAB;

        return new DownloadCitation(
                resolveSuggestedCitation(citation.getSuggestedCitation(), title, dataAccessUrl),
                usageConstraints(citation, model.getLicense())
        );
    }

    private static CitationModel parseCitation(String json) {
        if (json == null || json.isEmpty()) {
            return new CitationModel();
        }
        return ConstructUtils.constructByJsonString(json, CitationModel.class).orElseGet(CitationModel::new);
    }

    // Fill the placeholders, in the same order as the portal. Empty when there is no citation.
    private String resolveSuggestedCitation(String suggestedCitation, String title, String dataAccessUrl) {
        if (suggestedCitation == null || suggestedCitation.isEmpty()) {
            return "";
        }

        LocalDate accessDate = LocalDate.now(clock);

        String citation = replaceAll(YEAR_PLACEHOLDER, suggestedCitation, String.valueOf(accessDate.getYear()));
        citation = replaceAll(TITLE_PLACEHOLDER, citation, title);
        citation = replaceAll(ACCESS_URL_PLACEHOLDER, citation, dataAccessUrl);
        return replaceAll(ACCESS_DATE_PLACEHOLDER, citation, ACCESS_DATE_FORMAT.format(accessDate));
    }

    // The value is used as plain text, so a $ or \ in a title is not read as a group reference
    private static String replaceAll(Pattern placeholder, String text, String value) {
        return placeholder.matcher(text).replaceAll(Matcher.quoteReplacement(value));
    }

    /**
     * Use limitations then other constraints, with these removed:
     * 1. Empty items.
     * 2. An item that equals the licence.
     * 3. Duplicates, the first one is kept.
     * Items are compared after trim, single spaces and lower case.
     */
    private static List<String> usageConstraints(CitationModel citation, String license) {
        String normalizedLicense = license == null ? "" : normalize(license);
        Set<String> seen = new HashSet<>();
        List<String> constraints = new ArrayList<>();

        Stream.concat(
                        Objects.requireNonNullElse(citation.getUseLimitations(), List.<String>of()).stream(),
                        Objects.requireNonNullElse(citation.getOtherConstraints(), List.<String>of()).stream())
                .filter(Objects::nonNull)
                .forEach(constraint -> {
                    String normalized = normalize(constraint);
                    if (!normalized.isEmpty() && !normalized.equals(normalizedLicense) && seen.add(normalized)) {
                        constraints.add(constraint);
                    }
                });

        return constraints;
    }

    private static String normalize(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }
}
