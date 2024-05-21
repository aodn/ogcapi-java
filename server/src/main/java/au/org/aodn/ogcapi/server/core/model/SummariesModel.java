package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SummariesModel {
    protected int score;
    protected String status;

    // for testing usage
    public SummariesModel(int score, String status) {
        this.score = score;
        this.status = status;
    }
}
