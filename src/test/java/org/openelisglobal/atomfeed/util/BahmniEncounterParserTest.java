package org.openelisglobal.atomfeed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class BahmniEncounterParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void resolveOrderType_readsStringLabOrder() throws Exception {
        JsonNode order = objectMapper.readTree("{\"orderType\":\"Lab Order\"}");
        assertEquals("Lab Order", BahmniEncounterParser.resolveOrderType(order));
    }

    @Test
    public void resolveOrderType_readsNestedOpenMrsFormat() throws Exception {
        JsonNode order = objectMapper.readTree("{\"orderType\":{\"name\":\"Test Order\",\"display\":\"Test Order\"}}");
        assertEquals("Test Order", BahmniEncounterParser.resolveOrderType(order));
    }

    @Test
    public void resolveConceptDisplay_readsBahmniConceptNameString() throws Exception {
        JsonNode order = objectMapper
                .readTree("{\"concept\":{\"uuid\":\"33cb5232-172e-4769-ad2d-49fadaafc318\",\"name\":\"CD4 Test\"}}");
        assertEquals("CD4 Test", BahmniEncounterParser.resolveConceptDisplay(order));
    }

    @Test
    public void resolveLocationUuid_readsEncounterFields() throws Exception {
        JsonNode encounter = objectMapper.readTree("{\"locationUuid\":\"loc-1\",\"organizationId\":42}");
        assertEquals("loc-1", BahmniEncounterParser.resolveLocationUuid(encounter));
    }

    @Test
    public void resolveConceptUuid_prefersTopLevelConceptUuid() throws Exception {
        JsonNode order = objectMapper.readTree("{\"conceptUuid\":\"top-level\",\"concept\":{\"uuid\":\"nested\"}}");
        assertEquals("top-level", BahmniEncounterParser.resolveConceptUuid(order));
    }

    @Test
    public void isLaboratoryOrder_acceptsLabOrderType() throws Exception {
        JsonNode order = objectMapper.readTree("{\"orderType\":\"Lab Order\"}");
        assertTrue(BahmniEncounterParser.isLaboratoryOrder(order, "Lab Order"));
    }

    @Test
    public void isLaboratoryOrder_acceptsLabTestConceptClass() throws Exception {
        JsonNode order = objectMapper.readTree("{\"orderType\":\"\",\"concept\":{\"conceptClass\":\"LabTest\"}}");
        assertTrue(BahmniEncounterParser.isLaboratoryOrder(order, ""));
    }

    @Test
    public void isLaboratoryOrder_rejectsRadiologyOrder() throws Exception {
        JsonNode order = objectMapper
                .readTree("{\"orderType\":\"Radiology Order\",\"concept\":{\"conceptClass\":\"Radiology\"}}");
        assertFalse(BahmniEncounterParser.isLaboratoryOrder(order, "Radiology Order"));
    }
}
