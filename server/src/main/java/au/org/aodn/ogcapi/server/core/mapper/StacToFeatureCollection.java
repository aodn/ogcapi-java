package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import au.org.aodn.ogcapi.server.core.model.StacItemModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import org.mapstruct.Mapper;
import org.opengis.filter.Filter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToFeatureCollection implements Converter<ElasticSearch.SearchResult<StacItemModel>, FeatureCollectionGeoJSON> {

    @Override
    public FeatureCollectionGeoJSON convert(ElasticSearch.SearchResult<StacItemModel> model, Filter filter) {
        FeatureCollectionGeoJSON f = new FeatureCollectionGeoJSON();
        f.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);

        List<FeatureGeoJSON> features = model.getCollections().parallelStream()
                .map(i -> {
                    FeatureGeoJSON feature = new FeatureGeoJSON();
                    feature.setType(FeatureGeoJSON.TypeEnum.FEATURE);

                    if(i.getGeometry().get("geometry") instanceof Map<?, ?> map) {
                        if(map.get("coordinates") instanceof List<?> coords) {
                            List<BigDecimal> c = coords.stream()
                                    .filter(item -> item instanceof BigDecimal)
                                    .map(item -> (BigDecimal)item)
                                    .toList();

                            feature.setGeometry(new PointGeoJSON().type(PointGeoJSON.TypeEnum.POINT).coordinates(c));
                        }
                    }
                    feature.setProperties(i.getProperties());

                    return feature;
                })
                .filter(i -> i.getGeometry() != null)
                .toList();

        f.setFeatures(features);
        return f;
    }
}
