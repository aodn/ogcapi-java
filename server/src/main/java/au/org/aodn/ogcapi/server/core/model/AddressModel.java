package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//https://github.com/stac-extensions/contacts?tab=readme-ov-file#address-object
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressModel {

    @JsonProperty("delivery_point")
    protected List<String> deliveryPoint;
    protected String city;

    @JsonProperty("administrative_area")
    protected String administrativeArea;

    @JsonProperty("postal_code")
    protected String postalCode;
    protected String country;
}
