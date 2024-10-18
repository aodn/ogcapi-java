package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
public class DataRecordModel {

    private String time;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal depth;
}
