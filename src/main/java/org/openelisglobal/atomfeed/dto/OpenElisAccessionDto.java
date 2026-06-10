package org.openelisglobal.atomfeed.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors org.bahmni.module.elisatomfeedclient.api.domain.OpenElisAccession.
 * The Bahmni client deserializes with a default Jackson mapper
 * (FAIL_ON_UNKNOWN_PROPERTIES enabled), so only field names that exist on the
 * legacy class may be serialized here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenElisAccessionDto {

    private String accessionUuid;
    private String labLocationUuid;
    private String patientUuid;
    private String patientIdentifier;
    private String patientFirstName;
    private String patientLastName;
    private String dateTime;
    private List<OpenElisTestDetailDto> testDetails = new ArrayList<>();

    public String getAccessionUuid() {
        return accessionUuid;
    }

    public void setAccessionUuid(String accessionUuid) {
        this.accessionUuid = accessionUuid;
    }

    public String getPatientUuid() {
        return patientUuid;
    }

    public void setPatientUuid(String patientUuid) {
        this.patientUuid = patientUuid;
    }

    public String getPatientIdentifier() {
        return patientIdentifier;
    }

    public void setPatientIdentifier(String patientIdentifier) {
        this.patientIdentifier = patientIdentifier;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public void setPatientFirstName(String patientFirstName) {
        this.patientFirstName = patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public String getLabLocationUuid() {
        return labLocationUuid;
    }

    public void setLabLocationUuid(String labLocationUuid) {
        this.labLocationUuid = labLocationUuid;
    }

    public List<OpenElisTestDetailDto> getTestDetails() {
        return testDetails;
    }

    public void setTestDetails(List<OpenElisTestDetailDto> testDetails) {
        this.testDetails = testDetails;
    }
}
