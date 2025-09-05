package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.PolygonGeoJSON;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class EsPolygonModel implements EsGeometry {
    protected List<List<List<BigDecimal>>> coordinates;

    @Override
    public PolygonGeoJSON toGeoJson() {
        PolygonGeoJSON p =  new PolygonGeoJSON();
        p.setCoordinates(coordinates);
        return p;
    }
}
