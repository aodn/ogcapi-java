package au.org.aodn.ogcapi.server.core.mapper;

import co.elastic.clients.transport.endpoints.BinaryResponse;
import org.mapstruct.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
@Mapper(componentModel = "spring")
public abstract class BinaryResponseToBytes implements Converter<BinaryResponse, byte[]> {

    protected Logger logger = LoggerFactory.getLogger(BinaryResponseToBytes.class);

    @Override
    public byte[] convert(BinaryResponse from, Param noUse) {
        logger.debug("Incoming BinaryResponse type is {}", from.contentType());

        try (InputStream s = from.content()) {
            return s.readAllBytes();
        }
        catch (IOException e) {
            logger.warn("Fail to read datastream from BinaryResponse");
        }
        return null;
    }
}
