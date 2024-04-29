package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.CategoryVocabModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.springframework.test.util.AssertionErrors.assertEquals;
import static org.springframework.test.util.AssertionErrors.assertTrue;

import java.io.IOException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RestExtServiceTest {

    @Autowired
    protected RestTemplate mockRestTemplate;

    @Autowired
    protected RestExtService restExtService;
    protected ObjectMapper objectMapper = new ObjectMapper();
    @Test
    public void verifyGetParameterCategory() throws IOException {
        // Create expect result
        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab0.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=1"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab1.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=2"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab2.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=3"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab3.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-discovery-parameter-vocabulary/version-1-6/concept.json"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab_discovery0.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/390"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab_entity_390.json")));

        List<CategoryVocabModel> categoryVocabModelList = restExtService.getParameterCategory("");
        assertEquals("Total equals", 33, categoryVocabModelList.size());


        Optional<CategoryVocabModel> c = categoryVocabModelList
                .stream()
                .filter(p -> p.getBroader().isEmpty() && !p.getNarrower().isEmpty() && p.getLabel().equals("Chemical"))
                .findFirst();

        assertTrue("Find target Chemical", c.isPresent());
        assertEquals("Have narrower equals", 5, c.get().getNarrower().size());


        Optional<CategoryVocabModel> b = categoryVocabModelList
                .stream()
                .filter(p -> p.getBroader().isEmpty() && !p.getNarrower().isEmpty() && p.getLabel().equals("Biological"))
                .findFirst();

        assertTrue("Find target Biological", b.isPresent());
        assertEquals("Have narrower equals", 5, b.get().getNarrower().size());


        Optional<CategoryVocabModel> pa = categoryVocabModelList
                .stream()
                .filter(p -> p.getBroader().isEmpty() && !p.getNarrower().isEmpty() && p.getLabel().equals("Physical-Atmosphere"))
                .findFirst();

        assertTrue("Find target Physical-Atmosphere", pa.isPresent());
        assertEquals("Have narrower equals", 8, pa.get().getNarrower().size());

        Optional<CategoryVocabModel> airTemperature = pa.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Air temperature"))
                .findFirst();
        assertTrue("Find target Visibility", airTemperature.isPresent());

        Optional<CategoryVocabModel> visibility = pa.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Visibility"))
                .findFirst();

        assertTrue("Find target Visibility", visibility.isPresent());

        Optional<CategoryVocabModel> horizontalVisibilityInTheAtmosphere = visibility.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Horizontal visibility in the atmosphere"))
                .findFirst();

        assertTrue("Horizontal visibility in the atmosphere found", horizontalVisibilityInTheAtmosphere.isPresent());

        Optional<CategoryVocabModel> pw = categoryVocabModelList
                .stream()
                .filter(p -> p.getBroader().isEmpty() && !p.getNarrower().isEmpty() && p.getLabel().equals("Physical-Water"))
                .findFirst();

        assertTrue("Find target Physical-Water", pw.isPresent());
        assertEquals("Have narrower equals", 14, pw.get().getNarrower().size());

    }
}
