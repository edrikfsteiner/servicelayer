package com.migration.servicelayer.config;

import com.migration.servicelayer.model.SilverDocument;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

@Configuration
public class MongoIndexConfig {

    @Bean
    public ApplicationRunner ensureSilverIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            Index index = new Index()
                    .on("tenantId", Sort.Direction.ASC)
                    .on("eventType", Sort.Direction.ASC)
                    .on("primaryKeyHash", Sort.Direction.ASC)
                    .unique()
                    .partial(PartialIndexFilter.of(Criteria.where("primaryKeyHash").type(2)))
                    .named("silver_tenant_event_primary_key_hash_unique");

            mongoTemplate.indexOps(SilverDocument.class).createIndex(index);
        };
    }
}
