package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.IngestionProtocol;
import com.migration.servicelayer.model.ProtocolStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ProtocolRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<IngestionProtocol> ROW_MAPPER = (rs, _) -> {
        IngestionProtocol p = new IngestionProtocol();
        p.setId(rs.getString("id"));
        p.setTenantId(rs.getString("tenant_id"));
        p.setEventType(rs.getString("event_type"));
        p.setStatus(ProtocolStatus.valueOf(rs.getString("status")));
        p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return p;
    };

    public ProtocolRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS ingestion_protocol (
                    id VARCHAR(36) PRIMARY KEY,
                    tenant_id VARCHAR(255) NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    status VARCHAR(50) DEFAULT 'QUEUED',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
        """);
    }

    public void save(IngestionProtocol protocol) {
        jdbc.update("""
                INSERT INTO ingestion_protocol (id, tenant_id, event_type, status, created_at, updated_at)
                VALUES (:id, :tenantId, :eventType, :status, :createdAt, :updatedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", protocol.getId())
                        .addValue("tenantId", protocol.getTenantId())
                        .addValue("eventType", protocol.getEventType())
                        .addValue("status", protocol.getStatus().name())
                        .addValue("createdAt", protocol.getCreatedAt())
                        .addValue("updatedAt", protocol.getUpdatedAt())
        );
    }

    public Optional<IngestionProtocol> findById(String id) {
        var result = jdbc.query(
                "SELECT * FROM ingestion_protocol WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    public void updateStatus(String protocolId, ProtocolStatus status) {
        jdbc.update("""
                UPDATE ingestion_protocol
                SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", protocolId)
                        .addValue("status", status.name())
        );
    }
}
