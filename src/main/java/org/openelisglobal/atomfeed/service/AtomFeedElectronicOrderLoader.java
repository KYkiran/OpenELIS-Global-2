package org.openelisglobal.atomfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.atomfeed.dto.AtomFeedOrderPayload;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AtomFeedElectronicOrderLoader {

    private static final Logger log = LoggerFactory.getLogger(AtomFeedElectronicOrderLoader.class);

    @Autowired
    private PatientService patientService;

    @Autowired
    private TestService testService;

    @Autowired
    private TypeOfSampleService typeOfSampleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void applyToForm(SamplePatientEntryForm form, ElectronicOrder eOrder) {
        if (eOrder == null || eOrder.getData() == null) {
            return;
        }

        try {
            AtomFeedOrderPayload payload = objectMapper.readValue(eOrder.getData(), AtomFeedOrderPayload.class);
            SampleOrderItem sampleOrder = form.getSampleOrderItems();
            if (sampleOrder != null) {
                if (eOrder.getPriority() != null) {
                    sampleOrder.setPriority(eOrder.getPriority());
                } else if ("STAT".equalsIgnoreCase(payload.getUrgency())) {
                    sampleOrder.setPriority(OrderPriority.STAT);
                } else {
                    sampleOrder.setPriority(OrderPriority.ROUTINE);
                }
            }

            populatePatient(form, payload);
            populateSampleXml(form, payload);
        } catch (Exception e) {
            log.warn("Could not preload AtomFeed electronic order {}: {}", eOrder.getExternalId(), e.getMessage());
        }
    }

    private void populatePatient(SamplePatientEntryForm form, AtomFeedOrderPayload payload) {
        if (GenericValidator.isBlankOrNull(payload.getPatientGuid())) {
            return;
        }

        Patient patient = patientService.getPatientForGuid(payload.getPatientGuid());
        if (patient == null) {
            return;
        }

        Person person = patientService.getPerson(patient);
        PatientManagementInfo patientInfo = form.getPatientProperties();
        if (patientInfo == null) {
            patientInfo = new PatientManagementInfo();
            form.setPatientProperties(patientInfo);
        }

        patientInfo.setPatientPK(patient.getId());
        patientInfo.setGuid(payload.getPatientGuid());
        patientInfo.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
        patientInfo.setGender(patient.getGender());
        patientInfo.setBirthDateForDisplay(patient.getBirthDateForDisplay());
        if (person != null) {
            patientInfo.setFirstName(person.getFirstName());
            patientInfo.setLastName(person.getLastName());
            patientInfo.setPrimaryPhone(person.getPrimaryPhone());
            patientInfo.setEmail(person.getEmail());
            patientInfo.setStreetAddress(person.getStreetAddress());
            patientInfo.setCity(person.getCity());
        }
    }

    private void populateSampleXml(SamplePatientEntryForm form, AtomFeedOrderPayload payload) {
        if (GenericValidator.isBlankOrNull(payload.getOe2TestId())) {
            return;
        }

        String sampleTypeId = resolveSampleTypeId(payload);
        if (StringUtils.isBlank(sampleTypeId)) {
            log.warn("Could not resolve sample type for AtomFeed order test {}", payload.getOe2TestId());
            return;
        }

        String sampleXml = "<samples><sample sampleID='" + sampleTypeId + "' date='' time='' collector='' tests='"
                + payload.getOe2TestId()
                + "' testSectionMap='' testSampleTypeMap='' panels='' rejected='false' rejectReasonId='' initialConditionIds='' /></samples>";
        form.setSampleXML(sampleXml);
    }

    private String resolveSampleTypeId(AtomFeedOrderPayload payload) {
        if (StringUtils.isNotBlank(payload.getSampleType())) {
            return payload.getSampleType();
        }

        Test test = testService.getTestById(payload.getOe2TestId());
        if (test == null) {
            return null;
        }

        TypeOfSample sampleType = typeOfSampleService.getSampleTypeFromTest(test);
        return sampleType != null ? sampleType.getId() : null;
    }
}
