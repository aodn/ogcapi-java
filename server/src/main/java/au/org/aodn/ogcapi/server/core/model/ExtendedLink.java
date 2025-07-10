package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.Link;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExtendedLink extends Link {

    @JsonProperty("ai:group")
    private String aiGroup;

    public ExtendedLink() {
        super();
    }

    public ExtendedLink aiGroup(String aiGroup) {
        this.aiGroup = aiGroup;
        return this;
    }

    @Override
    public ExtendedLink href(String href) {
        super.href(href);
        return this;
    }

    @Override
    public ExtendedLink rel(String rel) {
        super.rel(rel);
        return this;
    }

    @Override
    public ExtendedLink type(String type) {
        super.type(type);
        return this;
    }

    @Override
    public ExtendedLink title(String title) {
        super.title(title);
        return this;
    }

    @Override
    public ExtendedLink length(Integer length) {
        super.length(length);
        return this;
    }

    @Override
    public ExtendedLink hreflang(String hreflang) {
        super.hreflang(hreflang);
        return this;
    }
}
