package com.migration.servicelayer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "api_clients")
public class ApiClient {
    @Id
    private String id;

    private String clientId;
    private String clientSecret;
    private String tenantId;
}