package au.org.aodn.ogcapi.server.ardcvocabs.service;

import au.org.aodn.ogcapi.server.ardcvocabs.model.VocabModel;

import java.util.List;

public interface ArdcVocabService {
    List<VocabModel> getParameterVocabs(String vocabApiBase);
    List<VocabModel> getPlatformVocabs(String vocabApiBase);
}
