package com.example.demo.worker.repository;

import com.example.demo.worker.entity.LlmShadowMismatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmShadowMismatchRepository extends JpaRepository<LlmShadowMismatchEntity, Long> {

    boolean existsByShadowTaskId(String shadowTaskId);
}
