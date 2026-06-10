package org.openelisglobal.atomfeed.service;

import java.util.UUID;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.atomfeed.repository.EventRecordJdbc;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultFeedPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResultFeedPublisher.class);
    public static final String CATEGORY = "result";
    // The Bahmni elisatomfeedclient dispatches events by entry title and only
    // knows the key "accession" (OpenElisPatientFeedWorker); anything else is
    // silently ignored.
    public static final String TITLE = "accession";

    @Autowired
    private EventRecordJdbc eventRecordJdbc;

    @Autowired
    private SampleService sampleService;

    @Value("${atomfeed.result.publishUrlPattern:/OpenELIS-Global/rest/openmrs-atomfeed/accession/%s}")
    private String publishUrlPattern;

    @Value("${atomfeed.result.enabled:true}")
    private boolean resultFeedEnabled;

    @Transactional
    public void publishForAccession(String accessionNumber) {
        if (!resultFeedEnabled || GenericValidator.isBlankOrNull(accessionNumber)) {
            return;
        }

        Sample sample = sampleService.getSampleByAccessionNumber(accessionNumber);
        if (sample == null) {
            log.warn("Skipping result feed publish; sample not found for accession {}", accessionNumber);
            return;
        }

        if (sample.getFhirUuid() == null) {
            sample.setFhirUuid(UUID.randomUUID());
            sample.setSysUserId("1");
            sampleService.update(sample);
        }

        String objectUrl = String.format(publishUrlPattern, sample.getAccessionNumber());
        eventRecordJdbc.insert(TITLE, CATEGORY, objectUrl, CATEGORY);
        log.info("Published result atomfeed event for accession {} object={}", accessionNumber, objectUrl);
    }
}
