package au.org.aodn.ogcapi.server.ardc.service;

import au.org.aodn.ogcapi.server.ardc.model.ParameterVocabModel;
import au.org.aodn.ogcapi.server.ardc.model.PlatformVocabModel;

import java.util.List;

public interface ArdcVocabService {
    List<ParameterVocabModel> getParameterVocabs(String vocabApiBase);
    List<PlatformVocabModel> getPlatformVocabs(String vocabApiBase);
}
