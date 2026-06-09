package org.openelisglobal.atomfeed.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient;
import org.openelisglobal.atomfeed.dto.OpenMrsOrganizationDto;
import org.openelisglobal.organization.service.OrganizationService;
import org.openelisglobal.organization.service.OrganizationTypeService;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.organization.valueholder.OrganizationType;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class OpenMrsOrganizationSyncServiceTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private OrganizationTypeService organizationTypeService;

    @Mock
    private OpenMrsHttpClient openMrsHttpClient;

    @InjectMocks
    private OpenMrsOrganizationSyncService syncService;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(syncService, "writebackOpenelisOrgId", true);
        OrganizationType referringType = new OrganizationType();
        referringType.setId("10");
        referringType.setName("referingClinic");
        when(organizationTypeService.getOrganizationTypeByName("referingClinic")).thenReturn(referringType);
    }

    @Test
    public void upsert_createsOrganizationAndWritesBack() {
        OpenMrsOrganizationDto dto = new OpenMrsOrganizationDto();
        dto.setOrganizationId(42);
        dto.setOrganizationName("Test Clinic");
        dto.setIdUuid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        dto.setIsActive(1);

        when(organizationService.getOrganizationByExternalId("42")).thenReturn(null);
        when(organizationService.getOrganizationByFhirId(dto.getIdUuid())).thenReturn(null);
        when(organizationService.insert(any(Organization.class))).thenAnswer(invocation -> {
            Organization org = invocation.getArgument(0);
            org.setId("99");
            return "99";
        });

        Organization result = syncService.upsert(dto);

        assertNotNull(result);
        assertEquals("99", result.getId());
        verify(organizationService).linkOrganizationAndType(any(Organization.class), eq("10"));
    }

    @Test
    public void upsert_updatesExistingOrganization() {
        Organization existing = new Organization();
        existing.setId("5");
        existing.setExternalId("42");
        existing.setOrganizationName("Old Name");
        existing.setIsActive("Y");

        OpenMrsOrganizationDto dto = new OpenMrsOrganizationDto();
        dto.setOrganizationId(42);
        dto.setOrganizationName("Updated Clinic");
        dto.setOpenelisOrganizationId("5");

        when(organizationService.getOrganizationByExternalId("42")).thenReturn(existing);

        Organization result = syncService.upsert(dto);

        assertEquals("Updated Clinic", result.getOrganizationName());
        verify(organizationService).update(existing);
        verify(organizationService, never()).insert(any(Organization.class));
    }
}
