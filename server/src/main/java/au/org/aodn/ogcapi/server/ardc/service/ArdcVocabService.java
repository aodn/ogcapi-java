package au.org.aodn.ogcapi.server.ardc.service;

import au.org.aodn.ogcapi.server.ardc.model.VocabModel;

import java.util.List;

public interface ArdcVocabService {
    List<VocabModel> getParameterVocabs(String vocabApiBase);
    List<VocabModel> getPlatformVocabs(String vocabApiBase);
}
