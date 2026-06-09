package org.openelisglobal.atomfeed.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient.OpenMrsHttpException;
import org.openelisglobal.atomfeed.service.OE2OrderService;
import org.openelisglobal.atomfeed.util.BahmniEncounterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderEventWorker implements EventWorker {

    private static final Logger log = LoggerFactory.getLogger(OrderEventWorker.class);

    @Autowired
    private OpenMrsHttpClient openMrsHttpClient;

    @Autowired
    private OE2OrderService oe2OrderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Event event) {
        String content = event.getContent();
        if (content == null || content.isBlank()) {
            log.warn("AtomFeed event {} has no content, skipping", event.getId());
            return;
        }

        log.info("Processing encounter event: {}", content);

        try {
            String encounterJson = openMrsHttpClient.fetchJson(content);
            JsonNode encounter = objectMapper.readTree(encounterJson);

            String encounterUuid = BahmniEncounterParser.resolveEncounterUuid(encounter);

            if (encounterUuid.isBlank()) {
                log.warn("Could not determine encounter UUID from event {}, skipping", event.getId());
                return;
            }

            String patientUuid = BahmniEncounterParser.resolvePatientUuid(encounter);
            JsonNode patientData = null;
            if (!patientUuid.isBlank()) {
                try {
                    String patientJson = openMrsHttpClient
                            .fetchJson("/openmrs/ws/rest/v1/patient/" + patientUuid + "?v=full");
                    patientData = objectMapper.readTree(patientJson);
                } catch (Exception e) {
                    log.warn("Could not fetch patient {} for encounter {}", patientUuid, encounterUuid, e);
                }
            }

            JsonNode orders = encounter.path("orders");
            log.info("Encounter UUID: '{}', orders count: {}", encounterUuid, orders.size());
            if (!orders.isArray() || orders.isEmpty()) {
                log.debug("Encounter {} has no orders, skipping", encounterUuid);
                return;
            }

            for (JsonNode order : orders) {
                processOrder(encounterUuid, order, encounter, patientData);
            }

        } catch (OpenMrsHttpException e) {
            log.error("OpenMRS HTTP error processing event {}: {}", content, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to process event: {} | error: {}", content, e.getMessage(), e);
            throw new RuntimeException("Failed to process encounter event: " + content, e);
        }
    }

    private void processOrder(String encounterUuid, JsonNode order, JsonNode encounter, JsonNode patientData) {
        String orderType = BahmniEncounterParser.resolveOrderType(order);
        boolean voided = order.path("voided").asBoolean(false);
        String orderUuid = order.path("uuid").asText("");
        String conceptDisplay = BahmniEncounterParser.resolveConceptDisplay(order);
        String action = order.path("action").asText("");

        log.info("Order found - uuid: '{}', type: '{}', action: '{}', voided: {}, concept: '{}'", orderUuid, orderType,
                action, voided, conceptDisplay);

        if (voided || "DISCONTINUE".equalsIgnoreCase(action)) {
            try {
                oe2OrderService.cancelOrder(BahmniEncounterParser.resolveCancelTargetUuid(order));
            } catch (Exception e) {
                log.warn("Failed to cancel order {}: {}", orderUuid, e.getMessage());
            }
            return;
        }

        if (!BahmniEncounterParser.isLaboratoryOrder(order, orderType)) {
            log.info("Skipping order with type '{}' - not a laboratory order", orderType);
            return;
        }

        if (!BahmniEncounterParser.shouldCreateOrder(order)) {
            log.info("Skipping order {} with action '{}' - not a new order", orderUuid, action);
            return;
        }

        try {
            log.info("Found lab order in encounter {}, creating in OE2", encounterUuid);
            oe2OrderService.createOrder(encounterUuid, order, encounter, patientData);
        } catch (Exception e) {
            log.error("Failed to create OE2 order for OpenMRS order {}: {}", orderUuid, e.getMessage(), e);
            throw new RuntimeException("Failed to create order " + orderUuid, e);
        }
    }

    @Override
    public void cleanUp(Event event) {
        // nothing to clean up
    }
}
