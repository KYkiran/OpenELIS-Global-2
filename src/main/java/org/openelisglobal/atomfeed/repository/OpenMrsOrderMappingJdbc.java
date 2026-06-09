package org.openelisglobal.atomfeed.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OpenMrsOrderMappingJdbc {

    private static final String SCHEMA = "clinlims.";
    private final JdbcTemplate jdbcTemplate;

    public OpenMrsOrderMappingJdbc(@Qualifier("atomFeedJdbcTemplate") JdbcTemplate atomFeedJdbcTemplate) {
        this.jdbcTemplate = atomFeedJdbcTemplate;
    }

    public void insert(String encounterUuid, String orderUuid, String oe2PatientId, String openmrsOrganizationId,
            String status, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO " + SCHEMA + "openmrs_order_mapping "
                        + "(openmrs_encounter_uuid, openmrs_order_uuid, oe2_patient_id, openmrs_organization_id, "
                        + "status, error_message) " + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (openmrs_order_uuid) DO UPDATE SET "
                        + "status = EXCLUDED.status, error_message = EXCLUDED.error_message, "
                        + "oe2_patient_id = EXCLUDED.oe2_patient_id, "
                        + "openmrs_organization_id = EXCLUDED.openmrs_organization_id, updated_at = NOW()",
                encounterUuid, orderUuid, oe2PatientId, openmrsOrganizationId, status, errorMessage);
    }

    public void markAccessioned(String orderUuid, String accessionNumber, String oe2PatientId) {
        jdbcTemplate.update(
                "UPDATE " + SCHEMA + "openmrs_order_mapping "
                        + "SET status = ?, oe2_accession_number = ?, oe2_patient_id = COALESCE(?, oe2_patient_id), "
                        + "updated_at = NOW() WHERE openmrs_order_uuid = ?",
                OpenMrsOrderMappingStatus.ACCESSED, accessionNumber, oe2PatientId, orderUuid);
    }

    public void updateStatus(String orderUuid, String status, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE " + SCHEMA + "openmrs_order_mapping "
                        + "SET status = ?, error_message = ?, updated_at = NOW() WHERE openmrs_order_uuid = ?",
                status, errorMessage, orderUuid);
    }

    public boolean exists(String orderUuid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA + "openmrs_order_mapping WHERE openmrs_order_uuid = ?", Integer.class,
                orderUuid);
        return count != null && count > 0;
    }

    public String getStatus(String orderUuid) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT status FROM " + SCHEMA + "openmrs_order_mapping WHERE openmrs_order_uuid = ?", String.class,
                    orderUuid);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public String getOrganizationIdByAccession(String accessionNumber) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT openmrs_organization_id FROM " + SCHEMA + "openmrs_order_mapping "
                            + "WHERE oe2_accession_number = ? ORDER BY updated_at DESC LIMIT 1",
                    String.class, accessionNumber);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
