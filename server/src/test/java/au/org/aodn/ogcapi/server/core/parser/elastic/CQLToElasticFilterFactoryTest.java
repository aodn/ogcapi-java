package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLElasticSetting;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.jupiter.api.Test;
import org.opengis.filter.Filter;

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
}
