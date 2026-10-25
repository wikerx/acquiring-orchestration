package com.scott.payment.component.core.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OutboundHttpUrlPolicyTests
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : 出站 HTTP URL 安全策略测试，覆盖环境差异、DNS 多地址和非公网地址阻断。
 * @status : create
 */
class OutboundHttpUrlPolicyTests {

    @Test
    void shouldAllowDevelopmentHttpAndPrivateTarget() {
        OutboundHttpUrlPolicy policy = new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.DEVELOPMENT,
                host -> addresses("10.1.2.3"));

        assertThat(policy.validate("http://10.1.2.3:8080/callback").toString())
                .isEqualTo("http://10.1.2.3:8080/callback");
    }

    @Test
    void shouldRequireHttpsAndPublicResolutionOutsideDevelopment() {
        OutboundHttpUrlPolicy policy = new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                host -> addresses("8.8.8.8"));

        assertThatThrownBy(() -> policy.validate("http://merchant.example/callback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(policy.validate("https://merchant.example/callback").getHost())
                .isEqualTo("merchant.example");
    }

    @Test
    void shouldRejectPrivateOrMixedDnsAnswers() {
        OutboundHttpUrlPolicy privatePolicy = new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                host -> addresses("10.1.2.3"));
        OutboundHttpUrlPolicy mixedPolicy = new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                host -> addresses("8.8.8.8", "127.0.0.1"));

        assertThatThrownBy(() -> privatePolicy.validate("https://merchant.example/callback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mixedPolicy.validate("https://merchant.example/callback"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRevalidateTheAddressesUsedByTheHttpConnection() {
        AtomicInteger lookups = new AtomicInteger();
        OutboundHttpUrlPolicy policy = new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                host -> lookups.getAndIncrement() == 0
                        ? addresses("8.8.8.8")
                        : addresses("127.0.0.1"));

        assertThat(policy.validate("https://merchant.example/callback").getHost())
                .isEqualTo("merchant.example");
        assertThatThrownBy(() -> policy.resolveAllowedAddresses("merchant.example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not public");
    }

    @Test
    void shouldRejectReservedIpv4AndIpv6Ranges() throws UnknownHostException {
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("169.254.169.254"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("100.64.0.1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("198.51.100.1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("2001:2::1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("2001:10::1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("2001:20::1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("2001:db8::1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("3fff::1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("fc00::1"))).isFalse();
        assertThat(OutboundHttpUrlPolicy.isPublicAddress(address("2606:4700:4700::1111"))).isTrue();
    }

    @Test
    void shouldRejectAlternativeLoopbackAddressRepresentations() {
        OutboundHttpUrlPolicy policy = new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY);

        assertThatThrownBy(() -> policy.validate("https://2130706433/callback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("https://[::ffff:127.0.0.1]/callback"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailClosedWhenDnsResolutionFailsOrUrlContainsCredentials() {
        OutboundHttpUrlPolicy unresolvedPolicy = new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                host -> {
                    throw new UnknownHostException(host);
                });

        assertThatThrownBy(() -> unresolvedPolicy.validate("https://merchant.example/callback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(OutboundHttpUrlPolicy.isStructurallyValid("https://user:secret@merchant.example/callback"))
                .isFalse();
        assertThat(OutboundHttpUrlPolicy.isStructurallyValid("https://merchant.example/callback#token"))
                .isFalse();
    }

    @Test
    void shouldFailClosedWhenActiveProfileIsNullOrUnknown() {
        assertThat(OutboundHttpUrlPolicy.modeForProfiles(new String[]{null}))
                .isEqualTo(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY);
        assertThat(OutboundHttpUrlPolicy.modeForProfiles("dev", "test"))
                .isEqualTo(OutboundHttpUrlPolicy.Mode.DEVELOPMENT);
        assertThat(OutboundHttpUrlPolicy.modeForProfiles("dev", "unknown"))
                .isEqualTo(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY);
        assertThat(OutboundHttpUrlPolicy.modeForProfiles("test", "prod"))
                .isEqualTo(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY);
    }

    private static InetAddress[] addresses(String... values) throws UnknownHostException {
        InetAddress[] addresses = new InetAddress[values.length];
        for (int index = 0; index < values.length; index++) {
            addresses[index] = address(values[index]);
        }
        return addresses;
    }

    private static InetAddress address(String value) throws UnknownHostException {
        return InetAddress.getByName(value);
    }
}
