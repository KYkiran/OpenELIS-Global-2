package org.openelisglobal.atomfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.atomfeed.dto.AtomFeedOrderPayload;
import org.openelisglobal.common.util.XMLUtil;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AtomFeedLabOrderXmlBuilder {

    private static final Logger log = LoggerFactory.getLogger(AtomFeedLabOrderXmlBuilder.class);

    @Autowired
    private TestService testService;

    @Autowired
    private TypeOfSampleService typeOfSampleService;

    @Autowired
    private PatientService patientService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean appendOrderXml(ElectronicOrder electronicOrder, StringBuilder xml) {
        if (electronicOrder == null || GenericValidator.isBlankOrNull(electronicOrder.getData())) {
            return false;
        }

        try {
            AtomFeedOrderPayload payload = objectMapper.readValue(electronicOrder.getData(),
                    AtomFeedOrderPayload.class);
            appendOrderXml(payload, electronicOrder, xml);
            return true;
        } catch (Exception e) {
            log.warn("Could not build AtomFeed lab order XML for {}: {}", electronicOrder.getExternalId(),
                    e.getMessage());
            return false;
        }
    }

    public void appendOrderXml(AtomFeedOrderPayload payload, ElectronicOrder electronicOrder, StringBuilder xml) {
        xml.append("<order>");

        String patientGuid = resolvePatientGuid(payload, electronicOrder);
        xml.append("<patient>");
        XMLUtil.appendKeyValue("guid", patientGuid, xml);
        xml.append("</patient>");

        if (!GenericValidator.isBlankOrNull(payload.getReferringSiteId())) {
            xml.append("<requestingOrg>");
            XMLUtil.appendKeyValue("id", payload.getReferringSiteId(), xml);
            if (!GenericValidator.isBlankOrNull(payload.getReferringSiteName())) {
                XMLUtil.appendKeyValue("name", payload.getReferringSiteName(), xml);
            }
            if (!GenericValidator.isBlankOrNull(payload.getOpenmrsOrganizationId())) {
                XMLUtil.appendKeyValue("openmrsOrganizationId", payload.getOpenmrsOrganizationId(), xml);
            }
            xml.append("</requestingOrg>");
        }

        xml.append("<sampleTypes>");
        if (!GenericValidator.isBlankOrNull(payload.getOe2TestId())) {
            Test test = testService.getTestById(payload.getOe2TestId());
            TypeOfSample sampleType = resolveSampleType(payload, test);
            if (test != null && sampleType != null) {
                xml.append("<sampleType>");
                XMLUtil.appendKeyValue("id", sampleType.getId(), xml);
                XMLUtil.appendKeyValue("name", sampleType.getDescription(), xml);
                xml.append("<panels></panels>");
                xml.append("<tests>");
                xml.append("<test>");
                XMLUtil.appendKeyValue("id", test.getId(), xml);
                XMLUtil.appendKeyValue("name", test.getDescription(), xml);
                xml.append("</test>");
                xml.append("</tests>");
                xml.append("<collection></collection>");
                xml.append("</sampleType>");
            }
        }
        xml.append("</sampleTypes>");
        xml.append("<crosspanels></crosspanels>");
        xml.append("<crosstests></crosstests>");
        appendAlerts(xml, patientGuid);
        xml.append("</order>");
    }

    private String resolvePatientGuid(AtomFeedOrderPayload payload, ElectronicOrder electronicOrder) {
        if (!GenericValidator.isBlankOrNull(payload.getPatientGuid())) {
            return payload.getPatientGuid();
        }
        if (electronicOrder.getPatient() != null) {
            return patientService.getGUID(electronicOrder.getPatient());
        }
        return "";
    }

    private TypeOfSample resolveSampleType(AtomFeedOrderPayload payload, Test test) {
        if (StringUtils.isNotBlank(payload.getSampleType())) {
            TypeOfSample sampleType = typeOfSampleService.get(payload.getSampleType());
            if (sampleType != null) {
                return sampleType;
            }
        }
        if (test == null) {
            return null;
        }
        return typeOfSampleService.getSampleTypeFromTest(test);
    }

    private void appendAlerts(StringBuilder xml, String patientGuid) {
        if (GenericValidator.isBlankOrNull(patientGuid)) {
            XMLUtil.appendKeyValue("user_alert", MessageUtil.getMessage("electronic.order.warning.missingPatient"),
                    xml);
            return;
        }

        org.openelisglobal.patient.valueholder.Patient patient = patientService.getPatientForGuid(patientGuid);
        if (patient == null) {
            XMLUtil.appendKeyValue("user_alert", MessageUtil.getMessage("electronic.order.warning.missingPatient"),
                    xml);
        } else if (GenericValidator.isBlankOrNull(patientService.getEnteredDOB(patient))
                || GenericValidator.isBlankOrNull(patientService.getGender(patient))) {
            XMLUtil.appendKeyValue("user_alert", MessageUtil.getMessage("electroinic.order.warning.missingPatientInfo"),
                    xml);
        }
    }
}
