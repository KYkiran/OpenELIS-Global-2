package org.openelisglobal.atomfeed.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openelisglobal.BaseWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class OpenMrsOrderMappingJdbcTest extends BaseWebContextSensitiveTest {

    @Autowired
    private OpenMrsOrderMappingJdbc orderMappingJdbc;

    @Autowired
    private JdbcTemplate atomFeedJdbcTemplate;

    @Test
    public void insertAndMarkAccessioned_updatesMappingRow() {
        String orderUuid = "openmrs-order-test-" + System.currentTimeMillis();
        String encounterUuid = "openmrs-encounter-test-" + System.currentTimeMillis();

        orderMappingJdbc.insert(encounterUuid, orderUuid, "patient-1", "42", OpenMrsOrderMappingStatus.QUEUED, null);
        assertEquals(OpenMrsOrderMappingStatus.QUEUED, orderMappingJdbc.getStatus(orderUuid));
        assertTrue(orderMappingJdbc.exists(orderUuid));

        orderMappingJdbc.markAccessioned(orderUuid, "2026-00001", "patient-1");
        assertEquals(OpenMrsOrderMappingStatus.ACCESSED, orderMappingJdbc.getStatus(orderUuid));

        String accession = atomFeedJdbcTemplate.queryForObject(
                "SELECT oe2_accession_number FROM clinlims.openmrs_order_mapping WHERE openmrs_order_uuid = ?",
                String.class, orderUuid);
        assertEquals("2026-00001", accession);

        atomFeedJdbcTemplate.update("DELETE FROM clinlims.openmrs_order_mapping WHERE openmrs_order_uuid = ?",
                orderUuid);
    }
}
