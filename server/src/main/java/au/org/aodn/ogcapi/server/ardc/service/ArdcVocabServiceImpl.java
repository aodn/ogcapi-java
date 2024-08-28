package au.org.aodn.ogcapi.server.ardc.service;

import au.org.aodn.ogcapi.server.ardc.model.VocabModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArdcVocabServiceImpl implements ArdcVocabService {
    private final ParameterVocabProcessor parameterVocabProcessor;
    private final PlatformVocabProcessor platformVocabProcessor;

    @Autowired
    ArdcVocabServiceImpl(ParameterVocabProcessor parameterVocabProcessor,
                         PlatformVocabProcessor platformVocabProcessor) {
        this.parameterVocabProcessor = parameterVocabProcessor;
        this.platformVocabProcessor = platformVocabProcessor;
    }

    public List<VocabModel> getParameterVocabs(String vocabApiBase) {
        return parameterVocabProcessor.getParameterVocabs(vocabApiBase);
    }

    public List<VocabModel> getPlatformVocabs(String vocabApiBase) {
        return platformVocabProcessor.getPlatformVocabs(vocabApiBase);
    }
}
