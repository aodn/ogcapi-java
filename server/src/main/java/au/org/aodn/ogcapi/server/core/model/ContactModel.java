package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
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

    // for testing usage
    public ContactModel(String name, String organization, String identifier, String position, List<String> emails, List<AddressModel> addresses, List<LinkModel> links, List<String> roles, List<InfoModel> phones) {
        this.name = name;
        this.organization = organization;
        this.identifier = identifier;
        this.position = position;
        this.emails = emails;
        this.addresses = addresses;
        this.links = links;
        this.roles = roles;
        this.phones = phones;
    }


}
