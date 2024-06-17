package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SummariesModel {
    protected int score;
    protected String status;
    protected List<String> credits;

    // for testing usage
    public SummariesModel(int score, String status, List<String> credits) {
        this.score = score;
        this.status = status;
        this.credits = credits;
    }
}
