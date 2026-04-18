package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.SilverFieldRule;
import com.migration.servicelayer.model.SilverMappingRules;
import com.migration.servicelayer.repository.SilverMappingRulesRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SilverTransformationScheduler}.
 *
 * All external dependencies (MongoTemplate, repository) are mocked so no
 * running infrastructure is required.
 */
@ExtendWith(MockitoExtension.class)
class SilverTransformationSchedulerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SilverMappingRulesRepository silverMappingRulesRepository;

    private SilverTransformationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SilverTransformationScheduler(mongoTemplate, silverMappingRulesRepository);
    }

    // -------------------------------------------------------------------------
    // processAllTenants — integration of the full orchestration flow
    // -------------------------------------------------------------------------

    @Test
    void processAllTenants_transformsRecordsAndPersistsToSilverCollection() {
        SilverMappingRules rules = buildRules("tenant1",
                new SilverFieldRule("customer_name", "STRING",  true,  false),
                new SilverFieldRule("age",           "INTEGER", false, false),
                new SilverFieldRule("score",         "DOUBLE",  false, true));

        when(silverMappingRulesRepository.findAll()).thenReturn(List.of(rules));
        when(mongoTemplate.count(any(Query.class), eq("bronze_tenant1"))).thenReturn(1L);

        Document payload = new Document("customer_name", "  Alice  ")
                .append("age", "28")
                .append("score", "9.5");
        Document bronze = bronzeDoc("tenant1", "order_placed", payload);

        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("bronze_tenant1")))
                .thenReturn(List.of(bronze));

        scheduler.processAllTenants();

        // ── Assert silver insert ──────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Document>> silverCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mongoTemplate).insert(silverCaptor.capture(), eq("silver_tenant1"));

        Document silver = silverCaptor.getValue().iterator().next();
        assertThat(silver.getString("customer_name")).isEqualTo("Alice");      // trimmed
        assertThat(silver.get("age")).isEqualTo(28);                           // Integer
        assertThat(silver.get("score")).isEqualTo(9.5);                        // Double
        assertThat(silver.getString("tenantId")).isEqualTo("tenant1");

        // ── Assert bronze mark-as-processed ──────────────────────────────────
        verify(mongoTemplate).updateMulti(
                any(Query.class), any(Update.class), eq("bronze_tenant1"));
    }

    @Test
    void processAllTenants_skipsTenantsWithNoUnprocessedRecords() {
        SilverMappingRules rules = buildRules("tenant-empty");
        when(silverMappingRulesRepository.findAll()).thenReturn(List.of(rules));
        when(mongoTemplate.count(any(Query.class), eq("bronze_tenant_empty"))).thenReturn(0L);

        scheduler.processAllTenants();

        verify(mongoTemplate, never()).find(any(Query.class), eq(Document.class), anyString());
        verify(mongoTemplate, never()).insert(anyCollection(), anyString());
    }

    @Test
    void processAllTenants_continuesOtherTenantsWhenOneFails() {
        SilverMappingRules failing = buildRules("bad-tenant");
        SilverMappingRules good    = buildRules("good-tenant",
                new SilverFieldRule("value", "STRING", false, true));

        when(silverMappingRulesRepository.findAll()).thenReturn(List.of(failing, good));

        // First tenant explodes; second returns 1 unprocessed record.
        when(mongoTemplate.count(any(Query.class), eq("bronze_bad_tenant")))
                .thenThrow(new RuntimeException("Mongo unreachable"));
        when(mongoTemplate.count(any(Query.class), eq("bronze_good_tenant"))).thenReturn(1L);

        Document bronze = bronzeDoc("good-tenant", "evt",
                new Document("value", "hello"));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("bronze_good_tenant")))
                .thenReturn(List.of(bronze));

        scheduler.processAllTenants();   // must not throw

        verify(mongoTemplate).insert(anyCollection(), eq("silver_good_tenant"));
    }

    // -------------------------------------------------------------------------
    // toSilver — transformation unit tests
    // -------------------------------------------------------------------------

    @Test
    void toSilver_handlesNullableFieldGracefully() {
        SilverMappingRules rules = buildRules("t1",
                new SilverFieldRule("optional_field", "STRING", false, true));

        Document payload = new Document(); // field absent
        Document bronze  = bronzeDoc("t1", "evt", payload);

        Document silver = scheduler.toSilver(bronze, rules.getFields());

        assertThat(silver.get("optional_field")).isNull();
    }

    @Test
    void toSilver_usesDefaultValueForNonNullableWhenFieldAbsent() {
        SilverMappingRules rules = buildRules("t1",
                new SilverFieldRule("count", "INTEGER", false, false));

        Document payload = new Document(); // field absent
        Document bronze  = bronzeDoc("t1", "evt", payload);

        Document silver = scheduler.toSilver(bronze, rules.getFields());

        assertThat(silver.get("count")).isEqualTo(0);
    }

    @Test
    void toSilver_fallsBackToDefaultOnCoercionError() {
        SilverMappingRules rules = buildRules("t1",
                new SilverFieldRule("price", "DOUBLE", false, false));

        Document payload = new Document("price", "not-a-number");
        Document bronze  = bronzeDoc("t1", "evt", payload);

        Document silver = scheduler.toSilver(bronze, rules.getFields());

        // Must not throw — fallback to default DOUBLE value
        assertThat(silver.get("price")).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // coerce — type conversion matrix
    // -------------------------------------------------------------------------

    @Test
    void coerce_convertsAllSupportedTypes() {
        assertThat(scheduler.coerce("hello", new SilverFieldRule("f", "STRING",  false, false))).isEqualTo("hello");
        assertThat(scheduler.coerce("42",    new SilverFieldRule("f", "INTEGER", false, false))).isEqualTo(42);
        assertThat(scheduler.coerce("3.14",  new SilverFieldRule("f", "DOUBLE",  false, false))).isEqualTo(3.14);
        assertThat(scheduler.coerce("2024-01-15", new SilverFieldRule("f", "DATE", false, false)))
                .isEqualTo("2024-01-15");
    }

    @Test
    void coerce_trimIsAppliedBeforeConversion() {
        Object result = scheduler.coerce("  42  ", new SilverFieldRule("f", "INTEGER", true, false));
        assertThat(result).isEqualTo(42);
    }

    // -------------------------------------------------------------------------
    // Collection name sanitisation
    // -------------------------------------------------------------------------

    @Test
    void collectionNames_sanitiseHyphensInTenantId() {
        assertThat(SilverTransformationScheduler.bronzeCollection("empresa-teste"))
                .isEqualTo("bronze_empresa_teste");
        assertThat(SilverTransformationScheduler.silverCollection("empresa-teste"))
                .isEqualTo("silver_empresa_teste");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static SilverMappingRules buildRules(String tenantId, SilverFieldRule... rules) {
        SilverMappingRules r = new SilverMappingRules();
        r.setTenantId(tenantId);
        r.setEventType("test_event");
        r.setFields(List.of(rules));
        return r;
    }

    private static Document bronzeDoc(String tenantId, String eventType, Document payload) {
        return new Document("_id", new ObjectId())
                .append("tenantId", tenantId)
                .append("eventType", eventType)
                .append("_processed", false)
                .append("payload", payload);
    }
}
