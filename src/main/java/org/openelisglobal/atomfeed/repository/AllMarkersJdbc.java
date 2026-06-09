package org.openelisglobal.atomfeed.repository;

import java.net.URI;
import java.util.List;
import org.ict4h.atomfeed.client.domain.Marker;
import org.ict4h.atomfeed.client.repository.AllMarkers;
import org.springframework.jdbc.core.JdbcTemplate;

public class AllMarkersJdbc implements AllMarkers {

    private static final String SCHEMA = "clinlims.";
    private final JdbcTemplate jdbcTemplate;

    public AllMarkersJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Marker get(URI feedUri) {
        String sql = "SELECT feed_uri, last_read_entry_id, feed_uri_for_last_read_entry " + "FROM " + SCHEMA
                + "event_records_offset_marker WHERE feed_uri = ?";
        List<Marker> markers = jdbcTemplate.query(sql,
                (rs, rowNum) -> new Marker(URI.create(rs.getString("feed_uri")), rs.getString("last_read_entry_id"),
                        rs.getString("feed_uri_for_last_read_entry") != null
                                ? URI.create(rs.getString("feed_uri_for_last_read_entry"))
                                : null),
                feedUri.toString());
        return markers.isEmpty() ? null : markers.get(0);
    }

    @Override
    public void put(URI feedUri, String entryId, URI entryFeedUri) {
        int updated = jdbcTemplate.update(
                "UPDATE " + SCHEMA + "event_records_offset_marker "
                        + "SET last_read_entry_id = ?, feed_uri_for_last_read_entry = ? WHERE feed_uri = ?",
                entryId, entryFeedUri != null ? entryFeedUri.toString() : null, feedUri.toString());
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO " + SCHEMA + "event_records_offset_marker "
                            + "(feed_uri, last_read_entry_id, feed_uri_for_last_read_entry) VALUES (?, ?, ?)",
                    feedUri.toString(), entryId, entryFeedUri != null ? entryFeedUri.toString() : null);
        }
    }

    @Override
    public List<Marker> getMarkerList() {
        return jdbcTemplate.query(
                "SELECT feed_uri, last_read_entry_id, feed_uri_for_last_read_entry " + "FROM " + SCHEMA
                        + "event_records_offset_marker",
                (rs, rowNum) -> new Marker(URI.create(rs.getString("feed_uri")), rs.getString("last_read_entry_id"),
                        rs.getString("feed_uri_for_last_read_entry") != null
                                ? URI.create(rs.getString("feed_uri_for_last_read_entry"))
                                : null));
    }
}