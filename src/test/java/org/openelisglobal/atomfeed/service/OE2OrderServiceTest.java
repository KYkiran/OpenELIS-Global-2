package org.openelisglobal.atomfeed.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.atomfeed.mapping.OpenMrsConceptTestMapping;
import org.openelisglobal.atomfeed.mapping.OpenMrsConceptTestMapping.ConceptTestMappingInfo;
import org.openelisglobal.atomfeed.repository.OpenMrsOrderMappingJdbc;
import org.openelisglobal.atomfeed.repository.OpenMrsOrderMappingStatus;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.ExternalOrderStatus;
import org.openelisglobal.dataexchange.order.action.IOrderExistanceChecker;
import org.openelisglobal.dataexchange.order.action.IOrderExistanceChecker.CheckResult;
import org.openelisglobal.dataexchange.order.action.IOrderPersister;
import org.openelisglobal.dataexchange.order.action.MessagePatient;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.dataexchange.service.order.ElectronicOrderService;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.springframework.beans.factory.ObjectFactory;

@RunWith(MockitoJUnitRunner.class)
public class OE2OrderServiceTest {

    @Mock
    private ObjectFactory<IOrderPersister> orderPersisterFactory;

    @Mock
    private IStatusService statusService;

    @Mock
    private IOrderExistanceChecker existanceChecker;

    @Mock
    private OpenMrsConceptTestMapping conceptTestMapping;

    @Mock
    private OpenMrsOrderMappingJdbc orderMappingJdbc;

    @Mock
    private PatientService patientService;

    @Mock
    private ElectronicOrderService electronicOrderService;

    @Mock
    private IOrderPersister orderPersister;

    @InjectMocks
    private OE2OrderService oe2OrderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        when(orderPersisterFactory.getObject()).thenReturn(orderPersister);
        when(orderPersister.getServiceUserId()).thenReturn("1");
        when(statusService.getStatusID(ExternalOrderStatus.Entered)).thenReturn("entered-id");
        when(statusService.getStatusID(ExternalOrderStatus.NonConforming)).thenReturn("nonconforming-id");
        when(statusService.getStatusID(ExternalOrderStatus.Cancelled)).thenReturn("cancelled-id");
    }

    @Test
    public void createOrder_skipsDuplicate() throws Exception {
        when(existanceChecker.check("order-1")).thenReturn(CheckResult.ORDER_FOUND_QUEUED);

        JsonNode order = objectMapper.readTree("{\"uuid\":\"order-1\",\"concept\":{\"uuid\":\"c-1\",\"display\":\"Test\"}}");
        JsonNode encounter = objectMapper.readTree("{\"encounterUuid\":\"enc-1\",\"patientUuid\":\"pat-1\"}");

        oe2OrderService.createOrder("enc-1", order, encounter, null);

        verify(orderPersister, never()).persist(any(MessagePatient.class), any(ElectronicOrder.class));
    }

    @Test
    public void createOrder_persistsMappedOrder() throws Exception {
        when(existanceChecker.check("order-1")).thenReturn(CheckResult.NOT_FOUND);
        when(conceptTestMapping.resolve("c-1")).thenReturn(java.util.Optional.of(
                new ConceptTestMappingInfo("c-1", "101", "Hemoglobin", "5")));

        Patient patient = new Patient();
        patient.setId("patient-1");
        when(patientService.getPatientForGuid("person-1")).thenReturn(patient);

        JsonNode order = objectMapper.readTree(
                "{\"uuid\":\"order-1\",\"urgency\":\"ROUTINE\",\"concept\":{\"uuid\":\"c-1\",\"display\":\"Hemoglobin\"}}");
        JsonNode encounter = objectMapper.readTree("{\"encounterUuid\":\"enc-1\",\"patientUuid\":\"pat-1\"}");
        JsonNode patientData = objectMapper.readTree(
                "{\"person\":{\"uuid\":\"person-1\",\"gender\":\"M\",\"birthdate\":\"1990-01-01\",\"preferredName\":{\"givenName\":\"John\",\"familyName\":\"Doe\"}}}");

        oe2OrderService.createOrder("enc-1", order, encounter, patientData);

        ArgumentCaptor<ElectronicOrder> orderCaptor = ArgumentCaptor.forClass(ElectronicOrder.class);
        verify(orderPersister).persist(any(MessagePatient.class), orderCaptor.capture());
        assertEquals("entered-id", orderCaptor.getValue().getStatusId());
        verify(orderMappingJdbc).insert(eq("enc-1"), eq("order-1"), eq("patient-1"),
                eq(OpenMrsOrderMappingStatus.QUEUED), eq(null));
    }

    @Test
    public void createOrder_persistsUnmappedConceptAsNonConforming() throws Exception {
        when(existanceChecker.check("order-2")).thenReturn(CheckResult.NOT_FOUND);
        when(conceptTestMapping.resolve("c-2")).thenReturn(java.util.Optional.empty());
        when(patientService.getPatientForGuid("person-2")).thenReturn(null);

        JsonNode order = objectMapper.readTree(
                "{\"uuid\":\"order-2\",\"concept\":{\"uuid\":\"c-2\",\"display\":\"Unknown Test\"}}");
        JsonNode encounter = objectMapper.readTree("{\"encounterUuid\":\"enc-2\",\"patientUuid\":\"pat-2\"}");
        JsonNode patientData = objectMapper.readTree("{\"person\":{\"uuid\":\"person-2\",\"gender\":\"F\"}}");

        oe2OrderService.createOrder("enc-2", order, encounter, patientData);

        ArgumentCaptor<ElectronicOrder> orderCaptor = ArgumentCaptor.forClass(ElectronicOrder.class);
        verify(orderPersister).persist(any(MessagePatient.class), orderCaptor.capture());
        assertEquals("nonconforming-id", orderCaptor.getValue().getStatusId());
        verify(orderMappingJdbc).insert(eq("enc-2"), eq("order-2"), eq(null),
                eq(OpenMrsOrderMappingStatus.MAPPING_FAILED), eq("No OE2 test mapping for OpenMRS concept c-2"));
    }

    @Test
    public void cancelOrder_updatesEnteredElectronicOrder() {
        ElectronicOrder eOrder = new ElectronicOrder();
        eOrder.setStatusId("entered-id");
        when(electronicOrderService.getElectronicOrdersByExternalId("order-3"))
                .thenReturn(Collections.singletonList(eOrder));

        oe2OrderService.cancelOrder("order-3");

        assertEquals("cancelled-id", eOrder.getStatusId());
        verify(electronicOrderService).update(eOrder);
        verify(orderMappingJdbc).updateStatus("order-3", OpenMrsOrderMappingStatus.CANCELLED, "Voided in OpenMRS");
    }
}
