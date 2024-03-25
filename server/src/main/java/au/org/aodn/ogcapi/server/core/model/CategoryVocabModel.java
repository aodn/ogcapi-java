package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * This is the model class for http://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-parameter-category-vocabulary/
 */
@Builder
@Getter
public class CategoryVocabModel {

    protected String label;
    protected String definition;
    protected Map<String, String> broader;
    protected Map<String, String> narrower;
}
