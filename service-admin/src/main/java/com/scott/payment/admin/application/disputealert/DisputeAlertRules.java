package com.scott.payment.admin.application.disputealert;

import com.scott.payment.admin.dto.risk.DisputeAlertDtos.*;
import com.scott.payment.admin.infrastructure.disputealert.DisputeAlertRepository.Mid;
import com.scott.payment.admin.infrastructure.disputealert.DisputeAlertRepository.Source;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 约束拒付预警配置的状态、产品资料及关联范围。 */
final class DisputeAlertRules {

    static final String SHIELD = "VERXA_SHIELD";
    private static final Set<String> NON_BRANDS = Set.of("ALL", "NONE", "UNKNOWN");
    private static final Set<String> TEST_MID_PROFILES = Set.of("dev", "test", "uat", "sample");

    private DisputeAlertRules() {
    }

    static List<String> cardBrands(PlatformProductInput product, Set<String> enabledBrands) {
        List<String> brands = product.cardBrands() == null ? List.of() : product.cardBrands().stream().map(DisputeAlertRules::brand).toList();
        if (new HashSet<>(brands).size() != brands.size() || !enabledBrands.containsAll(brands)
                || (product.product() == Product.RDR && !Set.of("VISA").containsAll(brands))
                || (product.product() == Product.AMEX_INQUIRY && !Set.of("AMEX").containsAll(brands))) {
            throw invalid("卡品牌不适用于该产品，或包含重复选项");
        }
        if (product.coverage() == Coverage.CONFIRMED && brands.isEmpty()) {
            throw invalid("确认卡品牌覆盖时至少登记一个卡品牌");
        }
        return brands;
    }

    static boolean isCardBrand(String code) {
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,31}") && !NON_BRANDS.contains(code);
    }

    static boolean eligibleMid(Mid mid) {
        return mid.midAvailable() && "ACQUIRING".equals(mid.businessType())
                && StringUtils.hasText(mid.effectiveCardBrands()) && !"NONE".equalsIgnoreCase(mid.effectiveCardBrands());
    }

    static Environment resolveDeploymentEnvironment(String[] activeProfiles) {
        List<String> profiles = Arrays.asList(activeProfiles);
        boolean production = profiles.contains("prod");
        boolean test = profiles.stream().anyMatch(TEST_MID_PROFILES::contains);
        // 无法识别部署环境或同时激活两类环境时，保留已有记录的查看能力。
        if (production == test) return null;
        return production ? Environment.PRODUCTION : Environment.TEST;
    }

    static boolean supportsMid(Mid mid, Source source, List<String> brands) {
        if (!eligibleMid(mid)) return false;
        Set<String> allowed = new HashSet<>(Arrays.stream(mid.effectiveCardBrands().split(","))
                .map(DisputeAlertRules::brand).toList());
        if (source.product() == Product.RDR) return allowed.contains("VISA");
        return source.coverage() == Coverage.PENDING || brands.stream().anyMatch(allowed::contains);
    }

    static boolean sourceAvailable(Source source, Environment midEnvironment) {
        return midEnvironment != null && source.environment() == midEnvironment
                && source.platformEnabled() && source.platformProductEnabled()
                && source.accountStatus() == Status.ENABLED && source.productStatus() == Status.ENABLED
                && (!SHIELD.equals(source.platformCode()) || source.credentialVersion() != null)
                && (!DisputeAlertProviderRules.supported(source.platformCode()) || source.providerActivationValidated());
    }

    static void requireMidProduct(Product product) {
        if (product != Product.ETHOCA && product != Product.RDR) {
            throw invalid("渠道 MID 一期支持登记 Ethoca 和 RDR");
        }
    }

    static Delivery delivery(Delivery value) {
        if (value == null) return new Delivery(Transport.HTTPS, null, null, null, null, null);
        if (value.transport() == null) throw invalid("请填写产品交付方式");
        if (value.transport() == Transport.HTTPS) {
            String outcomeUrl = trim(value.outcomeUrl());
            if (outcomeUrl != null) {
                URI parsed;
                try {
                    parsed = URI.create(outcomeUrl);
                } catch (IllegalArgumentException failure) {
                    throw invalid("业务回执地址格式无效");
                }
                if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                        || parsed.getUserInfo() != null || parsed.getRawQuery() != null || parsed.getFragment() != null) {
                    throw invalid("业务回执地址须为无凭据的 HTTPS 地址");
                }
            }
            return new Delivery(value.transport(), null, null, null, trim(value.sourceAuthentication()), outcomeUrl);
        }
        return new Delivery(value.transport(), trim(value.host()), trim(value.directory()), trim(value.schedule()), null, null);
    }

    static List<IdentityInput> identifiers(Product product, List<IdentityInput> rows) {
        List<IdentityInput> normalized = rows.stream().map(row -> normalizeIdentity(product, row)).toList();
        long distinct = normalized.stream().map(row -> List.of(row.type(), row.acquirer(), row.acquirerMid(),
                row.acquirerBin(), row.caid())).distinct().count();
        if (distinct != normalized.size()) throw invalid("收单行标识重复");
        return normalized;
    }

    static void validateBinding(BindingInput input) {
        if (input.enrollment() == Enrollment.CONFIRMED && !StringUtils.hasText(input.enrollmentEvidence())) {
            throw invalid("确认上游开通时请录入开通依据");
        }
        if (input.descriptors().stream().anyMatch(row -> !StringUtils.hasText(row.value()))) {
            throw invalid("账单描述不能为空");
        }
        if (input.descriptors().stream().map(DescriptorInput::value).distinct().count() != input.descriptors().size()) {
            throw invalid("账单描述重复");
        }
    }

    static void checkVersion(long persisted, Long requested) {
        if (requested == null || persisted != requested) {
            throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode(), "配置已更新，请刷新后重试");
        }
    }

    static String trim(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    static ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private static IdentityInput normalizeIdentity(Product product, IdentityInput row) {
        String acquirer = row.acquirer().strip();
        String mid = trim(row.acquirerMid());
        String bin = trim(row.acquirerBin());
        String caid = trim(row.caid());
        if (row.type() == IdentityType.ACQUIRER_MID && mid != null && bin == null && caid == null) {
            return new IdentityInput(row.type(), acquirer, mid, "", "", row.enabled());
        }
        if (row.type() == IdentityType.BIN_CAID && product == Product.RDR && mid == null
                && bin != null && bin.matches("[0-9]{6}|[0-9]{8}") && caid != null) {
            return new IdentityInput(row.type(), acquirer, "", bin, caid, row.enabled());
        }
        throw invalid("请按类型填写收单行 MID，或成对填写 RDR 的收单 BIN 与 CAID");
    }

    private static String brand(String value) {
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return "AMERICAN_EXPRESS".equals(normalized) ? "AMEX" : normalized;
    }
}
