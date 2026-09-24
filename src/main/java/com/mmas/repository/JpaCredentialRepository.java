package com.mmas.repository;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import com.mmas.model.User;
import com.mmas.model.WebAuthnCredential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JpaCredentialRepository implements CredentialRepository {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebAuthnCredentialRepository credentialRepository;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepository.findByUsername(username)
            .map(user -> credentialRepository.findByUser(user).stream()
                .map(cred -> PublicKeyCredentialDescriptor.builder()
                    .id(new ByteArray(cred.getCredentialId()))
                    .build())
                .collect(Collectors.toSet()))
            .orElse(Collections.emptySet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByUsername(username)
            .map(user -> new ByteArray(user.getId().toString().getBytes()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        try {
            UUID userId = UUID.fromString(new String(userHandle.getBytes()));
            return userRepository.findById(userId).map(User::getUsername);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentialRepository.findByCredentialId(credentialId.getBytes())
            .map(cred -> RegisteredCredential.builder()
                .credentialId(credentialId)
                .userHandle(userHandle)
                .publicKeyCose(new ByteArray(cred.getPublicKey()))
                .signatureCount(cred.getSignatureCount())
                .build());
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentialRepository.findByCredentialId(credentialId.getBytes()).stream()
            .map(cred -> RegisteredCredential.builder()
                .credentialId(credentialId)
                .userHandle(new ByteArray(cred.getUser().getId().toString().getBytes()))
                .publicKeyCose(new ByteArray(cred.getPublicKey()))
                .signatureCount(cred.getSignatureCount())
                .build())
            .collect(Collectors.toSet());
    }
}
