package com.migration.servicelayer.repository;

import com.migration.servicelayer.model.MigrationProtocol;
import com.migration.servicelayer.model.ProtocolStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ProtocolRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<MigrationProtocol> ROW_MAPPER = (rs, _) -> {
        MigrationProtocol p = new MigrationProtocol();
        p.setId(rs.getString("id"));
        p.setOriginTable(rs.getString("origin_table"));
        p.setTargetTable(rs.getString("target_table"));
        p.setTotalRecords(rs.getLong("total_records"));
        p.setProcessedRecords(rs.getLong("processed_records"));
        p.setFailedRecords(rs.getLong("failed_records"));
        p.setStatus(ProtocolStatus.valueOf(rs.getString("status")));
        p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return p;
    };

    public ProtocolRepository(@Qualifier("targetJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS migration_protocol (
                    id VARCHAR(36) PRIMARY KEY,
                    origin_table VARCHAR(255) NOT NULL,
                    target_table VARCHAR(255) NOT NULL,
                    total_records BIGINT DEFAULT 0,
                    processed_records BIGINT DEFAULT 0,
                    failed_records BIGINT DEFAULT 0,
                    status VARCHAR(50) DEFAULT 'PENDING',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
        """);
    }

    public void save(MigrationProtocol protocol) {
        jdbc.update("""
                INSERT INTO migration_protocol (id, origin_table, target_table, total_records, status, created_at, updated_at)
                VALUES (:id, :originTable, :targetTable, :totalRecords, :status, :createdAt, :updatedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", protocol.getId())
                        .addValue("originTable", protocol.getOriginTable())
                        .addValue("targetTable", protocol.getTargetTable())
                        .addValue("totalRecords", protocol.getTotalRecords())
                        .addValue("status", protocol.getStatus().name())
                        .addValue("createdAt", protocol.getCreatedAt())
                        .addValue("updatedAt", protocol.getUpdatedAt())
        );
    }

    public Optional<MigrationProtocol> findById(String id) {
        var result = jdbc.query(
                "SELECT * FROM migration_protocol WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    public void incrementProcessed(String protocolId) {
        jdbc.update("""
                UPDATE migration_protocol
                SET processed_records = processed_records + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """,
                new MapSqlParameterSource("id", protocolId)
        );
    }

    public void incrementFailed(String protocolId) {
        jdbc.update("""
                UPDATE migration_protocol
                SET failed_records = failed_records + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """,
                new MapSqlParameterSource("id", protocolId)
        );
    }

    public void updateStatus(String protocolId, ProtocolStatus status) {
        jdbc.update("""
                UPDATE migration_protocol
                SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", protocolId)
                        .addValue("status", status.name())
        );
    }
}
