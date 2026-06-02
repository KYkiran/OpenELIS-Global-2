package org.openelisglobal.atomfeed.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;

public final class BahmniEncounterParser {

    private BahmniEncounterParser() {
    }

    public static String resolveEncounterUuid(JsonNode encounter) {
        return firstNonBlank(encounter.path("encounterUuid").asText(null), encounter.path("uuid").asText(null));
    }

    public static String resolvePatientUuid(JsonNode encounter) {
        return firstNonBlank(encounter.path("patientUuid").asText(null),
                encounter.path("patient").path("uuid").asText(null));
    }

    public static String resolveOrderType(JsonNode order) {
        JsonNode orderTypeNode = order.get("orderType");
        if (orderTypeNode != null && orderTypeNode.isTextual()) {
            return orderTypeNode.asText("");
        }
        if (orderTypeNode != null && orderTypeNode.isObject()) {
            return firstNonBlank(orderTypeNode.path("name").asText(null),
                    orderTypeNode.path("display").asText(null));
        }
        return order.path("type").asText("");
    }

    public static String resolveConceptUuid(JsonNode order) {
        return firstNonBlank(order.path("conceptUuid").asText(null),
                order.path("concept").path("uuid").asText(null));
    }

    public static String resolveConceptDisplay(JsonNode order) {
        JsonNode concept = order.path("concept");
        if (concept.isMissingNode() || concept.isNull()) {
            return "unknown";
        }

        String display = concept.path("display").asText("");
        if (StringUtils.isNotBlank(display)) {
            return display;
        }

        JsonNode nameNode = concept.get("name");
        if (nameNode != null && nameNode.isTextual()) {
            return nameNode.asText("unknown");
        }
        if (nameNode != null && nameNode.isObject()) {
            return firstNonBlank(nameNode.path("name").asText(null), nameNode.path("display").asText(null), "unknown");
        }

        return firstNonBlank(concept.path("shortName").asText(null), "unknown");
    }

    public static String resolveConceptClass(JsonNode order) {
        JsonNode concept = order.path("concept");
        if (concept.isMissingNode()) {
            return "";
        }
        JsonNode conceptClass = concept.get("conceptClass");
        if (conceptClass == null || conceptClass.isNull()) {
            return "";
        }
        if (conceptClass.isTextual()) {
            return conceptClass.asText("");
        }
        return conceptClass.path("display").asText(conceptClass.path("name").asText(""));
    }

    public static boolean isLaboratoryOrder(JsonNode order, String orderType) {
        String normalizedType = orderType == null ? "" : orderType.toLowerCase();
        if (normalizedType.contains("lab") || normalizedType.contains("test")) {
            return true;
        }

        String conceptClass = resolveConceptClass(order).toLowerCase();
        return conceptClass.contains("lab") || conceptClass.equals("test");
    }

    public static boolean shouldCreateOrder(JsonNode order) {
        String action = order.path("action").asText("NEW");
        return StringUtils.isBlank(action) || "NEW".equalsIgnoreCase(action);
    }

    public static String resolveCancelTargetUuid(JsonNode order) {
        return firstNonBlank(order.path("previousOrderUuid").asText(null), order.path("uuid").asText(null));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
