package org.openelisglobal.atomfeed.config;

import javax.sql.DataSource;
import org.ict4h.atomfeed.client.repository.AllFailedEvents;
import org.ict4h.atomfeed.client.repository.AllMarkers;
import org.ict4h.atomfeed.transaction.AFTransactionManager;
import org.openelisglobal.atomfeed.repository.AllFailedEventsJdbc;
import org.openelisglobal.atomfeed.repository.AllMarkersJdbc;
import org.openelisglobal.atomfeed.transaction.SpringAFTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableScheduling
public class AtomFeedConfig {

    @Bean
    public JdbcTemplate atomFeedJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public AllMarkers allMarkers(JdbcTemplate atomFeedJdbcTemplate) {
        return new AllMarkersJdbc(atomFeedJdbcTemplate);
    }

    @Bean
    public AllFailedEvents allFailedEvents(JdbcTemplate atomFeedJdbcTemplate) {
        return new AllFailedEventsJdbc(atomFeedJdbcTemplate);
    }

    @Bean
    public AFTransactionManager afTransactionManager(PlatformTransactionManager txManager) {
        return new SpringAFTransactionManager(txManager);
    }
}