package com.mmas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.SpeakerModel;
import org.vosk.Recognizer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

@Service
public class VoskSpeakerService {

    @Value("${vosk.model.path}")
    private String modelPath;

    @Value("${vosk.spk.model.path}")
    private String spkModelPath;

    @Autowired
    private ResourceLoader resourceLoader;

    private Model model;
    private SpeakerModel spkModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws Exception {
        String resolvedModelPath = resolvePath(modelPath);
        String resolvedSpkPath = resolvePath(spkModelPath);

        this.model = new Model(resolvedModelPath);
        this.spkModel = new SpeakerModel(resolvedSpkPath);
    }

    private String resolvePath(String rawPath) {
        try {
            Resource resource = resourceLoader.getResource(rawPath);
            return resource.getFile().getAbsolutePath();
        } catch (Exception e) {
            // Fallback: check working directory directly
            String cleaned = rawPath;
            if (cleaned.startsWith("classpath:")) {
                cleaned = cleaned.substring("classpath:".length());
            }
            File f = new File(cleaned);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
            throw new RuntimeException("VOSK Error: Model directory not found. " +
                    "Ensure " + rawPath + " or local directory " + f.getAbsolutePath() + " exists.", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (model != null) model.close();
        if (spkModel != null) spkModel.close();
    }

    /**
     * Extracts 128-float speaker vector (embedding/x-vector) from a 16kHz mono WAV file.
     */
    public float[] extractSpeakerVector(File wavFile) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(wavFile)))) {
            float sampleRate = ais.getFormat().getSampleRate();
            
            try (Recognizer recognizer = new Recognizer(model, sampleRate, spkModel)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = ais.read(buffer)) != -1) {
                    recognizer.acceptWaveForm(buffer, bytesRead);
                }

                String resultJson = recognizer.getFinalResult();
                return parseSpeakerVector(resultJson);
            }
        }
    }

    private float[] parseSpeakerVector(String jsonResult) throws Exception {
        JsonNode node = objectMapper.readTree(jsonResult);
        if (node.has("spk")) {
            JsonNode spkNode = node.get("spk");
            float[] vector = new float[spkNode.size()];
            for (int i = 0; i < spkNode.size(); i++) {
                vector[i] = (float) spkNode.get(i).asDouble();
            }
            return vector;
        }
        throw new IllegalArgumentException("VOSK: No speaker signature identified in audio sample. Please speak clearly.");
    }

    /**
     * Computes Cosine Similarity between two voice embedding vectors.
     * Formula: (A . B) / (||A|| * ||B||)
     */
    public double calculateSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length || vectorA.length == 0) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
