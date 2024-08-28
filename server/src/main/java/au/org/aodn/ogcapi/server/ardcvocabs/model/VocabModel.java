package au.org.aodn.ogcapi.server.ardcvocabs.model;

import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VocabModel {
    protected String label;
    protected String definition;
    protected String about;
    protected List<VocabModel> broader;
    protected List<VocabModel> narrower;
}
