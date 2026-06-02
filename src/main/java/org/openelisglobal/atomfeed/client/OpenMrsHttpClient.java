package org.openelisglobal.atomfeed.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenMrsHttpClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMrsHttpClient.class);

    @Value("${atomfeed.openmrs.baseUrl}")
    private String openmrsBaseUrl;

    @Value("${atomfeed.openmrs.username}")
    private String openmrsUsername;

    @Value("${atomfeed.openmrs.password}")
    private String openmrsPassword;

    @Value("${atomfeed.openmrs.connectTimeoutMs:10000}")
    private int connectTimeoutMs;

    @Value("${atomfeed.openmrs.readTimeoutMs:30000}")
    private int readTimeoutMs;

    private HttpClient httpClient;

    @jakarta.annotation.PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    }

    public String fetchJson(String contentPath) throws IOException, InterruptedException {
        String url = contentPath.startsWith("http") ? contentPath : openmrsBaseUrl + contentPath;

        log.info("Fetching from OpenMRS: {}", url);

        String credentials = Base64.getEncoder()
                .encodeToString((openmrsUsername + ":" + openmrsPassword).getBytes());

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Basic " + credentials).header("Accept", "application/json").GET()
                .timeout(Duration.ofMillis(readTimeoutMs)).build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("OpenMRS response: HTTP {} for {}", response.statusCode(), url);

        if (response.statusCode() != 200) {
            log.error("OpenMRS returned HTTP {} body: {}", response.statusCode(),
                    response.body().substring(0, Math.min(300, response.body().length())));
            throw new OpenMrsHttpException(response.statusCode(), url);
        }

        return response.body();
    }

    public static class OpenMrsHttpException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int statusCode;
        private final String url;

        public OpenMrsHttpException(int statusCode, String url) {
            super("OpenMRS returned HTTP " + statusCode + " for " + url);
            this.statusCode = statusCode;
            this.url = url;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getUrl() {
            return url;
        }
    }
}
