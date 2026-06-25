package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLElasticSetting;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacSummeries;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.jupiter.api.Test;
import org.opengis.filter.Filter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CQLToElasticFilterFactoryTest {

    @Test
    public void parameterVocabFilterEnablesPrioritySort() throws CQLException {
        String cql = "(parameter_vocabs='acoustics' OR ai_parameter_vocabs='acoustics') OR "
                + "(parameter_vocabs='aerosols' OR ai_parameter_vocabs='aerosols') OR "
                + "(parameter_vocabs='air pressure' OR ai_parameter_vocabs='air pressure')";
        CQLToElasticFilterFactory<CQLFields> factory = newFactory();
        Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);

        assertTrue(factory.isParameterPrioritySort());
        assertFalse(factory.isPlatformPrioritySort());

        OrImpl parameterFilter = assertInstanceOf(OrImpl.class, filter);
        assertTrue(parameterFilter.getQuery().isBool());
        assertEquals(6, parameterFilter.getQuery().bool().should().size());
        assertTrue(
                parameterFilter.getQuery().bool().should().stream().noneMatch(query -> query.isBool()),
                "Parameter vocabulary clauses should be flattened into one should list");
    }

    @Test
    public void platformVocabFilterEnablesPrioritySort() throws CQLException {
        String cql = "(platform_vocabs='satellite' OR ai_platform_vocabs='satellite') OR "
                + "(platform_vocabs='glider' OR ai_platform_vocabs='glider')";
        CQLToElasticFilterFactory<CQLFields> factory = newFactory();
        Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);

        assertTrue(factory.isPlatformPrioritySort());
        assertFalse(factory.isParameterPrioritySort());

        OrImpl platformFilter = assertInstanceOf(OrImpl.class, filter);
        assertTrue(platformFilter.getQuery().isBool());
        assertEquals(4, platformFilter.getQuery().bool().should().size());
        assertTrue(
                platformFilter.getQuery().bool().should().stream().noneMatch(query -> query.isBool()),
                "Grouped platform vocabulary clauses should be flattened into one should list");
    }

    @Test
    public void prioritySortMetadataIsCollectedAlongsideQuerySettings() throws CQLException {
        CQLToElasticFilterFactory<CQLFields> factory = parse(
                "page_size=11 AND "
                        + "((parameter_vocabs='heat budget' OR ai_parameter_vocabs='heat budget')) "
                        + "AND ((platform_vocabs='satellite' OR ai_platform_vocabs='satellite') OR "
                        + "(platform_vocabs='glider' OR ai_platform_vocabs='glider'))");

        assertEquals("11", factory.getQuerySetting().get(CQLElasticSetting.page_size));
        assertTrue(factory.isParameterPrioritySort());
        assertTrue(factory.isPlatformPrioritySort());
    }

    @Test
    public void temporalDuringUsesOverlapRangeQueryAndIncludesOngoingRecords() throws CQLException {
        Filter filter = CompilerUtil.parseFilter(
                Language.ECQL,
                "temporal DURING 2025-06-25T00:00:00Z/2026-06-25T00:00:00Z",
                newFactory());

        DuringImpl<?> duringFilter = assertInstanceOf(DuringImpl.class, filter);
        assertTrue(duringFilter.getQuery().isNested());

        List<Query> must = duringFilter.getQuery().nested().query().bool().must();
        assertEquals(2, must.size());

        Query startRange = findDateRange(must, StacSummeries.TemporalStart.searchField);
        assertEquals("strict_date_optional_time", startRange.range().date().format());
        assertTrue(startRange.range().date().lte().contains("2026-06-25"));

        Query endAfterFilterStartOrOngoing = must.stream()
                .filter(Query::isBool)
                .findFirst()
                .orElseThrow();
        assertEquals("1", endAfterFilterStartOrOngoing.bool().minimumShouldMatch());

        List<Query> should = endAfterFilterStartOrOngoing.bool().should();
        Query endRange = findDateRange(should, StacSummeries.TemporalEnd.searchField);
        assertEquals("strict_date_optional_time", endRange.range().date().format());
        assertTrue(endRange.range().date().gte().contains("2025-06-25"));

        assertTrue(should.stream()
                .filter(Query::isBool)
                .flatMap(query -> query.bool().mustNot().stream())
                .anyMatch(query -> query.isExists()
                        && StacSummeries.TemporalEnd.searchField.equals(query.exists().field())));
    }

    @Test
    public void querySettingsCannotBeCombinedWithOr() {
        IllegalArgumentException settingFirst = assertThrows(
                IllegalArgumentException.class,
                () -> parse("score>=2 OR parameter_vocabs='wave'"));
        assertEquals(
                "Or combine with query setting do not make sense",
                settingFirst.getMessage());

        IllegalArgumentException settingLast = assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "parameter_vocabs='wave' OR ai_parameter_vocabs='wave' OR score>=2"));
        assertEquals(
                "Or combine with query setting do not make sense",
                settingLast.getMessage());
    }

    private CQLToElasticFilterFactory<CQLFields> parse(String cql) throws CQLException {
        CQLToElasticFilterFactory<CQLFields> factory = newFactory();
        CompilerUtil.parseFilter(Language.ECQL, cql, factory);
        return factory;
    }

    private CQLToElasticFilterFactory<CQLFields> newFactory() {
        return new CQLToElasticFilterFactory<>(CQLCrsType.EPSG4326, CQLFields.class);
    }

    private Query findDateRange(List<Query> queries, String field) {
        return queries.stream()
                .filter(Query::isRange)
                .filter(query -> query.range().isDate())
                .filter(query -> field.equals(query.range().date().field()))
                .findFirst()
                .orElseThrow();
    }
}
