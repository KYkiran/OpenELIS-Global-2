package org.openelisglobal.atomfeed.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mirrors org.bahmni.module.elisatomfeedclient.api.domain.OpenElisTestDetail.
 * Only legacy field names may be serialized (strict Jackson mapper on the
 * consumer side). {@code dateTime} is mandatory for the consumer to create
 * result observations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenElisTestDetailDto {

    private String testName;
    private String testUuid;
    private String panelUuid;
    private String result;
    private String resultType;
    private String status;
    private Boolean abnormal;
    private String dateTime;

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getTestUuid() {
        return testUuid;
    }

    public void setTestUuid(String testUuid) {
        this.testUuid = testUuid;
    }

    public String getPanelUuid() {
        return panelUuid;
    }

    public void setPanelUuid(String panelUuid) {
        this.panelUuid = panelUuid;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getAbnormal() {
        return abnormal;
    }

    public void setAbnormal(Boolean abnormal) {
        this.abnormal = abnormal;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }
}
