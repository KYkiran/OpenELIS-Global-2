package org.openelisglobal.atomfeed.consumer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import org.ict4h.atomfeed.client.AtomFeedProperties;
import org.ict4h.atomfeed.client.repository.AllFailedEvents;
import org.ict4h.atomfeed.client.repository.AllFeeds;
import org.ict4h.atomfeed.client.repository.AllMarkers;
import org.ict4h.atomfeed.client.repository.datasource.HttpClient;
import org.ict4h.atomfeed.client.service.AtomFeedClient;
import org.ict4h.atomfeed.transaction.AFTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled poller for the OpenMRS patient AtomFeed.
 *
 * <p>This is the OE2 equivalent of Bahmni's patient feed job. It polls
 * {@code atomfeed.openmrs.patientFeedUrl} on the same interval as the encounter
 * feed. Patient events are dispatched to {@link PatientEventWorker}.
 *
 * <p>The consumer is disabled (no-op) when:
 * <ul>
 *   <li>{@code atomfeed.enabled=false} (master toggle), or
 *   <li>{@code atomfeed.openmrs.patientFeedUrl} is blank (not configured).
 * </ul>
 */
@Component
public class PatientFeedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatientFeedConsumer.class);

    @Value("${atomfeed.enabled:false}")
    private boolean atomFeedEnabled;

    /**
     * Patient feed URL, e.g.
     * {@code http://openmrs:8080/openmrs/ws/atomfeed/patient/recent}.
     * Intentionally optional — blank means patient sync is not used.
     */
    @Value("${atomfeed.openmrs.patientFeedUrl:}")
    private String patientFeedUrl;

    @Value("${atomfeed.openmrs.username}")
    private String openmrsUsername;

    @Value("${atomfeed.openmrs.password}")
    private String openmrsPassword;

    @Autowired
    private AllMarkers allMarkers;

    @Autowired
    private AllFailedEvents allFailedEvents;

    @Autowired
    private AFTransactionManager afTransactionManager;

    @Autowired
    private PatientEventWorker patientEventWorker;

    /**
     * Polls the patient feed. Runs on the same {@code atomfeed.pollIntervalMs}
     * cadence as the encounter and organization feeds (default: every 30 s).
     */
    @Scheduled(fixedDelayString = "${atomfeed.pollIntervalMs:30000}")
    public void pollPatientFeed() {
        if (!atomFeedEnabled) {
            log.trace("AtomFeed patient polling disabled (atomfeed.enabled=false)");
            return;
        }
        if (patientFeedUrl == null || patientFeedUrl.isBlank()) {
            log.trace("Patient feed URL not configured (atomfeed.openmrs.patientFeedUrl) – skipping patient sync");
            return;
        }

        log.debug("Polling OpenMRS patient feed: {}", patientFeedUrl);
        try {
            HttpClient authHttpClient = buildAuthHttpClient();
            AtomFeedClient feedClient = new AtomFeedClient(
                    new AllFeeds(authHttpClient),
                    allMarkers,
                    allFailedEvents,
                    new AtomFeedProperties(),
                    afTransactionManager,
                    URI.create(patientFeedUrl),
                    patientEventWorker);
            feedClient.processEvents();
            log.debug("Patient feed poll complete");
        } catch (Exception e) {
            log.error("Error polling patient feed: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds an authenticated {@link HttpClient} that the ict4h AtomFeed library
     * uses to fetch feed pages. Mirrors the same pattern used in
     * {@link EncounterFeedConsumer} and {@link OrganizationFeedConsumer}.
     */
    private HttpClient buildAuthHttpClient() {
        return (uri, properties, cookies) -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/atom+xml");
                conn.setRequestProperty(
                        "Authorization",
                        "Basic " + Base64.getEncoder()
                                .encodeToString((openmrsUsername + ":" + openmrsPassword).getBytes()));
                conn.setConnectTimeout(properties.getConnectTimeout());
                conn.setReadTimeout(properties.getReadTimeout());
                conn.connect();

                if (conn.getResponseCode() != 200) {
                    throw new RuntimeException(
                            "Patient feed request failed with HTTP " + conn.getResponseCode());
                }

                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    return sb.toString();
                } finally {
                    conn.disconnect();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch patient feed: " + uri, e);
            }
        };
    }
}
