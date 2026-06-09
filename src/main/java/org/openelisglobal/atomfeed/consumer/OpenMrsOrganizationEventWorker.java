package org.openelisglobal.atomfeed.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient.OpenMrsHttpException;
import org.openelisglobal.atomfeed.dto.OpenMrsOrganizationDto;
import org.openelisglobal.atomfeed.service.OpenMrsOrganizationSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenMrsOrganizationEventWorker implements EventWorker {

    private static final Logger log = LoggerFactory.getLogger(OpenMrsOrganizationEventWorker.class);

    @Autowired
    private OpenMrsHttpClient openMrsHttpClient;

    @Autowired
    private OpenMrsOrganizationSyncService organizationSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Event event) {
        String content = event.getContent();
        if (content == null || content.isBlank()) {
            log.warn("Organization atomfeed event {} has no content, skipping", event.getId());
            return;
        }

        log.info("Processing organization event: {}", content);

        try {
            String orgJson = openMrsHttpClient.fetchJson(content);
            OpenMrsOrganizationDto dto = objectMapper.readValue(orgJson, OpenMrsOrganizationDto.class);
            organizationSyncService.upsert(dto);
        } catch (OpenMrsHttpException e) {
            log.error("OpenMRS HTTP error processing organization event {}: {}", content, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to process organization event: {} | error: {}", content, e.getMessage(), e);
            throw new RuntimeException("Failed to process organization event: " + content, e);
        }
    }

    @Override
    public void cleanUp(Event event) {
        // nothing to clean up
    }
}
