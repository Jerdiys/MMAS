package com.mmas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mmas.model.VoiceSample;

@Repository
public interface VoiceSampleRepo extends JpaRepository<VoiceSample, Long> {
}
