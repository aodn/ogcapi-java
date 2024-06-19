package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

//https://github.com/stac-extensions/contacts?tab=readme-ov-file#address-object
@Data
@Builder
public class AddressModel {

    @JsonProperty("delivery_point")
    protected List<String> deliveryPoint;
    protected String city;

    @JsonProperty("administrative_area")
    protected String administrativeArea;

    @JsonProperty("postal_code")
    protected String postalCode;
    protected String country;


    // for testing usage
    public AddressModel(List<String> deliveryPoint, String city, String administrativeArea, String postalCode, String country) {
        this.deliveryPoint = deliveryPoint;
        this.city = city;
        this.administrativeArea = administrativeArea;
        this.postalCode = postalCode;
        this.country = country;
    }
}
