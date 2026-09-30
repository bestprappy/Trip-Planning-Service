package com.navio.tripplanningservice.service.publication;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mints the secret in a shared URL.
 *
 * <p>256 bits from {@link SecureRandom}, base64url without padding — 43
 * URL-safe characters. The token is the <em>only</em> thing standing between a
 * stranger and the plan, so it is not derived from the trip id, the owner, a
 * timestamp or a counter: anything guessable or enumerable would turn one leaked
 * link into a way to walk the others.
 */
@Component
public class ShareTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
