package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SummariesModel {
    protected int score;
    protected String status;
    protected List<TemporalModel> temporal;
}
