package com.example.insurancecrm.util;

public class PhoneUtil {

    private PhoneUtil() {}

    /**
     * Strips all formatting and country code prefix, returning a clean digit string.
     * Examples:
     *   +91 98765 43210  →  9876543210
     *   91-9876543210    →  9876543210
     *   (098) 765-43210  →  09876543210  (kept as-is if not 12-digit 91 prefix)
     *   9876543210       →  9876543210
     */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) return "";

        // Remove everything except digits
        String digits = phone.replaceAll("[^\\d]", "");

        // Strip Indian country code: 91XXXXXXXXXX (12 digits starting with 91)
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }

        return digits;
    }
}
