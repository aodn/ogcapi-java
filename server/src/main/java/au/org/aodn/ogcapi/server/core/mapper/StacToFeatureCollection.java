package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import au.org.aodn.ogcapi.server.core.model.StacItemModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import com.fasterxml.jackson.core.type.TypeReference;
import org.mapstruct.Mapper;
import org.opengis.filter.Filter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static au.org.aodn.ogcapi.server.core.util.CommonUtils.safeGet;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToFeatureCollection implements Converter<ElasticSearch.SearchResult<StacItemModel>, FeatureCollectionGeoJSON> {

    @Override
    public FeatureCollectionGeoJSON convert(ElasticSearch.SearchResult<StacItemModel> model, Filter filter) {
        FeatureCollectionGeoJSON f = new FeatureCollectionGeoJSON();
        f.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);

        List<FeatureGeoJSON> features = model.getCollections().stream()
                .map(i -> {
                    FeatureGeoJSON feature = new FeatureGeoJSON();
                    feature.setType(FeatureGeoJSON.TypeEnum.FEATURE);

                    safeGet(() -> ((Map<String,List<BigDecimal>>)i.getGeometry().get("geometry")).get("coordinates"))
                            .ifPresent(g ->
                                feature.setGeometry(new PointGeoJSON()
                                        .type(PointGeoJSON.TypeEnum.POINT)
                                        .coordinates(g)
                            ));

                    feature.setProperties(i.getProperties());

                    return feature;
                })
                .toList();

        f.setFeatures(features);
        return f;
    }
}
