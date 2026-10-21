package com.example.resqtap.room;

import java.security.SecureRandom;


/**
 * RoomCodeUtils
 * Utility untuk generate random 6-character room code (contoh: RQ7B9X).
 */
public final class RoomCodeUtils {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private RoomCodeUtils() {}

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[RNG.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}

