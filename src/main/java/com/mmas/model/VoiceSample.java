package com.mmas.model;

import jakarta.persistence.*;
import lombok.Data;
import com.mmas.util.FloatArrayConverter;

@Entity
@Table(name = "voice_samples")
@Data
public class VoiceSample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filepath;

    @Column(nullable = false)
    private String type;

    @Column(name = "speaker_vector", columnDefinition = "TEXT", nullable = false)
    @Convert(converter = FloatArrayConverter.class)
    private float[] speakerVector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
