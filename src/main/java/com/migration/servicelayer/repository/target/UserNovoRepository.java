package com.migration.servicelayer.repository.target;
import com.migration.servicelayer.model.target.UserNovo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserNovoRepository extends JpaRepository<UserNovo, UUID> {}