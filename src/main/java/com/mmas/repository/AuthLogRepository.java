package com.mmas.repository;

import com.mmas.model.AuthLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthLogRepository extends JpaRepository<AuthLog, Long> {
    
    @Query("SELECT AVG(a.durationMs) FROM AuthLog a WHERE a.success = true")
    Double getAverageSuccessDuration();

    @Query("SELECT COUNT(a) FROM AuthLog a WHERE a.success = true")
    long countSuccessfulAttempts();

    @Query("SELECT COUNT(a) FROM AuthLog a")
    long countTotalAttempts();

    List<AuthLog> findFirst10ByOrderByTimestampDesc();
}
