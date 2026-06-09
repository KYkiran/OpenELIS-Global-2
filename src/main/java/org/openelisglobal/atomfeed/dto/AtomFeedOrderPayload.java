package org.openelisglobal.atomfeed.dto;

public class AtomFeedOrderPayload {

    private String encounterUuid;
    private String orderUuid;
    private String conceptUuid;
    private String conceptDisplay;
    private String oe2TestId;
    private String oe2TestName;
    private String sampleType;
    private String urgency;
    private String patientGuid;
    private String patientDisplayName;
    private String openmrsOrganizationId;
    private String locationUuid;
    private String referringSiteId;
    private String referringSiteName;

    public String getEncounterUuid() {
        return encounterUuid;
    }

    public void setEncounterUuid(String encounterUuid) {
        this.encounterUuid = encounterUuid;
    }

    public String getOrderUuid() {
        return orderUuid;
    }

    public void setOrderUuid(String orderUuid) {
        this.orderUuid = orderUuid;
    }

    public String getConceptUuid() {
        return conceptUuid;
    }

    public void setConceptUuid(String conceptUuid) {
        this.conceptUuid = conceptUuid;
    }

    public String getConceptDisplay() {
        return conceptDisplay;
    }

    public void setConceptDisplay(String conceptDisplay) {
        this.conceptDisplay = conceptDisplay;
    }

    public String getOe2TestId() {
        return oe2TestId;
    }

    public void setOe2TestId(String oe2TestId) {
        this.oe2TestId = oe2TestId;
    }

    public String getOe2TestName() {
        return oe2TestName;
    }

    public void setOe2TestName(String oe2TestName) {
        this.oe2TestName = oe2TestName;
    }

    public String getSampleType() {
        return sampleType;
    }

    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getPatientGuid() {
        return patientGuid;
    }

    public void setPatientGuid(String patientGuid) {
        this.patientGuid = patientGuid;
    }

    public String getPatientDisplayName() {
        return patientDisplayName;
    }

    public void setPatientDisplayName(String patientDisplayName) {
        this.patientDisplayName = patientDisplayName;
    }

    public String getOpenmrsOrganizationId() {
        return openmrsOrganizationId;
    }

    public void setOpenmrsOrganizationId(String openmrsOrganizationId) {
        this.openmrsOrganizationId = openmrsOrganizationId;
    }

    public String getLocationUuid() {
        return locationUuid;
    }

    public void setLocationUuid(String locationUuid) {
        this.locationUuid = locationUuid;
    }

    public String getReferringSiteId() {
        return referringSiteId;
    }

    public void setReferringSiteId(String referringSiteId) {
        this.referringSiteId = referringSiteId;
    }

    public String getReferringSiteName() {
        return referringSiteName;
    }

    public void setReferringSiteName(String referringSiteName) {
        this.referringSiteName = referringSiteName;
    }
}
