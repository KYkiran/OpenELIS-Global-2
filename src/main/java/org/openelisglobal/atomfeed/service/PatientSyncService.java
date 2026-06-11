package org.openelisglobal.atomfeed.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientidentity.service.PatientIdentityService;
import org.openelisglobal.patientidentity.valueholder.PatientIdentity;
import org.openelisglobal.patientidentitytype.dao.PatientIdentityTypeDAO;
import org.openelisglobal.patientidentitytype.valueholder.PatientIdentityType;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs an OpenMRS patient (represented as a parsed JsonNode from the OpenMRS
 * REST API {@code /openmrs/ws/rest/v1/patient/{uuid}?v=full} response) into the
 * OpenELIS Global 2 patient tables.
 *
 * <p>This service is the OE2 equivalent of Bahmni's {@code BahmniPatientService}.
 * It creates a new Patient+Person if not found, or updates the existing record.
 * Identity type "ST" (the Bahmni/OpenMRS registration number) is synced when
 * present in the patient identifier list.
 */
@Service
public class PatientSyncService {

    private static final Logger log = LoggerFactory.getLogger(PatientSyncService.class);

    /** Identity type name used by Bahmni for the primary registration number. */
    private static final String ST_IDENTITY_TYPE = "ST";

    /** Fallback sysUserId for all atomfeed-driven inserts/updates. */
    private static final String ATOMFEED_SYS_USER_ID = "1";

    @Autowired
    private PatientService patientService;

    @Autowired
    private PersonService personService;

    @Autowired
    private PatientIdentityService patientIdentityService;

    @Autowired
    private PatientIdentityTypeDAO patientIdentityTypeDAO;

