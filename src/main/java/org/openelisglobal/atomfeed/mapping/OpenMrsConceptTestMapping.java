package org.openelisglobal.atomfeed.mapping;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenMrsConceptTestMapping {

    private static final String DEFAULT_CSV_PATH = "/var/lib/openelis-global/openmrs/openmrs-concept-test-map.csv";

    @Value("${atomfeed.openmrs.conceptMapPath:/var/lib/openelis-global/openmrs/openmrs-concept-test-map.csv}")
    private String conceptMapPath;

    @Autowired
    private TestService testService;

    private final Map<String, ConceptTestMappingInfo> conceptToTest = new HashMap<>();

    @PostConstruct
    public void init() {
        conceptToTest.clear();
        try (InputStream in = new FileInputStream(conceptMapPath)) {
            int count = parseCsv(in);
            LogEvent.logInfo(getClass().getSimpleName(), "init",
                    "Loaded OpenMRS concept mapping from " + conceptMapPath + ", rows=" + count);
        } catch (IOException e) {
            LogEvent.logWarn(getClass().getSimpleName(), "init",
                    "Could not load OpenMRS concept mapping from " + conceptMapPath + ": " + e.getMessage());
        }
    }

    public Optional<ConceptTestMappingInfo> resolve(String openMrsConceptUuid) {
        if (openMrsConceptUuid == null || openMrsConceptUuid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(conceptToTest.get(openMrsConceptUuid.trim()));
    }

    public boolean hasMapping(String openMrsConceptUuid) {
        return resolve(openMrsConceptUuid).isPresent();
    }

    int parseCsv(InputStream in) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true)
                .setTrim(true).setIgnoreHeaderCase(true).build();
        int count = 0;
        try (CSVParser parser = CSVParser.parse(in, java.nio.charset.StandardCharsets.UTF_8, format)) {
            for (CSVRecord record : parser) {
                String conceptUuid = record.get("OPENMRS_CONCEPT_UUID").trim();
                String oe2TestId = record.get("OE2_TEST_ID").trim();
                String oe2TestName = record.isMapped("OE2_TEST_NAME") ? record.get("OE2_TEST_NAME").trim() : "";
                String sampleType = record.isMapped("SAMPLE_TYPE") ? record.get("SAMPLE_TYPE").trim() : "";

                if (conceptUuid.isEmpty() || oe2TestId.isEmpty()) {
                    continue;
                }

                Test test = testService.getTestById(oe2TestId);
                if (test == null) {
                    LogEvent.logWarn(getClass().getSimpleName(), "parseCsv",
                            "Skipping row: OE2 test id '" + oe2TestId + "' not found for concept " + conceptUuid);
                    continue;
                }

                if (oe2TestName.isEmpty()) {
                    oe2TestName = test.getLocalizedName();
                }

                conceptToTest.put(conceptUuid,
                        new ConceptTestMappingInfo(conceptUuid, oe2TestId, oe2TestName, sampleType));
                count++;
            }
        }
        return count;
    }

    public static final class ConceptTestMappingInfo {
        private final String openMrsConceptUuid;
        private final String oe2TestId;
        private final String oe2TestName;
        private final String sampleType;

        public ConceptTestMappingInfo(String openMrsConceptUuid, String oe2TestId, String oe2TestName,
                String sampleType) {
            this.openMrsConceptUuid = openMrsConceptUuid;
            this.oe2TestId = oe2TestId;
            this.oe2TestName = oe2TestName;
            this.sampleType = sampleType;
        }

        public String getOpenMrsConceptUuid() {
            return openMrsConceptUuid;
        }

        public String getOe2TestId() {
            return oe2TestId;
        }

        public String getOe2TestName() {
            return oe2TestName;
        }

        public String getSampleType() {
            return sampleType;
        }
    }
}
