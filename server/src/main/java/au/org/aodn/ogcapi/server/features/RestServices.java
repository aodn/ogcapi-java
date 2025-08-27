package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.service.DuckDB;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadableFieldsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import au.org.aodn.ogcapi.features.model.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@Service("FeaturesRestService")
public class RestServices extends OGCApiService {

    @Autowired
    protected StacToCollection StacToCollection;

    @Autowired
    protected DownloadableFieldsService downloadableFieldsService;

    @Override
    public List<String> getConformanceDeclaration() {
        return List.of("http://www.opengis.net/doc/IS/ogcapi-features-1/1.0.1");
    }

    public ResponseEntity<Collection> getCollection(String id, String sortBy) throws NoSuchElementException {
        ElasticSearch.SearchResult<StacCollectionModel> model = search.searchCollections(List.of(id), sortBy);

        if (!model.getCollections().isEmpty()) {
            if (model.getCollections().size() > 1) {
                log.error("UUID {} found in multiple records ", id);
            }

            return ResponseEntity.ok()
                    .body(StacToCollection.convert(model.getCollections().get(0), null));
        } else {
            log.error("UUID {} not found", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get downloadable fields for a layer
     *
     * @param wfsUrl   The WFS server URL
     * @param typeName The WFS type name
     * @return List of downloadable fields
     */
    public ResponseEntity<List<DownloadableFieldModel>> getDownloadableFields(String wfsUrl, String typeName) {
        List<DownloadableFieldModel> fields = downloadableFieldsService.getDownloadableFields(wfsUrl, typeName);
        return ResponseEntity.ok(fields);
    }

    public ResponseEntity<FeatureCollectionGeoJSON> getFeatures(String from) {
        String to = null;
        if (from != null) {
            java.time.LocalDate localDate = java.time.LocalDate.parse(from);
            to = localDate.plusDays(1).toString();
        }
        try {
            long startTime = System.currentTimeMillis();
            long queryStart = System.currentTimeMillis();
            String sql = "SELECT \n" +
                    "        site_name,\n" +
                    "        first(TIME) as TIME,\n" +
                    "        first(LATITUDE) AS LATITUDE,\n" +
                    "        first(LONGITUDE) AS LONGITUDE\n" +
                    "        FROM 'https://gtrrz-victor-testing-bucket.s3.ap-southeast-2.amazonaws.com/db_wave_buoy_realtime_nonqc.parquet'\n"
                    +
                    "        WHERE TIME >= ? AND TIME < ? \n" +
                    "        GROUP BY site_name";
            java.sql.PreparedStatement pstmt = DuckDB.getConnection().prepareStatement(sql);
            pstmt.setString(1, from);
            pstmt.setString(2, to);
            ResultSet result = pstmt.executeQuery();
            long queryTime = System.currentTimeMillis() - queryStart;
            log.info("Query execution time: " + queryTime + "ms");

            long processingStart = System.currentTimeMillis();
            List<FeatureGeoJSON> features = new ArrayList<>();
            int featureCount = 0;
            while (result.next()) {
                featureCount++;
                String siteName = result.getString("site_name");
                BigDecimal latitude = result.getBigDecimal("LATITUDE");
                BigDecimal longitude = result.getBigDecimal("LONGITUDE");

                PointGeoJSON geometry = new PointGeoJSON();
                geometry.setType(PointGeoJSON.TypeEnum.POINT);
                geometry.setCoordinates(Arrays.asList(longitude, latitude));

                Map<String, Object> properties = new HashMap<>();
                properties.put("buoy", siteName);
                properties.put("date", result.getDate("TIME"));

                FeatureGeoJSON feature = new FeatureGeoJSON();
                feature.setType(FeatureGeoJSON.TypeEnum.FEATURE);
                feature.setGeometry(geometry);
                feature.setProperties(properties);
                features.add(feature);
            }
            long processingTime = System.currentTimeMillis() - processingStart;
            log.info("Result processing time: " + processingTime + "ms (processed " + featureCount
                    + " features)");

            FeatureCollectionGeoJSON featureCollection = new FeatureCollectionGeoJSON();
            featureCollection.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);
            featureCollection.setFeatures(features);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Total getFeatures action time: " + totalTime + "ms");

            return ResponseEntity.ok().body(featureCollection);
        } catch (java.lang.Exception e) {
            log.error("Error fetching features: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
