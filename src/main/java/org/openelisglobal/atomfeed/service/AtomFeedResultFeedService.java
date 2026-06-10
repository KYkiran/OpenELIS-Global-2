package org.openelisglobal.atomfeed.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openelisglobal.atomfeed.repository.EventRecordJdbc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds the result feed consumed by the Bahmni OpenMRS
 * openelis-atomfeed-client module (ict4h atomfeed-client + Rome parser). That
 * client imposes a strict contract: a {@code <link rel="self">} and
 * {@code <link rel="via">} on the feed (used for marker bookkeeping), entries
 * ordered oldest-first, and the event payload as inline CDATA content (a
 * {@code src} attribute is read as null by Rome/ict4h).
 */
@Service
public class AtomFeedResultFeedService {

    private static final int DEFAULT_PAGE_SIZE = 50;

    @Autowired
    private EventRecordJdbc eventRecordJdbc;

    @Value("${atomfeed.result.feedUrl:http://oe.openelis.org:8080/OpenELIS-Global/ws/feed/result/recent}")
    private String feedUrl;

    public String buildRecentFeedXml() {
        List<Map<String, Object>> events = eventRecordJdbc.getRecentByCategory(ResultFeedPublisher.CATEGORY,
                DEFAULT_PAGE_SIZE);
        // query returns newest-first; the ict4h client processes entries in
        // document order, so emit oldest-first
        Collections.reverse(events);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\">");
        xml.append("<title>").append(ResultFeedPublisher.TITLE).append("</title>");
        xml.append("<id>").append(escape(feedUrl)).append("</id>");
        xml.append("<updated>").append(Instant.now()).append("</updated>");
        xml.append("<link rel=\"self\" type=\"application/atom+xml\" href=\"").append(escape(feedUrl)).append("\"/>");
        xml.append("<link rel=\"via\" type=\"application/atom+xml\" href=\"").append(escape(feedUrl)).append("\"/>");

        for (Map<String, Object> event : events) {
            xml.append("<entry>");
            xml.append("<title>").append(escape(String.valueOf(event.get("title")))).append("</title>");
            xml.append("<id>urn:uuid:").append(escape(String.valueOf(event.get("uuid")))).append("</id>");
            xml.append("<updated>").append(toIsoInstant(event.get("date_created"))).append("</updated>");
            xml.append("<content type=\"application/vnd.atomfeed+xml\"><![CDATA[")
                    .append(String.valueOf(event.get("object"))).append("]]></content>");
            xml.append("</entry>");
        }

        xml.append("</feed>");
        return xml.toString();
    }

    private String toIsoInstant(Object dateCreated) {
        if (dateCreated instanceof Timestamp) {
            return ((Timestamp) dateCreated).toInstant().toString();
        }
        return Instant.now().toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
