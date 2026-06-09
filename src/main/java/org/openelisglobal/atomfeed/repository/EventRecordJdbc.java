package org.openelisglobal.atomfeed.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRecordJdbc {

    private static final String SCHEMA = "clinlims.";
    private final JdbcTemplate jdbcTemplate;

    public EventRecordJdbc(@Qualifier("atomFeedJdbcTemplate") JdbcTemplate atomFeedJdbcTemplate) {
        this.jdbcTemplate = atomFeedJdbcTemplate;
    }

    public void insert(String title, String category, String objectUrl, String tags) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO " + SCHEMA
                        + "event_records (uuid, title, timestamp, uri, object, category, date_created, tags) "
                        + "VALUES (?, ?, ?, NULL, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), title, now, objectUrl, category, now, tags);
    }

    public List<Map<String, Object>> getRecentByCategory(String category, int limit) {
        return jdbcTemplate.queryForList("SELECT uuid, title, object, category, date_created FROM " + SCHEMA
                + "event_records " + "WHERE category = ? ORDER BY date_created DESC, id DESC LIMIT ?", category, limit);
    }
}
