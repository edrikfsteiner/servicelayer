package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.AuthRequest;
import com.migration.servicelayer.dto.AuthResponse;
import com.migration.servicelayer.model.ApiClient;
import com.migration.servicelayer.repository.ApiClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RequiredArgsConstructor
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final ApiClientRepository apiClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    @Value("${app.security.jwt.expiration-time}")
    private int jwtExpiration;

    @Value("${app.security.jwt.issuer}")
    private String jwtIssuer;

    public void register(AuthRequest request) {
        if (apiClientRepository.findByClientId(request.clientId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "clientId ja existe");
        }

        ApiClient client = new ApiClient();
        client.setClientId(request.clientId());
        client.setClientSecret(passwordEncoder.encode(request.clientSecret()));
        client.setTenantId(request.tenantId());
        apiClientRepository.save(client);
    }

    public AuthResponse authenticate(AuthRequest request) {
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

        return new AuthResponse(token, TOKEN_TYPE, String.valueOf(jwtExpiration));
    }
}
