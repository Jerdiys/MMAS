package com.mmas.controller;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.mmas.model.*;
import com.mmas.repository.*;
import com.mmas.service.VoskSpeakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationApiController {

    private final RelyingParty relyingParty;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final VoiceSampleRepo voiceSampleRepository;
    private final AuthLogRepository authLogRepository;
    private final VoskSpeakerService voskSpeakerService;

    @Value("${voice.storage.path}")
    private String voiceUploadDir;

    private static final String REG_OPTIONS_KEY = "reg-options-";
    private static final String AUTH_REQUEST_KEY = "auth-request-";

    /* ========================================================================= */
    /* REGISTRATION FLOW */
    /* ========================================================================= */

    @PostMapping("/register/options")
    public ResponseEntity<?> getRegisterOptions(@RequestParam String username, HttpSession session) {
        try {
            Optional<User> existingUser = userRepository.findByUsername(username);
            User user = existingUser.orElseGet(() -> {
                User u = new User();
                u.setUsername(username);
                return userRepository.save(u);
            });

            PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                    StartRegistrationOptions.builder()
                            .user(UserIdentity.builder()
                                    .name(username)
                                    .displayName(username)
                                    .id(new ByteArray(user.getId().toString().getBytes()))
                                    .build())
                            .build());

            session.setAttribute(REG_OPTIONS_KEY + username, options);
            return ResponseEntity.ok(new ObjectMapper().readTree(options.toCredentialsCreateJson()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register/finish")
    public ResponseEntity<?> finishRegistration(
            @RequestParam String username,
            @RequestParam String credentialJson,
            @RequestParam("voice") MultipartFile voiceFile,
            HttpSession session) {
        try {
            // 1. Fetch Options
            PublicKeyCredentialCreationOptions options = (PublicKeyCredentialCreationOptions) session
                    .getAttribute(REG_OPTIONS_KEY + username);
            if (options == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Registration session expired."));
            }
            session.removeAttribute(REG_OPTIONS_KEY + username);

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User context not found."));

            // 2. Verify WebAuthn attestation
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc = PublicKeyCredential
                    .parseRegistrationResponseJson(credentialJson);

            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(pkc)
                            .build());

            // 3. Process voice WAV sample
            File tempFile = File.createTempFile("reg_voice_", ".wav");
            voiceFile.transferTo(tempFile);

            float[] speakerVector = voskSpeakerService.extractSpeakerVector(tempFile);

            // Store permanent audio WAV
            File uploadFolder = new File(cleanUploadDir(voiceUploadDir));
            if (!uploadFolder.exists())
                uploadFolder.mkdirs();
            File destFile = new File(uploadFolder, user.getId() + "_reg.wav");
            tempFile.renameTo(destFile);

            // 4. Save WebAuthn credential & Voice metadata to Database
            WebAuthnCredential credential = new WebAuthnCredential();
            credential.setCredentialId(result.getKeyId().getId().getBytes());
            credential.setPublicKey(result.getPublicKeyCose().getBytes());
            credential.setSignatureCount(result.getSignatureCount());
            credential.setUser(user);
            credentialRepository.save(credential);

            VoiceSample sample = new VoiceSample();
            sample.setFilepath(destFile.getAbsolutePath());
            sample.setType("REGISTRATION");
            sample.setSpeakerVector(speakerVector);
            sample.setUser(user);
            voiceSampleRepository.save(sample);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* ========================================================================= */
    /* LOGIN FLOW (MULTI-MODAL VERIFICATION) */
    /* ========================================================================= */

    @PostMapping("/login/options")
    public ResponseEntity<?> getLoginOptions(@RequestParam String username, HttpSession session) {
        try {
            AssertionRequest assertionRequest = relyingParty.startAssertion(
                    StartAssertionOptions.builder()
                            .username(Optional.of(username))
                            .build());
            session.setAttribute(AUTH_REQUEST_KEY + username, assertionRequest);
            return ResponseEntity.ok(new ObjectMapper().readTree(assertionRequest.toCredentialsGetJson()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No credentials registered for username"));
        }
    }

    @PostMapping("/login/finish")
    public ResponseEntity<?> finishLogin(
            @RequestParam String username,
            @RequestParam String credentialJson,
            @RequestParam("voice") MultipartFile voiceFile,
            HttpSession session) {

        long startTime = System.currentTimeMillis();
        User user = userRepository.findByUsername(username).orElse(null);

        try {
            // 1. Verify user context
            if (user == null) {
                throw new IllegalArgumentException("Username not registered.");
            }

            // 2. Fetch Options
            AssertionRequest assertionRequest = (AssertionRequest) session.getAttribute(AUTH_REQUEST_KEY + username);
            if (assertionRequest == null) {
                throw new IllegalStateException("Authentication session expired.");
            }
            session.removeAttribute(AUTH_REQUEST_KEY + username);

            // 3. FACTOR 1: WebAuthn verification
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc = PublicKeyCredential
                    .parseAssertionResponseJson(credentialJson);

            AssertionResult webauthnResult = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(assertionRequest)
                            .response(pkc)
                            .build());

            if (!webauthnResult.isSuccess()) {
                throw new SecurityException("WebAuthn verification failed.");
            }

            // Update signature count in DB
            WebAuthnCredential credential = credentialRepository
                    .findByCredentialId(webauthnResult.getCredentialId().getBytes())
                    .orElseThrow(() -> new SecurityException("Matched credential record missing."));
            credential.setSignatureCount(webauthnResult.getSignatureCount());
            credentialRepository.save(credential);

            // 4. FACTOR 2: Voice Verification (VOSK Speaker Recognition)
            File tempFile = File.createTempFile("auth_voice_", ".wav");
            voiceFile.transferTo(tempFile);

            float[] currentVector = voskSpeakerService.extractSpeakerVector(tempFile);
            tempFile.delete(); // Cleanup temp audio

            // Retrieve registration reference vector
            VoiceSample referenceSample = user.getVoiceSamples().stream()
                    .filter(s -> "REGISTRATION".equals(s.getType()))
                    .findFirst()
                    .orElseThrow(() -> new SecurityException("Vocal reference print not found."));

            double similarity = voskSpeakerService.calculateSimilarity(currentVector,
                    referenceSample.getSpeakerVector());
            double threshold = 0.80; // Cosine similarity threshold for verification

            if (similarity < threshold) {
                throw new SecurityException(String.format(
                        "Voice verification failed. Match score: %.2f (required >= %.2f)", similarity, threshold));
            }

            // Log successful attempt
            logAuth(username, true, System.currentTimeMillis() - startTime, null);

            // Log user in custom session context
            session.setAttribute("user", username);
            return ResponseEntity.ok(Map.of("success", true, "similarity", similarity));

        } catch (Exception e) {
            logAuth(username, false, System.currentTimeMillis() - startTime, e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    private void logAuth(String username, boolean success, long duration, String reason) {
        AuthLog log = new AuthLog();
        log.setUsername(username);
        log.setSuccess(success);
        log.setDurationMs(duration);
        log.setTimestamp(LocalDateTime.now());
        log.setFailureReason(reason);
        authLogRepository.save(log);
    }

    private String cleanUploadDir(String path) {
        if (path.startsWith("classpath:")) {
            return "voice_uploads";
        }
        return path;
    }
}
