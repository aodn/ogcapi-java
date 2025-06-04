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
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
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
     * Test almost the same as the verifyIntersectionWorks1, since verifyIntersectionWorks1 create a polygon same as box
     * so use Intersect or BBox will result in same result
     *
     * @throws CQLException - Will not throw
     * @throws IOException - Will not throw
     * @throws FactoryException - Will not throw
     * @throws TransformException - Will not throw
     * @throws ParseException - Will not throw
     */
    @Test
    public void verifyBBoxWorks1() throws CQLException, IOException, FactoryException, TransformException, ParseException {
        // Assume we have the following CQL
        Converter.Param param = Converter.Param.builder()
                .coordinationSystem(CQLCrsType.EPSG4326)
                .filter("score>=1.5 AND BBOX(geometry,106.60546875000024,-40.43564679957577,167.42578125,-6.4339250592726005)")
                .build();
        // Expected polygon shape after the no land area  intersection with the BBOX
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
    /**
     * Test almost the same as the verifyIntersectionWorks2, since verifyIntersectionWorks1 create a polygon same as box
     * so use Intersect or BBox will result in same result
     *
     * @throws CQLException - Will not throw
     * @throws IOException - Will not throw
     */
    @Test
    public void verifyBBoxWorks2() throws CQLException, IOException {

        // Parse the json and get the noland section
        String json = BaseTestClass.readResourceFile("classpath:databag/0015db7e-e684-7548-e053-08114f8cd4ad.json");
        StacCollectionModel model = mapper.readValue(json, StacCollectionModel.class);

        Filter filter = CompilerUtil.parseFilter(
                Language.CQL,
                "score>=1.5 AND BBOX(geometry,-203.16603491348164,-60.248194404495756,-86.85117538227594,15.902738674628525)",
                factory);

        Optional<Geometry> geo = GeometryUtils.readGeometry(model.getSummaries().getGeometryNoLand());

        Assertions.assertTrue(geo.isPresent(), "Parse no land correct");
        GeometryVisitor visitor = GeometryVisitor.builder()
                .build();

        // return value are geo applied the CQL, and in this case only BBOX intersected
        Geometry g = (Geometry)filter.accept(visitor, geo.get());

        Assertions.assertFalse(g.isEmpty());
        Assertions.assertInstanceOf(Polygon.class, g);

        Assertions.assertEquals(168.30090846621448, g.getCentroid().getX(), 0.0000001, "getX()");
        Assertions.assertEquals(-33.95984804960966, g.getCentroid().getY(), 0.0000001, "getY()");
    }
    /**
     * Similar test as verifyBBoxWorks2, the BBOX not only cross meridian but the sample json have spatial extents
     * near equator and span across the whole world
     *
     * @throws CQLException - Will not throw
     * @throws IOException - Will not throw
     */
    @Test
    public void verifyBBoxWorks3() throws CQLException, IOException {

        // Parse the json and get the noland section
        String json = BaseTestClass.readResourceFile("classpath:databag/c9055fe9-921b-44cd-b4f9-a00a1c93e8ac.json");
        StacCollectionModel model = mapper.readValue(json, StacCollectionModel.class);

        Filter filter = CompilerUtil.parseFilter(
                Language.CQL,
                "score>=1.5 AND BBOX(geometry,-209.8851491167079,-45.44715475181477,-149.06483661670887,-5.632766095762394)",
                factory);

        Optional<Geometry> geo = GeometryUtils.readGeometry(model.getSummaries().getGeometryNoLand());

        Assertions.assertTrue(geo.isPresent(), "Parse no land correct");
        GeometryVisitor visitor = GeometryVisitor.builder()
                .build();

        // return value are geo applied the CQL, and in this case only BBOX intersected
        Geometry g = (Geometry)filter.accept(visitor, geo.get());

        Assertions.assertInstanceOf(MultiPolygon.class, g);

        MultiPolygon mp = (MultiPolygon)g;
        Assertions.assertEquals(2, mp.getNumGeometries(), "Geometries correct");

        Assertions.assertEquals(-159.53241830835444, mp.getGeometryN(1).getCentroid().getX(), 0.0000001, "getX() for 0");
        Assertions.assertEquals(-19.5, mp.getGeometryN(1).getCentroid().getY(), 0.0000001, "getY() for 0");

        Assertions.assertEquals(151.62121416760516, mp.getGeometryN(0).getCentroid().getX(), 0.0000001, "getX() for 1");
        Assertions.assertEquals(-18.000822620336752, mp.getGeometryN(0).getCentroid().getY(),  0.0000001, "getY() for 1");
    }

    @Test
    public void verifyIntersectWorks1() throws CQLException, IOException, FactoryException, TransformException, ParseException {
        // Assume we have the following CQL
        Converter.Param param = Converter.Param.builder()
                .coordinationSystem(CQLCrsType.EPSG4326)
                .filter("score>=1.5 AND  INTERSECTS(geometry,POLYGON ((145.16909105878426 -10.464349225802914, 144.73440233886106 -9.848517493899884, 144.47064456419093 -9.657695528909853, 144.46280593719746 -9.905001382894994, 144.2060197558053 -10.242037054678411, 144.16893528215599 -10.457443951550495, 144.0000000000374 -10.46518242537087, 144.0000000000374 -10.686536176266213, 144.9994690420176 -10.681893446539034, 145.00106191494456 -12.998524118431453, 146.00107091314112 -14.998515117696627, 147.00107091515906 -17.498488120437123, 149.96308495492246 -19.293403529258228, 152.91580273613386 -20.997390209369716, 154.00000443319107 -24.495283451153206, 158.33154102878632 -24.498407105902825, 157.8012592598626 -23.248298763101843, 157.31004039489108 -22.519537254185753, 157.21996792100708 -22.18289466451383, 157.24246410497653 -21.781057076406388, 157.17153685914286 -21.215798230336727, 157.05264790906187 -20.54107583454862, 156.82625898816468 -20.141075746345383, 156.2603178109748 -19.293585408462032, 156.63014151780646 -18.925219368232813, 156.94570327990823 -18.540241996336075, 158.3761729881353 -16.430664685375895, 158.76098074418962 -15.735241194712154, 157.72268453595484 -14.68925351658542, 157.25646824177636 -14.286426921513115, 156.97026318293013 -14.177729011764056, 156.67405154112384 -14.093699797756436, 156.61349189679618 -14.08296197760852, 154.2550937971438 -14.747312889369214, 152.12620732406822 -14.63230005662328, 150.23373673986976 -13.961229062351725, 148.08837935213808 -13.174974098634262, 147.1427175218439 -12.640158093580235, 146.50484986992365 -12.33365784677699, 145.16909105878426 -10.464349225802914)))")
                .build();
        // Expected polygon shape after the no land area intersection of the Intersect polygon
        Optional<Geometry> expected = GeometryUtils.readGeometry(
                GeometryUtils.convertToGeoJson(
                        new LiteralExpressionImpl(
                                "POLYGON ((144.73440233886106 -9.848517493899884, 145.16909105878426 -10.464349225802914, 146.50484986992365 -12.33365784677699, 147.1427175218439 -12.640158093580235, 148.08837935213808 -13.174974098634262, 150.23373673986976 -13.961229062351727, 152.12620732406822 -14.63230005662328, 154.2550937971438 -14.747312889369214, 156.6134918967962 -14.08296197760852, 156.67405154112384 -14.093699797756436, 156.97026318293013 -14.177729011764056, 157.25646824177636 -14.286426921513115, 157.72268453595484 -14.68925351658542, 158.76098074418962 -15.735241194712154, 158.3761729881353 -16.430664685375895, 156.94570327990823 -18.540241996336075, 156.63014151780646 -18.925219368232813, 156.2603178109748 -19.293585408462032, 156.82625898816468 -20.141075746345383, 157.05264790906187 -20.54107583454862, 157.17153685914286 -21.215798230336727, 157.24246410497653 -21.781057076406388, 157.21996792100708 -22.18289466451383, 157.31004039489108 -22.519537254185753, 157.8012592598626 -23.248298763101843, 158.3315410287863 -24.498407105902825, 154.00000443319107 -24.49528345115321, 152.91580273613386 -20.997390209369716, 149.96308495492246 -19.293403529258228, 147.00107091515906 -17.498488120437123, 146.00107091314112 -14.998515117696625, 145.00106191494456 -12.998524118431453, 144.9994690420176 -10.681893446539034, 144.0000000000374 -10.686536176266213, 144.0000000000374 -10.46518242537087, 144.168935282156 -10.457443951550495, 144.2060197558053 -10.24203705467841, 144.46280593719746 -9.905001382894994, 144.47064456419093 -9.657695528909853, 144.73440233886106 -9.848517493899884))"),
                        CQLCrsType.EPSG4326
                )
        );

        // Parse the json and get the noland section
        String json = BaseTestClass.readResourceFile("classpath:databag/e26d0a56-5603-4413-911d-7b359a533a75.json");
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
