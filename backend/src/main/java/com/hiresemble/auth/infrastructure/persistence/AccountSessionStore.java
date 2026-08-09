package com.hiresemble.auth.infrastructure.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountSessionStore {

    private final JdbcClient jdbc;

    public AccountSessionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteOtherSessions(UUID userId, String currentSessionId) {
        jdbc.sql("""
                        DELETE FROM spring_session
                        WHERE principal_name=:principalName AND session_id<>:currentSessionId
                        """)
                .param("principalName", userId.toString())
                .param("currentSessionId", currentSessionId)
                .update();
    }

    public void deleteAllSessions(UUID userId) {
        jdbc.sql("DELETE FROM spring_session WHERE principal_name=:principalName")
                .param("principalName", userId.toString())
                .update();
    }
}
