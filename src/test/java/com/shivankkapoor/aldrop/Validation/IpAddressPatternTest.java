package com.shivankkapoor.aldrop.Validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IpAddressPatternTest {

    private final Pattern pattern = Pattern.compile(IpAddressPattern.REGEX);

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "0.0.0.0",
            "127.0.0.1",
            "192.168.1.1",
            "255.255.255.255",
            "10.0.0.88",
            "1.2.3.4",
            "::1",
            "::",
            "2001:db8::1",
            "2001:0db8:0000:0000:0000:ff00:0042:8329",
            "fe80::1ff:fe23:4567:890a",
            "::ffff:192.168.1.1",
            "2001:db8:3333:4444:5555:6666:7777:8888",
            "a:b:c:d:e:f:0:1"
    })
    void acceptsValidIpAddresses(String value) {
        assertThat(pattern.matcher(value).matches()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-an-ip",
            "256.1.1.1",
            "1.2.3.4.5",
            "1.2.3",
            "192.168.1.1 ",
            " 192.168.1.1",
            "192.168.1.1/24",
            "12345::1",
            "gggg::1",
            "1.2.3.4:80",
            "'; DROP TABLE sessions; --",
            "192.168.1.1,10.0.0.1"
    })
    void rejectsInvalidIpAddresses(String value) {
        assertThat(pattern.matcher(value).matches()).isFalse();
    }
}
