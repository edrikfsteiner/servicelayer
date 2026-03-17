package com.migration.servicelayer.repository;
import com.migration.servicelayer.model.target.UserNovo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserNovoRepository extends JpaRepository<UserNovo, UUID> {}