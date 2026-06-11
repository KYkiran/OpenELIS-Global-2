package org.openelisglobal.atomfeed.mapping;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.service.PanelItemService;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads the {@code openmrs-concept-panel-map.csv} file and provides a lookup
 * from an OpenMRS concept UUID to the corresponding OE2 Panel and its member
 * test IDs.
 *
 * <p>CSV format (header row required):
 * <pre>
 * OPENMRS_CONCEPT_UUID,OE2_PANEL_ID,OE2_PANEL_NAME,SAMPLE_TYPE
 * </pre>
 *
 * <p>For each mapped panel concept, all {@link PanelItem} members are resolved
 * from the database at startup so that order creation can use the exact OE2
 * test IDs without additional queries.
 */
@Component
public class OpenMrsConceptPanelMapping {

    @Value("${atomfeed.openmrs.panelMapPath:/var/lib/openelis-global/openmrs/openmrs-concept-panel-map.csv}")
    private String panelMapPath;

    @Autowired
    private PanelService panelService;

    @Autowired
    private PanelItemService panelItemService;

    /** concept UUID → mapping info (panel ID + resolved test IDs) */
    private final Map<String, ConceptPanelMappingInfo> conceptToPanel = new HashMap<>();

    @PostConstruct
    public void init() {
        conceptToPanel.clear();
        try (InputStream in = new FileInputStream(panelMapPath)) {
            int count = parseCsv(in);
            LogEvent.logInfo(getClass().getSimpleName(), "init",
                    "Loaded OpenMRS panel mapping from " + panelMapPath + ", rows=" + count);
        } catch (IOException e) {
            LogEvent.logWarn(getClass().getSimpleName(), "init",
                    "Could not load OpenMRS panel mapping from " + panelMapPath + ": " + e.getMessage());
        }
    }

    /**
     * Returns the panel mapping for the given OpenMRS concept UUID, if present.
     *
     * @param openMrsConceptUuid the concept UUID from the OpenMRS order
     * @return an Optional containing the mapping info, or empty if not a panel
     */
    public Optional<ConceptPanelMappingInfo> resolve(String openMrsConceptUuid) {
        if (openMrsConceptUuid == null || openMrsConceptUuid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(conceptToPanel.get(openMrsConceptUuid.trim()));
    }

    public boolean hasMapping(String openMrsConceptUuid) {
        return resolve(openMrsConceptUuid).isPresent();
    }

    // -------------------------------------------------------------------------

    int parseCsv(InputStream in) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setIgnoreHeaderCase(true)
                .build();

        int count = 0;
        try (CSVParser parser = CSVParser.parse(in, java.nio.charset.StandardCharsets.UTF_8, format)) {
            for (CSVRecord record : parser) {
                String conceptUuid = record.get("OPENMRS_CONCEPT_UUID").trim();
                String oe2PanelId = record.get("OE2_PANEL_ID").trim();
                String oe2PanelName = record.isMapped("OE2_PANEL_NAME")
                        ? record.get("OE2_PANEL_NAME").trim() : "";
                String sampleType = record.isMapped("SAMPLE_TYPE")
                        ? record.get("SAMPLE_TYPE").trim() : "";

                if (conceptUuid.isEmpty() || oe2PanelId.isEmpty()) {
                    continue;
                }

                Panel panel = panelService.getPanelById(oe2PanelId);
                if (panel == null) {
                    LogEvent.logWarn(getClass().getSimpleName(), "parseCsv",
                            "Skipping row: OE2 panel id '" + oe2PanelId + "' not found for concept "
                                    + conceptUuid);
                    continue;
                }

                if (oe2PanelName.isEmpty()) {
                    oe2PanelName = panel.getPanelName();
                }

                List<String> testIds = resolveTestIds(oe2PanelId);
                conceptToPanel.put(conceptUuid,
                        new ConceptPanelMappingInfo(conceptUuid, oe2PanelId, oe2PanelName, sampleType, testIds));
                count++;
            }
        }
        return count;
    }

    /**
     * Resolves the ordered list of OE2 test IDs that belong to the given panel.
     * Returns an empty list if the panel has no items.
     */
    private List<String> resolveTestIds(String panelId) {
        List<PanelItem> items = panelItemService.getPanelItemsForPanel(panelId);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(items.size());
        for (PanelItem item : items) {
            if (item.getTest() != null) {
                ids.add(item.getTest().getId());
            }
        }
        return Collections.unmodifiableList(ids);
    }

    // -------------------------------------------------------------------------

    public static final class ConceptPanelMappingInfo {
        private final String openMrsConceptUuid;
        private final String oe2PanelId;
        private final String oe2PanelName;
        private final String sampleType;
        private final List<String> oe2TestIds;

        public ConceptPanelMappingInfo(String openMrsConceptUuid, String oe2PanelId,
                String oe2PanelName, String sampleType, List<String> oe2TestIds) {
            this.openMrsConceptUuid = openMrsConceptUuid;
            this.oe2PanelId = oe2PanelId;
            this.oe2PanelName = oe2PanelName;
            this.sampleType = sampleType;
            this.oe2TestIds = oe2TestIds;
        }

        public String getOpenMrsConceptUuid() {
            return openMrsConceptUuid;
        }

        public String getOe2PanelId() {
            return oe2PanelId;
        }

        public String getOe2PanelName() {
            return oe2PanelName;
        }

        public String getSampleType() {
            return sampleType;
        }

        /** OE2 test IDs belonging to this panel; may be empty if panel has no items yet. */
        public List<String> getOe2TestIds() {
            return oe2TestIds;
        }
    }
}
