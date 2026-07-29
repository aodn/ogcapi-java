package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.indexer.EsIndexer;
import au.org.aodn.ogcapi.server.core.service.indexer.IndexerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class IndexerConfig {

    @Bean
    public EsIndexer createEsIndexer(IndexerProperties properties, RestTemplate template) {
        return new EsIndexer(properties, template);
    }
}
