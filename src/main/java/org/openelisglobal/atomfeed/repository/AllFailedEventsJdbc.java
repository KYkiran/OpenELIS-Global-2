package org.openelisglobal.atomfeed.repository;

import java.util.List;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.domain.FailedEvent;
import org.ict4h.atomfeed.client.domain.FailedEventRetryLog;
import org.ict4h.atomfeed.client.repository.AllFailedEvents;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class AllFailedEventsJdbc implements AllFailedEvents {

    private static final String SCHEMA = "clinlims.";
    private final JdbcTemplate jdbcTemplate;

    public AllFailedEventsJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // FailedEvent wraps an Event — reconstruct from DB columns
    private final RowMapper<FailedEvent> mapper = (rs, rowNum) -> {
        Event event = new Event(rs.getString("event_id"), rs.getString("event_content"), rs.getString("title"));
        long failedAt = rs.getLong("failed_at");
        return new FailedEvent(rs.getString("feed_uri"), event, rs.getString("error_message"),
                failedAt != 0 ? failedAt : System.currentTimeMillis(), rs.getInt("retries"));
    };

    @Override
    public void addOrUpdate(FailedEvent event) {
        FailedEvent existing = get(event.getFeedUri(), event.getEventId());
        if (existing == null) {
            jdbcTemplate.update(
                    "INSERT INTO " + SCHEMA + "failed_events "
                            + "(feed_uri, event_id, event_content, title, error_message, retries, failed_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    event.getFeedUri(), event.getEventId(), event.getEvent().getContent(), event.getEvent().getTitle(),
                    event.getErrorMessage(), event.getRetries(), event.getFailedAt() // epoch millis → BIGINT
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE " + SCHEMA + "failed_events " + "SET retries = ?, error_message = ?, failed_at = ? "
                            + "WHERE feed_uri = ? AND event_id = ?",
                    event.getRetries(), event.getErrorMessage(), event.getFailedAt(), // epoch millis → BIGINT
                    event.getFeedUri(), event.getEventId());
        }
    }

    @Override
    public List<FailedEvent> getOldestNFailedEvents(String feedUri, int n, int maxRetry) {
        return jdbcTemplate.query(
                "SELECT * FROM " + SCHEMA + "failed_events "
                        + "WHERE feed_uri = ? AND retries < ? ORDER BY failed_at ASC LIMIT ?",
                mapper, feedUri, maxRetry, n);
    }

    @Override
    public int getNumberOfFailedEvents(String feedUri) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA + "failed_events WHERE feed_uri = ?", Integer.class, feedUri);
        return count != null ? count : 0;
    }

    @Override
    public void remove(FailedEvent event) {
        jdbcTemplate.update("DELETE FROM " + SCHEMA + "failed_events WHERE feed_uri = ? AND event_id = ?",
                event.getFeedUri(), event.getEventId());
    }

    @Override
    public FailedEvent get(String feedUri, String eventId) {
        List<FailedEvent> results = jdbcTemplate.query(
                "SELECT * FROM " + SCHEMA + "failed_events WHERE feed_uri = ? AND event_id = ? LIMIT 1", mapper,
                feedUri, eventId);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void insert(FailedEventRetryLog log) {
        // no-op — retry log detail not needed for basic operation
    }

    @Override
    public List<FailedEvent> getFailedEvents(String feedUri) {
        return jdbcTemplate.query("SELECT * FROM " + SCHEMA + "failed_events WHERE feed_uri = ?", mapper, feedUri);
    }

    @Override
    public FailedEvent getByEventId(String eventId) {
        List<FailedEvent> list = jdbcTemplate.query("SELECT * FROM " + SCHEMA + "failed_events WHERE event_id = ?",
                mapper, eventId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<FailedEventRetryLog> getFailedEventRetryLogs(String eventId) {
        return List.of();
    }
}