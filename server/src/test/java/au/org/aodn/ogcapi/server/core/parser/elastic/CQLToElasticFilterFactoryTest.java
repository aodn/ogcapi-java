package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLElasticSetting;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.jupiter.api.Test;
import org.opengis.filter.Filter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CQLToElasticFilterFactoryTest {

    @Test
    public void parameterVocabFilterEnablesPrioritySortAndCollectsTerms() throws CQLException {
        String cql = "parameter_vocabs='acoustics' OR ai_parameter_vocabs='acoustics' OR "
                + "parameter_vocabs='aerosols' OR ai_parameter_vocabs='aerosols' OR "
                + "parameter_vocabs='air pressure' OR ai_parameter_vocabs='air pressure'";
        CQLToElasticFilterFactory<CQLFields> factory = newFactory();
        Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);

        assertTrue(factory.isParameterPrioritySort());
        assertEquals(
                Set.of("acoustics", "aerosols", "air pressure"),
                factory.getParameterPrioritySortTerms());
        assertFalse(factory.isPlatformPrioritySort());
        assertTrue(factory.getPlatformPrioritySortTerms().isEmpty());

        OrImpl parameterFilter = assertInstanceOf(OrImpl.class, filter);
        assertTrue(parameterFilter.getQuery().isBool());
        assertEquals(6, parameterFilter.getQuery().bool().should().size());
        assertTrue(
                parameterFilter.getQuery().bool().should().stream().noneMatch(query -> query.isBool()),
                "Parameter vocabulary clauses should be flattened into one should list");
    }

    @Test
    public void platformVocabFilterEnablesPrioritySortAndCollectsTerms() throws CQLException {
        CQLToElasticFilterFactory<CQLFields> factory = parse(
                "platform_vocabs='glider' OR platform_vocabs='mooring'");

        assertTrue(factory.isPlatformPrioritySort());
        assertEquals(Set.of("glider", "mooring"), factory.getPlatformPrioritySortTerms());
        assertFalse(factory.isParameterPrioritySort());
        assertTrue(factory.getParameterPrioritySortTerms().isEmpty());
    }

    @Test
    public void duplicateVocabTermsAreCollectedOnce() throws CQLException {
        CQLToElasticFilterFactory<CQLFields> factory = parse(
                "(parameter_vocabs='temperature' OR ai_parameter_vocabs='temperature') OR "
                        + "(parameter_vocabs='temperature' OR ai_parameter_vocabs='temperature')");

        assertEquals(Set.of("temperature"), factory.getParameterPrioritySortTerms());
    }

    @Test
    public void aiAndUnrelatedFiltersDoNotEnablePrioritySort() throws CQLException {
        CQLToElasticFilterFactory<CQLFields> factory = parse(
                "ai_parameter_vocabs='temperature' OR "
                        + "ai_platform_vocabs='glider' OR status='ongoing'");

        assertFalse(factory.isParameterPrioritySort());
        assertTrue(factory.getParameterPrioritySortTerms().isEmpty());
        assertFalse(factory.isPlatformPrioritySort());
        assertTrue(factory.getPlatformPrioritySortTerms().isEmpty());
    }

    @Test
    public void prioritySortMetadataIsCollectedAlongsideQuerySettings() throws CQLException {
        CQLToElasticFilterFactory<CQLFields> factory = parse(
                "page_size=11 AND "
                        + "(parameter_vocabs='heat budget' OR ai_parameter_vocabs='heat budget') "
                        + "AND platform_vocabs='glider'");

        assertEquals("11", factory.getQuerySetting().get(CQLElasticSetting.page_size));
        assertTrue(factory.isParameterPrioritySort());
        assertEquals(Set.of("heat budget"), factory.getParameterPrioritySortTerms());
        assertTrue(factory.isPlatformPrioritySort());
        assertEquals(Set.of("glider"), factory.getPlatformPrioritySortTerms());
    }

    private CQLToElasticFilterFactory<CQLFields> parse(String cql) throws CQLException {
        CQLToElasticFilterFactory<CQLFields> factory = newFactory();
        CompilerUtil.parseFilter(Language.ECQL, cql, factory);
        return factory;
    }

    private CQLToElasticFilterFactory<CQLFields> newFactory() {
        return new CQLToElasticFilterFactory<>(CQLCrsType.EPSG4326, CQLFields.class);
    }
}
