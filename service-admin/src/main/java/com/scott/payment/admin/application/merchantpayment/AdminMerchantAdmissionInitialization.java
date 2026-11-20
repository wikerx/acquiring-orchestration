package com.scott.payment.admin.application.merchantpayment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** 扩表和支付防护联调就绪后，可为新建商户设置明确的强制准入模式。 */
@Service
public class AdminMerchantAdmissionInitialization {
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean enforcedOnCreate;

    public AdminMerchantAdmissionInitialization(NamedParameterJdbcTemplate jdbc,
            @Value("${admin.merchant-payment.admission-enforced-on-create:false}") boolean enforcedOnCreate) {
        this.jdbc = jdbc;
        this.enforcedOnCreate = enforcedOnCreate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(String merchantId, LocalDateTime now) {
        if (!enforcedOnCreate) {
            return;
        }
        int updated = jdbc.update("""
                UPDATE base_merchant_info
                SET payment_admission_mode = 'ENFORCED', payment_admission_cutover_time = :now
                WHERE merchant_id = :merchantId AND deleted = 0 AND payment_admission_mode IS NULL
                """, Map.of("merchantId", merchantId, "now", now));
        if (updated != 1) {
            throw new IllegalStateException("new merchant payment admission mode was not initialized");
        }
    }
}
