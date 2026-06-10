package org.openelisglobal.atomfeed.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.atomfeed.dto.OpenElisAccessionDto;
import org.openelisglobal.atomfeed.dto.OpenElisTestDetailDto;
import org.openelisglobal.atomfeed.mapping.OpenMrsConceptTestMapping;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenElisAccessionJsonBuilder {

    private static final String RESULTS_FINAL_STATUS = "Results final";

    @Autowired
    private SampleService sampleService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private OpenMrsConceptTestMapping conceptTestMapping;

    @Autowired
    private IStatusService statusService;

    /**
     * Optional OpenMRS Location uuid stamped on result encounters/visits created by
     * the Bahmni consumer (OpenElisAccession.labLocationUuid).
     */
    @Value("${atomfeed.result.labLocationUuid:}")
    private String labLocationUuid;

    @Transactional(readOnly = true)
    public OpenElisAccessionDto build(String accessionKey) {
        Sample sample = resolveSample(accessionKey);
        if (sample == null) {
            return null;
        }

        OpenElisAccessionDto dto = new OpenElisAccessionDto();
        dto.setAccessionUuid(sample.getFhirUuid() != null ? sample.getFhirUuidAsString() : sample.getAccessionNumber());
        // must be Joda DateTime.parse()-able on the Bahmni side
        dto.setDateTime(toIsoDateTime(sample.getEnteredDate()));
        if (!GenericValidator.isBlankOrNull(labLocationUuid)) {
            dto.setLabLocationUuid(labLocationUuid);
        }

        Patient patient = sampleService.getPatient(sample);
        if (patient != null) {
            dto.setPatientUuid(patientService.getGUID(patient));
            dto.setPatientIdentifier(patient.getExternalId());
            Person person = patientService.getPerson(patient);
            if (person != null) {
                dto.setPatientFirstName(person.getFirstName());
                dto.setPatientLastName(person.getLastName());
            }
        }

        String finishedStatusId = statusService.getStatusID(AnalysisStatus.Finalized);
        List<Analysis> analyses = analysisService.getAnalysesBySampleId(sample.getId());
        List<OpenElisTestDetailDto> testDetails = new ArrayList<>();
        for (Analysis analysis : analyses) {
            if (!finishedStatusId.equals(analysis.getStatusId())) {
                continue;
            }
            Test test = analysis.getTest();
            if (test == null) {
                continue;
            }
            List<Result> results = resultService.getResultsByAnalysis(analysis);
            if (results == null || results.isEmpty()) {
                continue;
            }
            Result result = results.get(results.size() - 1);
            OpenElisTestDetailDto detail = new OpenElisTestDetailDto();
            detail.setTestName(test.getLocalizedName());
            conceptTestMapping.resolveConceptUuidByTestId(test.getId()).ifPresent(detail::setTestUuid);
            if (detail.getTestUuid() == null) {
                // the Bahmni worker NPEs on test details without a concept uuid
                continue;
            }
            detail.setResult(result.getValue());
            detail.setResultType(result.getResultType());
            detail.setStatus(RESULTS_FINAL_STATUS);
            detail.setAbnormal(Boolean.FALSE);
            // the consumer skips test details with a blank dateTime
            Date resultDate = result.getLastupdated() != null ? result.getLastupdated()
                    : (analysis.getReleasedDate() != null ? analysis.getReleasedDate() : sample.getEnteredDate());
            detail.setDateTime(toIsoDateTime(resultDate));
            testDetails.add(detail);
        }
        dto.setTestDetails(testDetails);
        return dto;
    }

    private Sample resolveSample(String accessionKey) {
        if (GenericValidator.isBlankOrNull(accessionKey)) {
            return null;
        }
        return sampleService.getSampleByAccessionNumber(accessionKey);
    }

    private String toIsoDateTime(Date date) {
        Date effective = date != null ? date : new Date();
        return Instant.ofEpochMilli(effective.getTime()).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
