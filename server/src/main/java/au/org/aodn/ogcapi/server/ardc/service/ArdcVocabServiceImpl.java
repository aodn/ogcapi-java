package au.org.aodn.ogcapi.server.ardc.service;

import au.org.aodn.ogcapi.server.ardc.model.ParameterVocabModel;
import au.org.aodn.ogcapi.server.ardc.model.PlatformVocabModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArdcVocabServiceImpl implements ArdcVocabService {
    private final ParameterVocabService parameterVocabService;
    private final PlatformVocabService platformVocabService;

    @Autowired
    ArdcVocabServiceImpl(ParameterVocabService parameterVocabService,
                         PlatformVocabService platformVocabService) {
        this.parameterVocabService = parameterVocabService;
        this.platformVocabService = platformVocabService;
    }

    public List<ParameterVocabModel> getParameterVocabs(String vocabApiBase) {
        return parameterVocabService.getParameterVocabs(vocabApiBase);
    }

    public List<PlatformVocabModel> getPlatformVocabs(String vocabApiBase) {
        return platformVocabService.getPlatformVocabs(vocabApiBase);
    }
}
