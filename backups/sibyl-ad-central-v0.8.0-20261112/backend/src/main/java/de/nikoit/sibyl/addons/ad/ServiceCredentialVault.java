package de.nikoit.sibyl.addons.ad;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** AES-256-GCM encrypted service-account credentials. Key is outside MySQL. */
@Service
public class ServiceCredentialVault {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final SecretKeySpec key;
    public ServiceCredentialVault(@Value("${SIBYL_CONFIG_KEY_BASE64}") String keyBase64) {
        byte[] bytes=Base64.getDecoder().decode(keyBase64);
        if(bytes.length!=32) throw new IllegalStateException("Configuration encryption key must be 32 bytes");
        key=new SecretKeySpec(bytes,"AES");
    }
    public String encrypt(String text) {
        try {
            byte[] nonce=new byte[12];RANDOM.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            byte[] cipherText=cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer=ByteBuffer.allocate(nonce.length+cipherText.length);
            buffer.put(nonce).put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch(Exception e){throw new IllegalStateException("Credential encryption failed",e);}
    }
    public String decrypt(String encrypted) {
        try {
            byte[] raw=Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer=ByteBuffer.wrap(raw);
            byte[] nonce=new byte[12];buffer.get(nonce);
            byte[] text=new byte[buffer.remaining()];buffer.get(text);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            return new String(cipher.doFinal(text),StandardCharsets.UTF_8);
        } catch(Exception e){throw new IllegalStateException("Credential decryption failed");}
    }
}
