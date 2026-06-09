package org.openelisglobal.atomfeed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.atomfeed.dto.AtomFeedOrderPayload;
import org.openelisglobal.atomfeed.mapping.OpenMrsConceptTestMapping;
import org.openelisglobal.atomfeed.mapping.OpenMrsConceptTestMapping.ConceptTestMappingInfo;
import org.openelisglobal.atomfeed.repository.OpenMrsOrderMappingJdbc;
import org.openelisglobal.atomfeed.repository.OpenMrsOrderMappingStatus;
import org.openelisglobal.atomfeed.util.BahmniEncounterParser;
import org.openelisglobal.atomfeed.util.OpenMrsLocationResolver;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.ExternalOrderStatus;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.dataexchange.order.action.IOrderExistanceChecker;
import org.openelisglobal.dataexchange.order.action.IOrderExistanceChecker.CheckResult;
import org.openelisglobal.dataexchange.order.action.IOrderPersister;
import org.openelisglobal.dataexchange.order.action.MessagePatient;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrderType;
import org.openelisglobal.dataexchange.service.order.ElectronicOrderService;
import org.openelisglobal.organization.service.OrganizationService;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OE2OrderService {

    private static final Logger log = LoggerFactory.getLogger(OE2OrderService.class);

    @Autowired
    private ObjectFactory<IOrderPersister> orderPersisterFactory;

    @Autowired
    private IStatusService statusService;

    @Autowired
    private IOrderExistanceChecker existanceChecker;

    @Autowired
    private OpenMrsConceptTestMapping conceptTestMapping;

    @Autowired
    private OpenMrsOrderMappingJdbc orderMappingJdbc;

    @Autowired
    private PatientService patientService;

    @Autowired
    private ElectronicOrderService electronicOrderService;

    @Autowired
    private OpenMrsLocationResolver locationResolver;

    @Autowired
    private OrganizationService organizationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void createOrder(String encounterUuid, JsonNode order, JsonNode encounter, JsonNode patientData) {
        String orderUuid = order.path("uuid").asText();
        if (GenericValidator.isBlankOrNull(orderUuid)) {
            log.warn("AtomFeed order has no uuid, skipping");
            return;
        }

        if (existanceChecker.check(orderUuid) != CheckResult.NOT_FOUND) {
            log.info("Order {} already exists, skipping", orderUuid);
            return;
        }

        String conceptUuid = BahmniEncounterParser.resolveConceptUuid(order);
        String conceptDisplay = BahmniEncounterParser.resolveConceptDisplay(order);
        ConceptTestMappingInfo mapping = conceptTestMapping.resolve(conceptUuid).orElse(null);

        log.info("=== PROCESSING ATOMFEED ORDER ===");
        log.info("  Encounter UUID : {}", encounterUuid);
        log.info("  Order UUID     : {}", orderUuid);
        log.info("  Concept        : {} ({})", conceptDisplay, conceptUuid);
        log.info("  Mapped test    : {}", mapping != null ? mapping.getOe2TestId() : "none");

        MessagePatient mp = buildMessagePatient(encounter, patientData);
        AtomFeedOrderPayload payload = buildPayload(encounterUuid, orderUuid, order, encounter, conceptUuid,
                conceptDisplay, mapping, mp);

        ElectronicOrder eOrder = new ElectronicOrder();
        eOrder.setExternalId(orderUuid);
        try {
            eOrder.setData(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AtomFeed order payload for " + orderUuid, e);
        }

        boolean mapped = mapping != null;
        eOrder.setStatusId(
                statusService.getStatusID(mapped ? ExternalOrderStatus.Entered : ExternalOrderStatus.NonConforming));
        eOrder.setOrderTimestamp(DateUtil.getNowAsTimestamp());

        IOrderPersister persister = orderPersisterFactory.getObject();
        eOrder.setSysUserId(persister.getServiceUserId());
        eOrder.setType(ElectronicOrderType.ATOMFEED);

        String urgency = order.path("urgency").asText("");
        if ("STAT".equalsIgnoreCase(urgency)) {
            eOrder.setPriority(OrderPriority.STAT);
        } else {
            eOrder.setPriority(OrderPriority.ROUTINE);
        }

        persister.persist(mp, eOrder);

        Patient patient = patientService.getPatientForGuid(mp.getGuid());
        String patientId = patient != null ? patient.getId() : null;
        String mappingStatus = mapped ? OpenMrsOrderMappingStatus.QUEUED : OpenMrsOrderMappingStatus.MAPPING_FAILED;
        String errorMessage = mapped ? null : "No OE2 test mapping for OpenMRS concept " + conceptUuid;
        orderMappingJdbc.insert(encounterUuid, orderUuid, patientId, payload.getOpenmrsOrganizationId(), mappingStatus,
                errorMessage);

        log.info("Successfully persisted AtomFeed electronic order {} (status={})", orderUuid, mappingStatus);
    }

    @Transactional
    public void cancelOrder(String orderUuid) {
        if (GenericValidator.isBlankOrNull(orderUuid)) {
            return;
        }

        List<ElectronicOrder> orders = electronicOrderService.getElectronicOrdersByExternalId(orderUuid);
        if (orders == null || orders.isEmpty()) {
            log.debug("No electronic order to cancel for {}", orderUuid);
            return;
        }

        ElectronicOrder eOrder = orders.get(orders.size() - 1);
        String enteredStatusId = statusService.getStatusID(ExternalOrderStatus.Entered);
        String nonConformingStatusId = statusService.getStatusID(ExternalOrderStatus.NonConforming);

        if (enteredStatusId.equals(eOrder.getStatusId()) || nonConformingStatusId.equals(eOrder.getStatusId())) {
            eOrder.setStatusId(statusService.getStatusID(ExternalOrderStatus.Cancelled));
            electronicOrderService.update(eOrder);
            orderMappingJdbc.updateStatus(orderUuid, OpenMrsOrderMappingStatus.CANCELLED, "Voided in OpenMRS");
            log.info("Cancelled AtomFeed electronic order {}", orderUuid);
        }
    }

    private MessagePatient buildMessagePatient(JsonNode encounter, JsonNode patientData) {
        MessagePatient mp = new MessagePatient();
        if (patientData != null && patientData.has("person")) {
            JsonNode person = patientData.get("person");
            mp.setGuid(person.path("uuid").asText());
            mp.setGender(person.path("gender").asText());

            String dob = person.path("birthdate").asText();
            if (dob != null && dob.length() >= 10) {
                mp.setDisplayDOB(dob.substring(0, 10));
            }

            JsonNode name = person.path("preferredName");
            if (!name.isMissingNode()) {
                mp.setFirstName(name.path("givenName").asText());
                mp.setLastName(name.path("familyName").asText());
            }
        } else {
            mp.setGuid(BahmniEncounterParser.resolvePatientUuid(encounter));
        }
        return mp;
    }

    private AtomFeedOrderPayload buildPayload(String encounterUuid, String orderUuid, JsonNode order,
            JsonNode encounter, String conceptUuid, String conceptDisplay, ConceptTestMappingInfo mapping,
            MessagePatient mp) {
        AtomFeedOrderPayload payload = new AtomFeedOrderPayload();
        payload.setEncounterUuid(encounterUuid);
        payload.setOrderUuid(orderUuid);
        payload.setConceptUuid(conceptUuid);
        payload.setConceptDisplay(conceptDisplay);
        payload.setUrgency(order.path("urgency").asText(""));
        payload.setPatientGuid(mp.getGuid());
        if (!GenericValidator.isBlankOrNull(mp.getFirstName()) || !GenericValidator.isBlankOrNull(mp.getLastName())) {
            payload.setPatientDisplayName((mp.getFirstName() + " " + mp.getLastName()).trim());
        }
        if (mapping != null) {
            payload.setOe2TestId(mapping.getOe2TestId());
            payload.setOe2TestName(mapping.getOe2TestName());
            payload.setSampleType(mapping.getSampleType());
        }

        Integer openMrsOrgId = locationResolver.resolveOrganizationId(encounter);
        if (openMrsOrgId != null) {
            payload.setOpenmrsOrganizationId(String.valueOf(openMrsOrgId));
            Organization referringSite = organizationService.getOrganizationByExternalId(String.valueOf(openMrsOrgId));
            if (referringSite != null) {
                payload.setReferringSiteId(referringSite.getId());
                payload.setReferringSiteName(referringSite.getOrganizationName());
            } else {
                log.warn("No OE2 organization synced for OpenMRS org {}", openMrsOrgId);
            }
        }
        payload.setLocationUuid(locationResolver.resolveLocationUuid(encounter));

        return payload;
    }
}
