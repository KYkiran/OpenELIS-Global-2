package org.openelisglobal.atomfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient;
import org.openelisglobal.atomfeed.dto.OpenMrsOrganizationDto;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.organization.service.OrganizationService;
import org.openelisglobal.organization.service.OrganizationTypeService;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.organization.valueholder.OrganizationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenMrsOrganizationSyncService {

    private static final Logger log = LoggerFactory.getLogger(OpenMrsOrganizationSyncService.class);
    private static final String REFERRING_CLINIC_TYPE = "referingClinic";
    private static final String DEFAULT_SYS_USER_ID = "1";

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationTypeService organizationTypeService;

    @Autowired
    private OpenMrsHttpClient openMrsHttpClient;

    @Value("${atomfeed.openmrs.writebackOpenelisOrgId:true}")
    private boolean writebackOpenelisOrgId;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Organization upsert(OpenMrsOrganizationDto dto) {
        if (dto == null || dto.getOrganizationId() == null) {
            log.warn("Skipping organization sync with no OpenMRS organization id");
            return null;
        }

        String externalId = String.valueOf(dto.getOrganizationId());
        Organization existing = organizationService.getOrganizationByExternalId(externalId);
        if (existing == null && StringUtils.isNotBlank(dto.getIdUuid())) {
            existing = organizationService.getOrganizationByFhirId(dto.getIdUuid());
        }

        boolean created = existing == null;
        Organization org = created ? buildNewOrganization(dto, externalId) : existing;
        applyFields(org, dto);

        if (created) {
            organizationService.insert(org);
            linkReferringClinicType(org);
            log.info("Created OE2 organization {} for OpenMRS org {}", org.getId(), externalId);
        } else {
            organizationService.update(org);
            log.info("Updated OE2 organization {} for OpenMRS org {}", org.getId(), externalId);
        }

        if (writebackOpenelisOrgId && created && GenericValidator.isBlankOrNull(dto.getOpenelisOrganizationId())) {
            writeBackOpenelisOrganizationId(dto.getOrganizationId(), org.getId());
        }

        return org;
    }

    private Organization buildNewOrganization(OpenMrsOrganizationDto dto, String externalId) {
        Organization org = new Organization();
        org.setExternalId(externalId);
        org.setSysUserId(DEFAULT_SYS_USER_ID);
        org.setMlsSentinelLabFlag(IActionConstants.NO);
        org.setMlsLabFlag(IActionConstants.NO);
        org.setShortName(truncate("OMRS" + externalId, 15));
        if (StringUtils.isNotBlank(dto.getIdUuid())) {
            org.setFhirUuid(UUID.fromString(dto.getIdUuid()));
        } else {
            org.setFhirUuid(UUID.randomUUID());
        }
        return org;
    }

    private void applyFields(Organization org, OpenMrsOrganizationDto dto) {
        if (StringUtils.isNotBlank(dto.getOrganizationName())) {
            org.setOrganizationName(truncate(dto.getOrganizationName(), 40));
        }
        if (dto.getCity() != null) {
            org.setCity(truncate(dto.getCity(), 30));
        }
        if (dto.getAddressLine1() != null) {
            org.setStreetAddress(truncate(dto.getAddressLine1(), 30));
        }
        if (dto.getState() != null) {
            org.setState(truncate(dto.getState(), 2));
        }
        if (dto.getPincode() != null) {
            org.setZipCode(truncate(dto.getPincode(), 10));
        }
        if (dto.getEmail() != null) {
            org.setInternetAddress(truncate(dto.getEmail(), 40));
        }
        if (dto.getIsActive() != null) {
            org.setIsActive(dto.getIsActive() == 1 ? IActionConstants.YES : IActionConstants.NO);
        } else if (GenericValidator.isBlankOrNull(org.getIsActive())) {
            org.setIsActive(IActionConstants.YES);
        }
        if (StringUtils.isNotBlank(dto.getIdUuid()) && org.getFhirUuid() == null) {
            org.setFhirUuid(UUID.fromString(dto.getIdUuid()));
        }
        if (GenericValidator.isBlankOrNull(org.getExternalId()) && dto.getOrganizationId() != null) {
            org.setExternalId(String.valueOf(dto.getOrganizationId()));
        }
        org.setSysUserId(DEFAULT_SYS_USER_ID);
    }

    private void linkReferringClinicType(Organization org) {
        OrganizationType referringType = organizationTypeService.getOrganizationTypeByName(REFERRING_CLINIC_TYPE);
        if (referringType != null) {
            organizationService.linkOrganizationAndType(org, referringType.getId());
        }
    }

    private void writeBackOpenelisOrganizationId(Integer openMrsOrgId, String oe2OrgId) {
        try {
            String body = objectMapper.createObjectNode().put("openelis_organization_id", oe2OrgId).toString();
            openMrsHttpClient.putJson("/openmrs/ws/rest/v1/organizations/" + openMrsOrgId, body);
            log.info("Wrote back openelis_organization_id={} to OpenMRS org {}", oe2OrgId, openMrsOrgId);
        } catch (Exception e) {
            log.warn("Failed to write back openelis_organization_id for OpenMRS org {}: {}", openMrsOrgId,
                    e.getMessage());
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
