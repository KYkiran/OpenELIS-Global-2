package org.openelisglobal.atomfeed.consumer;

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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;

@Component
public class EncounterFeedConsumer {

    private static final Logger log = LoggerFactory.getLogger(EncounterFeedConsumer.class);

    @Value("${atomfeed.enabled:false}")
    private boolean atomFeedEnabled;

    @Value("${atomfeed.openmrs.encounterFeedUrl}")
    private String encounterFeedUrl;

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
    private OrderEventWorker orderEventWorker;

    @Scheduled(fixedDelayString = "${atomfeed.pollIntervalMs:30000}")
    public void pollEncounterFeed() {
        if (!atomFeedEnabled) {
            log.trace("AtomFeed polling disabled (atomfeed.enabled=false)");
            return;
        }

        log.debug("Polling OpenMRS encounter feed: {}", encounterFeedUrl);
        try {
            HttpClient authHttpClient = (uri, properties, cookies) -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/atom+xml");
                    conn.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            (openmrsUsername + ":" + openmrsPassword).getBytes()));
                    conn.setConnectTimeout(properties.getConnectTimeout());
                    conn.setReadTimeout(properties.getReadTimeout());
                    conn.connect();

                    if (conn.getResponseCode() != 200) {
                        throw new RuntimeException("Feed request failed with HTTP " + conn.getResponseCode());
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
                    throw new RuntimeException("Failed to fetch feed: " + uri, e);
                }
            };

            AtomFeedClient feedClient = new AtomFeedClient(new AllFeeds(authHttpClient), allMarkers, allFailedEvents,
                    new AtomFeedProperties(), afTransactionManager, URI.create(encounterFeedUrl), orderEventWorker);
            feedClient.processEvents();
            log.debug("Encounter feed poll complete");
        } catch (Exception e) {
            log.error("Error polling encounter feed: {}", e.getMessage(), e);
        }
    }
}
