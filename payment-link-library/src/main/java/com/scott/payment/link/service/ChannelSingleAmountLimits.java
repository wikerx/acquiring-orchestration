package com.scott.payment.link.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 对已换算的渠道金额应用启用的单笔限额，限额数值仅在服务端使用。 */
@Service
public class ChannelSingleAmountLimits {
    public static final String FAILURE_CODE = "SINGLE_AMOUNT_LIMIT_EXCEEDED";
    public static final String PUBLIC_CODE = OpenApiResultCode.SINGLE_AMOUNT_LIMIT_EXCEEDED.getCode();
    public static final String FAILURE_MESSAGE = "Choose another payment method or contact the merchant.";
    private static final BigDecimal DEVELOPMENT_WALLET_MAX = new BigDecimal("10000.00");

    private final JdbcTemplate jdbc;

    public ChannelSingleAmountLimits(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static boolean exceedsDevelopmentWalletMaximum(BigDecimal amount) {
        return amount.compareTo(DEVELOPMENT_WALLET_MAX) > 0;
    }

    @DS(DataSourceName.MASTER)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Rules load(Collection<Long> channelIds, String currency) {
        if (channelIds.isEmpty() || currency == null) {
            return new Rules(List.of());
        }
        var args = new java.util.ArrayList<Object>(channelIds);
        args.add(currency);
        var placeholders = String.join(", ", Collections.nCopies(channelIds.size(), "?"));
        return new Rules(jdbc.query("""
                SELECT channel_id, payment_method, card_brand, limit_type, limit_amount
                FROM channel_limit_rule
                WHERE channel_id IN (%s) AND business_type = 'ACQUIRING' AND limit_currency = ?
                  AND rule_status = 1 AND deleted = 0 AND limit_type IN ('SINGLE_MIN', 'SINGLE_MAX')
                """.formatted(placeholders), (row, index) -> new Rule(row.getLong("channel_id"),
                row.getString("payment_method"), row.getString("card_brand"),
                "SINGLE_MIN".equals(row.getString("limit_type")), row.getBigDecimal("limit_amount")), args.toArray()));
    }

    public static final class Rules {
        private final List<Rule> rules;

        private Rules(List<Rule> rules) {
            this.rules = List.copyOf(rules);
        }

        public boolean rejects(Long channelId, String method, String brand, BigDecimal amount) {
            return amount != null && rules.stream().anyMatch(rule -> channelId != null && rule.channelId() == channelId
                    && matches(rule.method(), method) && matches(rule.brand(), brand)
                    && (rule.minimum() ? amount.compareTo(rule.amount()) < 0 : amount.compareTo(rule.amount()) > 0));
        }

        private static boolean matches(String scope, String value) {
            return "ALL".equalsIgnoreCase(scope) || scope.equalsIgnoreCase(value);
        }
    }

    private record Rule(long channelId, String method, String brand, boolean minimum, BigDecimal amount) { }
}
