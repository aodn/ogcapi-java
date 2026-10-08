package au.org.aodn.ogcapi.server.core.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Writes the same JSON object as {@code ExtendedCollections}, one collection at a time.
 * Field order is {@code links}, {@code collections}, {@code total}, {@code search_after}.
 */
public final class CollectionsResponseWriter {

    private CollectionsResponseWriter() {
    }

    /**
     * Opens {@code {"links":[],"collections":[} and leaves the generator inside the array.
     */
    public static JsonGenerator open(OutputStream output, ObjectMapper mapper) throws IOException {
        JsonGenerator generator = mapper.getFactory().createGenerator(output);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        generator.setCodec(mapper);
        generator.writeStartObject();
        generator.writeFieldName("links");
        generator.writeStartArray();
        generator.writeEndArray();
        generator.writeFieldName("collections");
        generator.writeStartArray();
        return generator;
    }

    public static void finish(JsonGenerator generator, Long total, List<String> searchAfter) throws IOException {
        generator.writeEndArray();
        if (total != null) {
            generator.writeNumberField("total", total);
        }
        if (searchAfter != null) {
            generator.writeFieldName("search_after");
            generator.writeObject(searchAfter);
        }
        generator.writeEndObject();
    }
}
