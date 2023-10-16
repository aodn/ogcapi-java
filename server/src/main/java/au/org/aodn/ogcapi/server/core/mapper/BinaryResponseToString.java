package au.org.aodn.ogcapi.server.core.mapper;

import co.elastic.clients.transport.endpoints.BinaryDataResponse;
import org.geotools.util.Base64;
import org.mapstruct.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
@Mapper(componentModel = "spring")
public abstract class BinaryResponseToString implements Converter<BinaryDataResponse, byte[]> {

    protected Logger logger = LoggerFactory.getLogger(BinaryResponseToString.class);

    @Override
    public byte[] convert(BinaryDataResponse from) {
        logger.debug("Type is {}", from.contentType());

        try (InputStream s = from.content()) {
            byte[] b = from.content().readAllBytes();
            return b;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
