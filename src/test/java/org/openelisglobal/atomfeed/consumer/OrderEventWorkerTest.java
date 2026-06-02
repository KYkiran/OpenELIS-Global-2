package org.openelisglobal.atomfeed.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict4h.atomfeed.client.domain.Event;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient.OpenMrsHttpException;
import org.openelisglobal.atomfeed.service.OE2OrderService;

@RunWith(MockitoJUnitRunner.class)
public class OrderEventWorkerTest {

    @Mock
    private OpenMrsHttpClient openMrsHttpClient;

    @Mock
    private OE2OrderService oe2OrderService;

    @InjectMocks
    private OrderEventWorker orderEventWorker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        String encounterJson = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("encounterUuid", "enc-1")
                .put("patientUuid", "pat-1")
                .set("orders", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("uuid", "order-1")
                                .put("voided", false)
                                .put("type", "Test Order")
                                .set("concept", objectMapper.createObjectNode().put("uuid", "c-1").put("display", "Hb")))
                        .add(objectMapper.createObjectNode()
                                .put("uuid", "order-2")
                                .put("voided", true)
                                .put("type", "Test Order")
                                .set("concept", objectMapper.createObjectNode().put("uuid", "c-2").put("display", "Void")))));

        when(openMrsHttpClient.fetchJson("/encounter/1")).thenReturn(encounterJson);
        when(openMrsHttpClient.fetchJson("/openmrs/ws/rest/v1/patient/pat-1?v=full"))
                .thenReturn("{\"person\":{\"uuid\":\"person-1\",\"gender\":\"M\"}}");
    }

    @Test
    public void process_createsTestOrdersAndCancelsVoided() throws Exception {
        Event event = new Event("evt-1", "/encounter/1", "Encounter");

        orderEventWorker.process(event);

        verify(oe2OrderService).createOrder(eq("enc-1"), any(), any(), any());
        verify(oe2OrderService).cancelOrder("order-2");
    }

    @Test
    public void process_createsBahmniLabOrders() throws Exception {
        String encounterJson = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("encounterUuid", "enc-bahmni")
                .put("patientUuid", "pat-bahmni")
                .set("orders", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("uuid", "order-lab-1")
                        .put("voided", false)
                        .put("orderType", "Lab Order")
                        .put("action", "NEW")
                        .put("conceptUuid", "33cb5232-172e-4769-ad2d-49fadaafc318")
                        .set("concept", objectMapper.createObjectNode()
                                .put("uuid", "33cb5232-172e-4769-ad2d-49fadaafc318")
                                .put("name", "CD4 Test")
                                .put("conceptClass", "LabTest")))));
        when(openMrsHttpClient.fetchJson("/encounter/bahmni")).thenReturn(encounterJson);
        when(openMrsHttpClient.fetchJson("/openmrs/ws/rest/v1/patient/pat-bahmni?v=full"))
                .thenReturn("{\"person\":{\"uuid\":\"person-bahmni\",\"gender\":\"F\"}}");

        Event event = new Event("evt-bahmni", "/encounter/bahmni", "Encounter");
        orderEventWorker.process(event);

        verify(oe2OrderService).createOrder(eq("enc-bahmni"), any(), any(), any());
    }

    @Test
    public void process_skipsNonTestOrders() throws Exception {
        String encounterJson = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("encounterUuid", "enc-2")
                .set("orders", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("uuid", "order-3")
                        .put("voided", false)
                        .put("type", "Drug Order")
                        .set("concept", objectMapper.createObjectNode().put("uuid", "c-3").put("display", "Drug")))));
        when(openMrsHttpClient.fetchJson("/encounter/2")).thenReturn(encounterJson);

        Event event = new Event("evt-2", "/encounter/2", "Encounter");
        orderEventWorker.process(event);

        verify(oe2OrderService, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    public void process_skipsBahmniRadiologyOrders() throws Exception {
        String encounterJson = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("encounterUuid", "enc-rad")
                .set("orders", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("uuid", "order-rad-1")
                        .put("voided", false)
                        .put("orderType", "Radiology Order")
                        .put("action", "NEW")
                        .set("concept", objectMapper.createObjectNode()
                                .put("uuid", "c-rad")
                                .put("name", "CHEST Lateral")
                                .put("conceptClass", "Radiology")))));
        when(openMrsHttpClient.fetchJson("/encounter/rad")).thenReturn(encounterJson);

        Event event = new Event("evt-rad", "/encounter/rad", "Encounter");
        orderEventWorker.process(event);

        verify(oe2OrderService, never()).createOrder(any(), any(), any(), any());
    }

    @Test(expected = OpenMrsHttpException.class)
    public void process_propagatesHttpErrorsForRetry() throws Exception {
        when(openMrsHttpClient.fetchJson("/encounter/3"))
                .thenThrow(new OpenMrsHttpException(503, "http://openmrs/encounter/3"));

        Event event = new Event("evt-3", "/encounter/3", "Encounter");
        orderEventWorker.process(event);
    }
}
