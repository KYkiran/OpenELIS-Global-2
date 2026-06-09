package org.openelisglobal.atomfeed.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.openelisglobal.atomfeed.repository.EventRecordJdbc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AtomFeedResultFeedService {

    private static final int DEFAULT_PAGE_SIZE = 50;

    @Autowired
    private EventRecordJdbc eventRecordJdbc;

    public String buildRecentFeedXml() {
        List<Map<String, Object>> events = eventRecordJdbc.getRecentByCategory(ResultFeedPublisher.CATEGORY,
                DEFAULT_PAGE_SIZE);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\">");
        xml.append("<title>").append(ResultFeedPublisher.TITLE).append("</title>");
        xml.append("<id>result-feed</id>");
        xml.append("<updated>").append(Instant.now()).append("</updated>");

        for (Map<String, Object> event : events) {
            xml.append("<entry>");
            xml.append("<title>").append(escape(String.valueOf(event.get("title")))).append("</title>");
            xml.append("<id>urn:uuid:").append(escape(String.valueOf(event.get("uuid")))).append("</id>");
            xml.append("<updated>").append(event.get("date_created")).append("</updated>");
            xml.append("<content type=\"application/vnd.atomfeed+xml\" src=\"")
                    .append(escape(String.valueOf(event.get("object")))).append("\"/>");
            xml.append("</entry>");
        }

        xml.append("</feed>");
        return xml.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
