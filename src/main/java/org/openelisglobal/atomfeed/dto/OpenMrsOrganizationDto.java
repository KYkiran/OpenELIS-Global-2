package org.openelisglobal.atomfeed.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMrsOrganizationDto {

    @JsonProperty("organizationId")
    @JsonAlias({ "openMRSLocID", "id" })
    private Integer organizationId;

    @JsonProperty("idUuid")
    @JsonAlias({ "id_uuid", "openmrs_location_id_uuid" })
    private String idUuid;

    @JsonProperty("openmrs_location_id_uuid")
    private String openmrsLocationIdUuid;

    @JsonProperty("organizationName")
    @JsonAlias("name")
    private String organizationName;

    @JsonProperty("city")
    private String city;

    @JsonProperty("is_active")
    private Integer isActive;

    @JsonProperty("address_line1")
    private String addressLine1;

    @JsonProperty("address_line2")
    private String addressLine2;

    @JsonProperty("state")
    private String state;

    @JsonProperty("pincode")
    private String pincode;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("email")
    private String email;

    @JsonProperty("openelis_organization_id")
    private String openelisOrganizationId;

    public Integer getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Integer organizationId) {
        this.organizationId = organizationId;
    }

    public String getIdUuid() {
        return idUuid != null ? idUuid : openmrsLocationIdUuid;
    }

    public void setIdUuid(String idUuid) {
        this.idUuid = idUuid;
    }

    public String getOpenmrsLocationIdUuid() {
        return openmrsLocationIdUuid;
    }

    public void setOpenmrsLocationIdUuid(String openmrsLocationIdUuid) {
        this.openmrsLocationIdUuid = openmrsLocationIdUuid;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOpenelisOrganizationId() {
        return openelisOrganizationId;
    }

    public void setOpenelisOrganizationId(String openelisOrganizationId) {
        this.openelisOrganizationId = openelisOrganizationId;
    }
}
