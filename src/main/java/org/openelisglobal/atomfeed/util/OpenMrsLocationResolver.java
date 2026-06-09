package org.openelisglobal.atomfeed.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.atomfeed.client.OpenMrsHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenMrsLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(OpenMrsLocationResolver.class);
    private static final int MAX_PARENT_DEPTH = 8;
    private static final String ORGANIZATION_TAG = "Organization";

    @Autowired
    private OpenMrsHttpClient openMrsHttpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Integer resolveOrganizationId(JsonNode encounter) {
        Integer direct = readOrganizationIdFromEncounter(encounter);
        if (direct != null) {
            return direct;
        }

        String locationUuid = BahmniEncounterParser.resolveLocationUuid(encounter);
        if (StringUtils.isBlank(locationUuid)) {
            return null;
        }

        try {
            return resolveOrganizationIdFromLocationUuid(locationUuid);
        } catch (Exception e) {
            log.warn("Could not resolve organization from location {}: {}", locationUuid, e.getMessage());
            return null;
        }
    }

    public String resolveLocationUuid(JsonNode encounter) {
        return BahmniEncounterParser.resolveLocationUuid(encounter);
    }

    private Integer readOrganizationIdFromEncounter(JsonNode encounter) {
        if (encounter == null) {
            return null;
        }
        if (encounter.hasNonNull("organizationId")) {
            return parseInteger(encounter.get("organizationId"));
        }
        if (encounter.hasNonNull("organization_id")) {
            return parseInteger(encounter.get("organization_id"));
        }
        return null;
    }

    private Integer resolveOrganizationIdFromLocationUuid(String locationUuid) throws Exception {
        JsonNode location = fetchLocation(locationUuid);
        for (int depth = 0; depth < MAX_PARENT_DEPTH && location != null; depth++) {
            if (hasOrganizationTag(location)) {
                if (location.hasNonNull("id")) {
                    return location.get("id").asInt();
                }
                JsonNode attributes = location.path("attributes");
                if (attributes.isArray()) {
                    for (JsonNode attribute : attributes) {
                        JsonNode value = attribute.path("value");
                        if (value.isNumber()) {
                            return value.asInt();
                        }
                    }
                }
            }

            JsonNode parent = location.path("parentLocation");
            if (parent.isMissingNode() || parent.isNull()) {
                break;
            }
            String parentUuid = parent.path("uuid").asText(null);
            if (StringUtils.isBlank(parentUuid)) {
                break;
            }
            location = fetchLocation(parentUuid);
        }
        return null;
    }

    private JsonNode fetchLocation(String locationUuid) throws Exception {
        String json = openMrsHttpClient.fetchJson("/openmrs/ws/rest/v1/location/" + locationUuid + "?v=full");
        return objectMapper.readTree(json);
    }

    private boolean hasOrganizationTag(JsonNode location) {
        JsonNode tags = location.path("tags");
        if (!tags.isArray()) {
            return false;
        }
        for (JsonNode tag : tags) {
            String display = tag.path("display").asText(tag.path("name").asText(""));
            if (ORGANIZATION_TAG.equalsIgnoreCase(display) || display.toLowerCase().contains("organization")) {
                return true;
            }
        }
        return false;
    }

    private Integer parseInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
