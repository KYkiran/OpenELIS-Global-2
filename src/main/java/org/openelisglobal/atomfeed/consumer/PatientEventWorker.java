package org.openelisglobal.atomfeed.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient.OpenMrsHttpException;
import org.openelisglobal.atomfeed.service.PatientSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Processes individual patient feed events from the OpenMRS AtomFeed.
 *
 * <p>This is the OE2 equivalent of Bahmni's {@code PatientFeedEventWorker}. For
 * each event the worker:
 * <ol>
 *   <li>Fetches the full patient JSON from OpenMRS REST
 *       ({@code /openmrs/ws/rest/v1/patient/{uuid}?v=full}).
 *   <li>Delegates upsert to {@link PatientSyncService}.
 * </ol>
 *
 * <p>The event {@code content} field may contain either an absolute URL or a
 * relative path such as {@code /openmrs/ws/rest/v1/patient/{uuid}}.
 * {@link OpenMrsHttpClient#fetchJson} handles both cases.
 */
@Component
public class PatientEventWorker implements EventWorker {

    private static final Logger log = LoggerFactory.getLogger(PatientEventWorker.class);

    @Autowired
    private OpenMrsHttpClient openMrsHttpClient;

    @Autowired
    private PatientSyncService patientSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Event event) {
        String content = event.getContent();
        if (content == null || content.isBlank()) {
            log.warn("Patient feed event {} has no content, skipping", event.getId());
            return;
        }

        log.info("Processing patient feed event: {}", content);

        // Build the full-detail patient URL
        String patientUrl = buildPatientUrl(content);

        try {
            String patientJson = openMrsHttpClient.fetchJson(patientUrl);
            JsonNode patientNode = objectMapper.readTree(patientJson);
            patientSyncService.upsert(patientNode);

        } catch (OpenMrsHttpException e) {
            log.error("OpenMRS HTTP {} fetching patient from {}: {}", e.getStatusCode(), patientUrl, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to process patient event {}: {}", content, e.getMessage(), e);
            throw new RuntimeException("Failed to process patient event: " + content, e);
        }
    }

    @Override
    public void cleanUp(Event event) {
        // nothing to clean up
    }

    /**
     * Ensures the patient URL requests the full representation ({@code ?v=full}).
     * If the content already points to a full-detail endpoint, it is returned as-is.
     * Otherwise {@code ?v=full} is appended.
     */
    private String buildPatientUrl(String content) {
        // The event content from Bahmni/OpenMRS patient feed is usually the relative
        // path like /openmrs/ws/rest/v1/patient/<uuid> without a representation suffix.
        // We always want v=full to get names, identifiers, person attributes, etc.
        if (content.contains("?v=")) {
            return content;
        }
        // strip trailing slash if present
        String base = content.endsWith("/") ? content.substring(0, content.length() - 1) : content;
        return base + "?v=full";
    }
}