    /**
     * Upserts the patient represented by {@code patientJson} (OpenMRS
     * {@code /patient/{uuid}?v=full} response) into OE2.
     *
     * <ul>
     *   <li>If a patient with the same GUID (fhirUuid) already exists → update
     *       Person fields and gender/DOB.
     *   <li>If not found → create Person + Patient; set fhirUuid to the OpenMRS
     *       person UUID; optionally persist the ST identity.
     * </ul>
     *
     * @param patientJson the full OpenMRS patient JSON node (top-level object)
     */
    @Transactional
    public void upsert(JsonNode patientJson) {
        if (patientJson == null || patientJson.isMissingNode()) {
            log.warn("PatientSyncService.upsert called with null/missing JSON – skipping");
            return;
        }

        JsonNode personNode = patientJson.path("person");
        String personUuid = personNode.path("uuid").asText("");

        if (StringUtils.isBlank(personUuid)) {
            log.warn("OpenMRS patient JSON has no person.uuid – skipping sync");
            return;
        }

        Patient existing = patientService.getPatientForGuid(personUuid);
        if (existing != null) {
            updateExisting(existing, personNode, patientJson);
        } else {
            createNew(personNode, patientJson, personUuid);
        }
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private void updateExisting(Patient patient, JsonNode personNode, JsonNode patientJson) {
        log.info("Updating existing OE2 patient for OpenMRS uuid {}", patientService.getGUID(patient));

        Person person = patientService.getPerson(patient);
        if (person == null) {
            person = new Person();
            person.setSysUserId(ATOMFEED_SYS_USER_ID);
        }

        applyPersonFields(person, personNode);

        if (StringUtils.isNotBlank(person.getId())) {
            personService.update(person);
        } else {
            personService.insert(person);
            patient.setPerson(person);
        }

        applyPatientFields(patient, personNode);
        patientService.update(patient);
        log.info("Updated OE2 patient id={}", patient.getId());
    }

    private void createNew(JsonNode personNode, JsonNode patientJson, String personUuid) {
        log.info("Creating new OE2 patient for OpenMRS person uuid {}", personUuid);

        Person person = new Person();
        person.setSysUserId(ATOMFEED_SYS_USER_ID);
        applyPersonFields(person, personNode);
        personService.insert(person);

        Patient patient = new Patient();
        patient.setPerson(person);
        patient.setSysUserId(ATOMFEED_SYS_USER_ID);
        patient.setFhirUuid(UUID.fromString(personUuid));
        applyPatientFields(patient, personNode);
        patientService.insert(patient);

        syncPrimaryIdentity(patient, patientJson);
        log.info("Created OE2 patient id={} for OpenMRS uuid={}", patient.getId(), personUuid);
    }

    /** Copies name fields from the OpenMRS person node into the OE2 Person entity. */
    private void applyPersonFields(Person person, JsonNode personNode) {
        JsonNode preferredName = personNode.path("preferredName");
        if (!preferredName.isMissingNode()) {
            safeSetField(person::setFirstName, preferredName.path("givenName").asText(null));
            safeSetField(person::setMiddleName, preferredName.path("middleName").asText(null));
            safeSetField(person::setLastName, preferredName.path("familyName").asText(null));
        }

        JsonNode preferredAddress = personNode.path("preferredAddress");
        if (!preferredAddress.isMissingNode()) {
            safeSetField(person::setStreetAddress, preferredAddress.path("address1").asText(null));
            safeSetField(person::setCity, preferredAddress.path("cityVillage").asText(null));
            safeSetField(person::setState, preferredAddress.path("stateProvince").asText(null));
            safeSetField(person::setCountry, preferredAddress.path("country").asText(null));
        }
    }

    /** Sets gender, DOB, and externalId on the Patient entity from the OpenMRS response. */
    private void applyPatientFields(Patient patient, JsonNode personNode) {
        String gender = personNode.path("gender").asText(null);
        if (StringUtils.isNotBlank(gender)) {
            patient.setGender(gender);
        }

        String birthdate = personNode.path("birthdate").asText(null);
        if (StringUtils.isNotBlank(birthdate) && birthdate.length() >= 10) {
            // OpenMRS ISO date: "YYYY-MM-DDThh:mm:ss.sss+0000" or just "YYYY-MM-DD"
            String dateOnly = birthdate.substring(0, 10);
            try {
                patient.setBirthDate(Timestamp.valueOf(dateOnly + " 00:00:00"));
            } catch (Exception e) {
                log.warn("Could not parse birthdate '{}' for patient – using display only", birthdate);
                patient.setBirthDateForDisplay(dateOnly.replace("-", "/"));
            }
        }

        patient.setSysUserId(ATOMFEED_SYS_USER_ID);
    }

    /**
     * Persists the preferred/primary identifier from the OpenMRS patient JSON as
     * a PatientIdentity with type "ST" in OE2, mirroring what BahmniPatientService
     * did via the REGISTRATION_KEY_NAME constant.
     */
    private void syncPrimaryIdentity(Patient patient, JsonNode patientJson) {
        JsonNode identifiers = patientJson.path("identifiers");
        if (!identifiers.isArray()) {
            return;
        }

        PatientIdentityType stType = patientIdentityTypeDAO.getNamedIdentityType(ST_IDENTITY_TYPE);
        if (stType == null) {
            log.warn("PatientIdentityType '{}' not found – skipping identity sync", ST_IDENTITY_TYPE);
            return;
        }

        // Prefer the preferred identifier; fall back to the first one
        String identifierValue = null;
        for (JsonNode identifier : identifiers) {
            boolean preferred = identifier.path("preferred").asBoolean(false);
            String value = identifier.path("identifier").asText(null);
            if (preferred && StringUtils.isNotBlank(value)) {
                identifierValue = value;
                break;
            }
            if (identifierValue == null && StringUtils.isNotBlank(value)) {
                identifierValue = value;
            }
        }

        if (StringUtils.isBlank(identifierValue)) {
            log.debug("No identifier value found for patient {}", patient.getId());
            return;
        }

        // Check if identity already exists for this patient+type before inserting
        List<PatientIdentity> existing = patientIdentityService
                .getPatientIdentitiesForPatient(patient.getId());
        for (PatientIdentity pi : existing) {
            if (stType.getId().equals(pi.getIdentityTypeId())) {
                // Update if changed
                if (!identifierValue.equals(pi.getIdentityData())) {
                    pi.setIdentityData(identifierValue);
                    pi.setSysUserId(ATOMFEED_SYS_USER_ID);
                    patientIdentityService.update(pi);
                }
                return;
            }
        }

        PatientIdentity newIdentity = new PatientIdentity();
        newIdentity.setPatientId(patient.getId());
        newIdentity.setIdentityTypeId(stType.getId());
        newIdentity.setIdentityData(identifierValue);
        newIdentity.setSysUserId(ATOMFEED_SYS_USER_ID);
        patientIdentityService.insert(newIdentity);
        log.debug("Inserted ST identity '{}' for OE2 patient {}", identifierValue, patient.getId());
    }

    @FunctionalInterface
    private interface StringSetter {
        void accept(String value);
    }

    private void safeSetField(StringSetter setter, String value) {
        if (StringUtils.isNotBlank(value)) {
            setter.accept(value);
        }
    }
}
