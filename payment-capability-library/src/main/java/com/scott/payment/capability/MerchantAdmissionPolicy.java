package com.scott.payment.capability;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/** 支付方式授权的时间和准入模式规则，资金调用与只读投影共享此口径。 */
public final class MerchantAdmissionPolicy {

    private static final Pattern PAYMENT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,51}");
    private static final String BANK_CARD = "BANK_CARD";

    private MerchantAdmissionPolicy() {
    }

    /** 从既有支付协议与可信品牌得到规范方式。未知品牌不能提供全卡组织能力。 */
    public static String itemKey(String paymentMethod, String confirmedCardBrand) {
        String method = normalize(paymentMethod);
        if (BANK_CARD.equals(method)) {
            String brand = normalize(confirmedCardBrand);
            return valid(brand) && !BANK_CARD.equals(brand) ? "CARD_BRAND:" + brand : null;
        }
        return valid(method) ? "METHOD:" + method : null;
    }

    /** 校验授权层的规范键，避免大小写或全量容器绕过批准范围。 */
    public static boolean isItemKey(String value) {
        if (value == null) {
            return false;
        }
        int separator = value.indexOf(':');
        if (separator < 0) {
            return false;
        }
        String prefix = value.substring(0, separator);
        String code = value.substring(separator + 1);
        return ("CARD_BRAND".equals(prefix) || "METHOD".equals(prefix)) && valid(code) && !BANK_CARD.equals(code);
    }

    /** 授权生效含下界、到期不含上界。显式暂停、终止优先于时间和迁移模式。 */
    public static State state(Grant grant, LocalDateTime now) {
        if (grant == null) {
            return State.NOT_GRANTED;
        }
        if ("SUSPENDED".equals(grant.status())) {
            return State.SUSPENDED;
        }
        if ("TERMINATED".equals(grant.status())) {
            return State.TERMINATED;
        }
        if (!"ACTIVE".equals(grant.status()) || grant.effectiveTime() == null) {
            return State.UNKNOWN;
        }
        if (grant.expiresAt() != null && !now.isBefore(grant.expiresAt())) {
            return State.EXPIRED;
        }
        return now.isBefore(grant.effectiveTime()) ? State.NOT_YET_EFFECTIVE : State.ACTIVE;
    }

    /** 返回有真实批准来源且允许在指定迁移模式中使用的当前授权。 */
    public static boolean permits(String mode, Grant grant, boolean frozenLegacyScope, LocalDateTime checkedAt) {
        State state = state(grant, checkedAt);
        if (state != State.ACTIVE && state != State.NOT_GRANTED) {
            return false;
        }
        if (grant != null && "APPLICATION".equals(grant.source())) {
            return "ENFORCED".equals(mode) || "LEGACY_OBSERVE".equals(mode);
        }
        return "LEGACY_OBSERVE".equals(mode) && frozenLegacyScope
                && (grant == null || "HISTORICAL_BASELINE".equals(grant.source()));
    }

    private static boolean valid(String code) {
        return PAYMENT_CODE.matcher(code).matches()
                && !"ALL".equals(code) && !"NONE".equals(code) && !"UNKNOWN".equals(code);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** 当前方式授权、有效期和来源事实。 */
    public record Grant(String status, String source, long version,
                        LocalDateTime effectiveTime, LocalDateTime expiresAt) {
    }

    /** 运营和付款受理使用的判定状态。 */
    public enum State {
        NOT_GRANTED, NOT_YET_EFFECTIVE, ACTIVE, EXPIRED, SUSPENDED, TERMINATED, UNKNOWN
    }
}
