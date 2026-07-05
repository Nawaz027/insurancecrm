package com.example.insurancecrm.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneUtilTest {

    @ParameterizedTest
    @CsvSource({
            "'+91 98765 43210', 9876543210",
            "'91-9876543210',   9876543210",
            "'9876543210',      9876543210",
            "'+91-98765-43210', 9876543210",
    })
    void normalize_stripsFormattingAndIndianCountryCode(String input, String expected) {
        assertThat(PhoneUtil.normalize(input)).isEqualTo(expected);
    }

    @Test
    void normalize_keepsNonIndianOrNonTwelveDigitNumbersAsIs() {
        // 11 digits starting with 91 — not the 12-digit "91 + 10 digit" shape, so no stripping
        assertThat(PhoneUtil.normalize("91987654321")).isEqualTo("91987654321");
    }

    @Test
    void normalize_onlyStripsWhenPrefixIsExactly91() {
        // 12 digits but doesn't start with 91 — left untouched
        assertThat(PhoneUtil.normalize("123456789012")).isEqualTo("123456789012");
    }

    @Test
    void normalize_returnsEmptyStringForNull() {
        assertThat(PhoneUtil.normalize(null)).isEmpty();
    }

    @Test
    void normalize_returnsEmptyStringForBlank() {
        assertThat(PhoneUtil.normalize("   ")).isEmpty();
    }

    @Test
    void normalize_returnsEmptyStringWhenNoDigitsPresent() {
        assertThat(PhoneUtil.normalize("abc-def")).isEmpty();
    }
}
