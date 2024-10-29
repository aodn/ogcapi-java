package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DatumModel {

    private String time;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal depth;

    private long count;
}
