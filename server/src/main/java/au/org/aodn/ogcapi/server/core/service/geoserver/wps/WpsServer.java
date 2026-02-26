package au.org.aodn.ogcapi.server.core.service.geoserver.wps;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.geoserver.Server;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import lombok.extern.slf4j.Slf4j;
import net.opengis.ows11.CodeType;
import net.opengis.ows11.Ows11Factory;
import net.opengis.wps10.*;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.wps.WPS;
import org.geotools.wps.WPSConfiguration;
import org.geotools.xsd.Encoder;
import org.opengis.filter.Filter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
public class WpsServer implements Server {

    protected final RestTemplate restTemplate;
    protected final WmsServer wmsServer;
    protected final WfsServer wfsServer;
    protected final HttpEntity<?> pretendUserEntity;

    public static class WpsProcessRequest extends FeatureRequest {}

    public WpsServer(WmsServer wmsServer, WfsServer wfsServer, RestTemplate restTemplate, HttpEntity<?> entity) {
        this.wfsServer = wfsServer;
        this.wmsServer = wmsServer;
        this.restTemplate = restTemplate;
        this.pretendUserEntity = entity;
    }

    public String getEstimateDownloadSize(String uuid, WpsProcessRequest request) throws Exception {
        // Get WFS server URL and field model for the given UUID and layer name
        Optional<String> featureServerUrl = wfsServer.getFeatureServerUrl(uuid, request.getLayerName());

        // Get the wfs fields to build the CQL filter
        if (featureServerUrl.isPresent()) {
            WfsServer.WfsFeatureRequest featureRequest = WfsServer.WfsFeatureRequest.builder()
                    .server(featureServerUrl.get())
                    .layerName(request.getLayerName())
                    .datetime(request.getDatetime())
                    .properties(request.getProperties())
                    .multiPolygon(request.getMultiPolygon())
                    .build();

            // Build CQL filter as long string
            String cqlFilter = wfsServer.buildCqlFilter(uuid, featureRequest);

            // Convert to filter
            Filter filter = CQL.toFilter(cqlFilter);

            // Finally create the request
            String xmlRequest = createEstimateDownloadSizeXmlRequest(featureRequest.getLayerName(), filter);
            HttpEntity<String> entity = new HttpEntity<>(xmlRequest, pretendUserEntity.getHeaders());

            ResponseEntity<String> response = restTemplate.exchange(
                    featureRequest.getServer().replace("wfs", "wps"),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if(response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            else {
                return "";
            }
        }
        else {
            throw new UnsupportedOperationException("Missing wfs url in metadata");
        }
    }

    @SuppressWarnings("unchecked")
    protected String createEstimateDownloadSizeXmlRequest(String layerName, Filter filter) throws Exception {
        Wps10Factory wpsFactory = Wps10Factory.eINSTANCE;
        Ows11Factory owsFactory = Ows11Factory.eINSTANCE;

        // 1. Setup Execute Request for gs:DownloadEstimator
        ExecuteType execute = wpsFactory.createExecuteType();
        CodeType processId = owsFactory.createCodeType();
        processId.setValue("gs:DownloadEstimator");
        execute.setIdentifier(processId);

        DataInputsType1 inputs = wpsFactory.createDataInputsType1();

        // 2. Add 'layername' Input
        inputs.getInput().add(createLiteralInput("layername", layerName));

        // 3. Add 'filter' Input node and will deal with content later
        if (filter != null) {
            inputs.getInput().add(createComplexInput("filter", "text/xml"));
        }

        execute.setDataInputs(inputs);


        ResponseFormType responseForm = wpsFactory.createResponseFormType();

        CodeType outputId = owsFactory.createCodeType();
        outputId.setValue("result");

        OutputDefinitionType rawOutput = wpsFactory.createOutputDefinitionType();
        rawOutput.setIdentifier(outputId);
        rawOutput.setMimeType("application/json");
        responseForm.setRawDataOutput(rawOutput);

        execute.setResponseForm(responseForm);

        Encoder encoder = new Encoder(new WPSConfiguration());
        encoder.setIndenting(true);

        Document doc = encoder.encodeAsDOM(execute, WPS.Execute);

        if (filter != null) {
            // The reason we need to do this is that the Encoder is so restrict that it do not
            // allow <!CDATA block, so we are force to transform it back to a Document, then
            // locate the filter section and add the <!CDATA back.
            Encoder filterEncoder = new Encoder(new org.geotools.filter.v1_1.OGCConfiguration());
            String filterXml = filterEncoder.encodeAsString(filter, org.geotools.filter.v1_1.OGC.Filter);

            // 2. Find the specific <wps:Input> that has the <ows:Identifier> "filter"
            NodeList nodes = doc.getElementsByTagNameNS("*", "Input");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element inputElem = (Element) nodes.item(i);

                // Check the identifier value
                NodeList idList = inputElem.getElementsByTagNameNS("*", "Identifier");
                if (idList.getLength() > 0 && "filter".equals(idList.item(0).getTextContent())) {

                    // 3. Find the <wps:ComplexData> inside THIS specific input
                    NodeList complexList = inputElem.getElementsByTagNameNS("*", "ComplexData");
                    if (complexList.getLength() > 0) {
                        Element complexData = (Element) complexList.item(0);

                        // 4. Inject the CDATA
                        CDATASection cdata = doc.createCDATASection(filterXml);
                        complexData.appendChild(cdata);
                    }
                }
            }
        }

        // 3. Transform the Document back to a String
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    protected InputType createLiteralInput(String id, String value) {
        Wps10Factory wpsFactory = Wps10Factory.eINSTANCE;
        Ows11Factory owsFactory = Ows11Factory.eINSTANCE;

        InputType input = wpsFactory.createInputType();
        CodeType identifier = owsFactory.createCodeType();
        identifier.setValue(id);
        input.setIdentifier(identifier);

        DataType data = wpsFactory.createDataType();
        LiteralDataType literalData = wpsFactory.createLiteralDataType();
        literalData.setValue(value);
        data.setLiteralData(literalData);
        input.setData(data);
        return input;
    }

    protected InputType createComplexInput(String id, String mimeType) {
        Wps10Factory wpsFactory = Wps10Factory.eINSTANCE;
        Ows11Factory owsFactory = Ows11Factory.eINSTANCE;

        InputType input = wpsFactory.createInputType();
        input.setIdentifier(owsFactory.createCodeType());
        input.getIdentifier().setValue(id);

        ComplexDataType complex = wpsFactory.createComplexDataType();
        complex.setMimeType(mimeType);

        DataType data = wpsFactory.createDataType();
        data.setComplexData(complex);
        input.setData(data);
        return input;
    }
}
