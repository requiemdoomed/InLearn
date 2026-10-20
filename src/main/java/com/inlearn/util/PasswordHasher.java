package com.inlearn.util;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hashes and verifies passwords with bcrypt (Bouncy Castle's {@link OpenBSDBCrypt}).
 *
 * <p>bcrypt stores the algorithm version, cost and random salt inside the hash string itself,
 * so only that single string is saved in the database. Plain-text passwords are never stored
 * or logged.
 */
public class PasswordHasher {

    /** Work factor: 2^12 rounds takes roughly a quarter second, slow enough to resist guessing. */
    public static final int DEFAULT_COST = 12;

    private static final Logger LOGGER = Logger.getLogger(PasswordHasher.class.getName());
    private static final String BCRYPT_VERSION = "2b";
    private static final int SALT_LENGTH_BYTES = 16;

    private final int cost;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHasher() {
        this(DEFAULT_COST);
    }

    /** Creates a hasher with a custom cost; tests use a low cost to stay fast. */
    public PasswordHasher(int cost) {
        this.cost = cost;
    }

    public String hash(char[] password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        return OpenBSDBCrypt.generate(BCRYPT_VERSION, password, salt, cost);
    }

    /**
     * Checks a password against a stored bcrypt hash.
     *
     * @return {@code false} for a wrong password or a malformed stored hash
     */
    public boolean verify(char[] password, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return OpenBSDBCrypt.checkPassword(storedHash, password);
        } catch (IllegalArgumentException e) {
            // A corrupted hash must never let anyone in, and must not crash sign-in either.
            LOGGER.log(Level.WARNING, "A stored password hash has an invalid format");
            return false;
        }
    }
}
