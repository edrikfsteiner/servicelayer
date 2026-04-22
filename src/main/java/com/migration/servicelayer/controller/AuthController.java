package com.migration.servicelayer.controller;

import com.migration.servicelayer.dto.AuthRequest;
import com.migration.servicelayer.model.ApiClient;
import com.migration.servicelayer.repository.ApiClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ApiClientRepository apiClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    @Value("${app.security.jwt.expiration-time}")
    private int jwtExpiration;

    @Value("${app.security.jwt.issuer}")
    private String jwtIssuer;

    @PostMapping
    public ResponseEntity<Map<String, String>> authenticate(@RequestBody AuthRequest request) {
        ApiClient client = apiClientRepository.findByClientId(request.clientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        if (!passwordEncoder.matches(request.clientSecret(), client.getClientSecret())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(jwtExpiration))
                .subject(client.getClientId())
                .claim("tenantId", client.getTenantId())
                .build();

        JwtEncoderParameters parameters = JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);
        String token = jwtEncoder.encode(parameters).getTokenValue();

        return ResponseEntity.ok(Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "expires_in", String.valueOf(jwtExpiration)
        ));
    }
}