package org.sibyl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HMAC signed, time-bound collector observations. Disabled until a key is configured. */
@Component
public final class CollectorProof {
    private final Map<String,byte[]> keys;
    private final Clock clock;

    @Autowired
    public CollectorProof(@Value("$"+"{SIBYL_COLLECTOR_KEYS:}") String config) {
        this(config,Clock.systemUTC());
    }
    CollectorProof(String config,Clock clock) {
        this.clock=clock;
        Map<String,byte[]> parsed=new HashMap<>();
        if (!config.isBlank()) for (String entry : config.split(",")) {
            String[] kv=entry.trim().split(":",2);
            if (kv.length != 2 || !kv[0].matches("[a-z][a-z0-9._-]{2,39}")
                || !kv[1].matches("[0-9a-fA-F]{64,128}")
                || kv[1].length()%2!=0 || parsed.containsKey(kv[0]))
                throw new IllegalArgumentException("Invalid collector key configuration");
            parsed.put(kv[0],HexFormat.of().parseHex(kv[1]));
        }
        keys=Map.copyOf(parsed);
    }
    public boolean verify(String id,String time,String nonce,String signature,byte[] body) {
        if (id==null || time==null || nonce==null || signature==null || body==null
            || body.length>32768 || !time.matches("[0-9]{10,11}")
            || !nonce.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
            || !signature.matches("[0-9a-fA-F]{64}")) return false;
        byte[] key=keys.get(id);
        if(key==null) return false;
        try {
            long seconds=Long.parseLong(time);
            if(Math.abs(clock.instant().getEpochSecond()-seconds)>120L) return false;
            String digest=HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body));
            String canonical=time+"\n"+nonce+"\n"+id+"\n"+digest;
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return MessageDigest.isEqual(
                mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)),
                HexFormat.of().parseHex(signature));
        } catch (Exception ignored) {
            return false;
        }
    }
}
