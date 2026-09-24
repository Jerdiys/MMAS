package com.mmas.test;

import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.ByteArray;
import java.util.Collections;

public class TestYubico {
    public static void main(String[] args) throws Exception {
        RelyingParty rp = RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id("localhost").name("Test").build())
                .credentialRepository(new com.yubico.webauthn.CredentialRepository() {
                    public java.util.Set<com.yubico.webauthn.data.PublicKeyCredentialDescriptor> getCredentialIdsForUsername(
                            String username) {
                        return Collections.emptySet();
                    }

                    public java.util.Optional<com.yubico.webauthn.data.ByteArray> getUserHandleForUsername(
                            String username) {
                        return java.util.Optional.empty();
                    }

                    public java.util.Optional<String> getUsernameForUserHandle(
                            com.yubico.webauthn.data.ByteArray userHandle) {
                        return java.util.Optional.empty();
                    }

                    public java.util.Optional<com.yubico.webauthn.RegisteredCredential> lookup(
                            com.yubico.webauthn.data.ByteArray credentialId,
                            com.yubico.webauthn.data.ByteArray userHandle) {
                        return java.util.Optional.empty();
                    }

                    public java.util.Set<com.yubico.webauthn.RegisteredCredential> lookupAll(
                            com.yubico.webauthn.data.ByteArray credentialId) {
                        return Collections.emptySet();
                    }
                })
                .origins(Collections.singleton("http://localhost:8080"))
                .build();
        PublicKeyCredentialCreationOptions options = rp.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name("test")
                                .displayName("test")
                                .id(new ByteArray(new byte[] { 1, 2, 3 }))
                                .build())
                        .build());
        System.out.println("JSON_START::" + options.toCredentialsCreateJson() + "::JSON_END");
    }
}
