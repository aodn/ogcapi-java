package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
@Getter
@Setter
public class EsPointModel implements EsGeometry{
    protected List<BigDecimal> coordinates;

    @Override
    public PointGeoJSON toGeoJson() {
        PointGeoJSON p = new PointGeoJSON();
        p.setCoordinates(coordinates);
        return p;
    }
}
