package au.org.aodn.ogcapi.server.core.mapper;

import co.elastic.clients.transport.endpoints.BinaryDataResponse;
import org.mapstruct.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
@Mapper(componentModel = "spring")
public abstract class BinaryResponseToBytes implements Converter<BinaryDataResponse, byte[]> {

    protected Logger logger = LoggerFactory.getLogger(BinaryResponseToBytes.class);

    @Override
    public byte[] convert(BinaryDataResponse from) {
        logger.debug("Type is {}", from.contentType());

        try (InputStream s = from.content()) {
            return from.content().readAllBytes();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
