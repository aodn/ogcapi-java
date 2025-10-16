package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.server.core.util.LinkUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkModel {
    protected String rel;
    protected String href;
    protected String type;
    protected String title;

    @JsonProperty("ai:group")
    protected String aiGroup;

    @JsonProperty("description")
    protected String description;

    public void setTitle(String title) {
        String[] parsed = LinkUtils.parseLinkTitleDescription(title);
        this.title = parsed[0];
        if (this.description == null) {
            this.description = parsed[1];
        }
    }
}
