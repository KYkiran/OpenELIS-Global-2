package org.openelisglobal.atomfeed.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.atomfeed.mapping.OpenMrsConceptTestMapping.ConceptTestMappingInfo;
import org.openelisglobal.test.service.TestService;

@RunWith(MockitoJUnitRunner.class)
public class OpenMrsConceptTestMappingTest {

    @Mock
    private TestService testService;

    private OpenMrsConceptTestMapping mapping;

    @Before
    public void setUp() {
        mapping = new OpenMrsConceptTestMapping();
        injectField(mapping, "testService", testService);
    }

    @Test
    public void parseCsv_loadsValidRows() throws IOException {
        org.openelisglobal.test.valueholder.Test test = new org.openelisglobal.test.valueholder.Test();
        test.setId("101");
        test.setDescription("Hemoglobin");
        when(testService.getTestById("101")).thenReturn(test);

        String csv = "OPENMRS_CONCEPT_UUID,OE2_TEST_ID,OE2_TEST_NAME,SAMPLE_TYPE\n"
                + "aaa-bbb-ccc,101,Hemoglobin,5\n";
        Path tempFile = Files.createTempFile("openmrs-concept-map", ".csv");
        Files.writeString(tempFile, csv);

        try (InputStream in = Files.newInputStream(tempFile)) {
            int count = mapping.parseCsv(in);
            assertEquals(1, count);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        assertTrue(mapping.hasMapping("aaa-bbb-ccc"));
        ConceptTestMappingInfo info = mapping.resolve("aaa-bbb-ccc").orElseThrow();
        assertEquals("101", info.getOe2TestId());
        assertEquals("Hemoglobin", info.getOe2TestName());
        assertEquals("5", info.getSampleType());
    }

    @Test
    public void parseCsv_skipsUnknownTestId() throws IOException {
        when(testService.getTestById("999")).thenReturn(null);

        String csv = "OPENMRS_CONCEPT_UUID,OE2_TEST_ID,OE2_TEST_NAME,SAMPLE_TYPE\n"
                + "aaa-bbb-ccc,999,Missing Test,5\n";
        Path tempFile = Files.createTempFile("openmrs-concept-map-invalid", ".csv");
        Files.writeString(tempFile, csv);

        try (InputStream in = Files.newInputStream(tempFile)) {
            int count = mapping.parseCsv(in);
            assertEquals(0, count);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        assertFalse(mapping.hasMapping("aaa-bbb-ccc"));
    }

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
