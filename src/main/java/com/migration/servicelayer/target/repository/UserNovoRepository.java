package com.migration.servicelayer.target.repository;
import com.migration.servicelayer.target.model.UserNovo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserNovoRepository extends JpaRepository<UserNovo, UUID> {}