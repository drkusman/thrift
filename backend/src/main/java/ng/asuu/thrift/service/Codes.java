package ng.asuu.thrift.service;

import java.security.SecureRandom;
import java.util.Base64;

public final class Codes {
    private Codes() {}
    private static final SecureRandom RANDOM = new SecureRandom();

    /** High-entropy, URL-safe token for one-time links (password reset, etc.) - not meant to be typed by a human. */
    public static String secureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
