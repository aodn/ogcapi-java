package au.org.aodn.ogcapi.server.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactModel {

    // Not include all fields according to this: https://github.com/stac-extensions/contacts
    // The types of some fields are also not aligned with the spec.
    // Currently only include the fields that are in use.
    // May need to add more fields / modify some fields in the future.
    protected String name;
    protected String organization;
    protected String identifier;
    protected String position;
    protected List<String> emails;
    protected List<AddressModel> addresses;
    protected List<LinkModel> links;
    protected List<String> roles;
    protected List<InfoModel> phones;

}
