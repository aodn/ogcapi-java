package au.org.aodn.ogcapi.server.core.parser.stac;

import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.server.core.mapper.Converter;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class ParserTest {

    protected static CQLToStacFilterFactory factory = CQLToStacFilterFactory
            .builder()
            .cqlCrsType(CQLCrsType.EPSG4326)
            .build();
    protected static ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    public static void init() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        factory.setCqlCrsType(CQLCrsType.EPSG4326);
    }
    /**
     * Verify CQL parse for STAC, this parse is used to apply after STAC created, at this time we have a very
     * specific use case to generate the centroid point dynamically. This is because it is too costly to generate
     * and store in the elastic search.
     * We created a spatial extents with no land area. Then we need to consider the CQL INTERSECTION form the query
     * and then apply the INTERSECTION to this spatial extents. By doing so we do not need to consider zoom level
     * but able to create one centroid that falls within the visible area.
     * @throws CQLException - Will not throw
     * @throws IOException - Will not throw
     * @throws FactoryException - Will not throw
     * @throws TransformException - Will not throw
     * @throws ParseException - Will not throw
     */
    @Test
    public void verifyIntersectionWorks() throws CQLException, IOException, FactoryException, TransformException, ParseException {
        // Assume we have the following CQL
        Converter.Param param = Converter.Param.builder()
                .coordinationSystem(CQLCrsType.EPSG4326)
                .filter("score>=1.5 AND INTERSECTS(geometry,POLYGON ((106.60546875000024 -40.43564679957577, 167.42578125 -40.43564679957577, 167.42578125 -6.4339250592726005, 106.60546875000024 -6.4339250592726005, 106.60546875000024 -40.43564679957577)))")
                .build();

        Optional<Geometry> expected = GeometryUtils.readGeometry(
                GeometryUtils.convertToGeoJson(
                        new LiteralExpressionImpl(
                                "POLYGON ((116 -34.84004284124928, 116 -36, 112 -36, 112 -27, 113.78063324619302 -27, 114.01254316500001 -27.31536223799992, 114.14389082100001 -27.687758070999905, 114.09791100400003 -27.862725518999923, 114.16602623800009 -28.10670338299991, 114.53109785200002 -28.52239348799992, 114.60377037900003 -28.838555596999925, 114.84245853000004 -29.108575127999927, 114.97877037900003 -29.479913018999923, 114.94263756600003 -30.031019789999903, 115.06080162900003 -30.52239348799992, 115.72152754000001 -31.772637627999927, 115.75684655000009 -32.18564218499995, 115.67367597700002 -32.273370049999926, 115.73585045700008 -32.33318450299993, 115.71452884200005 -32.537041924999926, 115.76929772200003 -32.60230885199991, 115.65886478000004 -32.62851327899995, 115.71412194100003 -32.78045012799993, 115.63965905000009 -32.656914971999925, 115.70093834700003 -32.51295338299991, 115.60092207100001 -32.66171640399995, 115.66684004000001 -33.28769296699994, 115.69792728000004 -33.1931291649999, 115.71013431100005 -33.27068450299993, 115.36719811300009 -33.63128020599993, 115.19467207100001 -33.64462655999995, 114.98853600400003 -33.52109140399995, 115.01042728000004 -34.24504973799992, 115.11882571700005 -34.36337655999995, 115.29078209700003 -34.301853122999944, 115.61231530000009 -34.44589609199994, 115.91578209700003 -34.70191822699991, 115.97852623800009 -34.83562590899993, 116 -34.84004284124928), (115.49210733500001 -31.99564901299993, 115.44014224400006 -32.021833949999916, 115.55219218900004 -32.00620348499996, 115.49210733500001 -31.99564901299993))"),
                        CQLCrsType.EPSG4326
                )
        );

        // Parse the json and get the noland section
        String json = BaseTestClass.readResourceFile("classpath:databag/00462296-be7a-452f-afaf-36c809cd51f8.json");
        StacCollectionModel model = mapper.readValue(json, StacCollectionModel.class);

        Filter filter = CompilerUtil.parseFilter(Language.CQL, param.getFilter(), factory);
        Optional<Geometry> geo = GeometryUtils.readGeometry(model.getSummaries().getGeometryNoLand());

        Assertions.assertTrue(geo.isPresent(), "Parse no land correct");
        GeometryVisitor visitor = GeometryVisitor.builder()
                .build();

        // return value are geo applied the CQL, and in this case only INTERSECTS
        Geometry g = (Geometry)filter.accept(visitor, geo.get());
        Assertions.assertTrue(expected.isPresent(), "Expected parse correct");
        Assertions.assertEquals(g, expected.get(), "They are equals");
    }
}
