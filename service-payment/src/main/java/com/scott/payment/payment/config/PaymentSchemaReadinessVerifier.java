package com.scott.payment.payment.config;

import com.scott.payment.payment.mapper.PaymentSchemaReadinessMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentSchemaReadinessVerifier
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 支付服务启动门禁，确保动作逻辑表和现有月分表可持久化可信接入 IP 审计信息。
 * @status : create
 */
@Component
public class PaymentSchemaReadinessVerifier implements SmartInitializingSingleton {

    /** 需要先由运维评审并执行的交易动作接入 IP 迁移草案。 */
    static final String MIGRATION_FILE = "docs/sql/20261017_03_transaction_ingress_ip_migration_draft.sql";

    private final PaymentSchemaReadinessMapper readinessMapper;

    /**
     * 创建支付数据库契约启动门禁。
     *
     * @param readinessMapper information_schema 只读查询 Mapper
     */
    public PaymentSchemaReadinessVerifier(PaymentSchemaReadinessMapper readinessMapper) {
        this.readinessMapper = readinessMapper;
    }

    /**
     * 单例初始化完成后检查逻辑表及所有现有物理分表；查询异常或字段缺失均阻止启动。
     */
    @Override
    public void afterSingletonsInstantiated() {
        List<String> incompatibleTables;
        try {
            incompatibleTables = readinessMapper.selectIncompatibleTransactionOperationTables();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Payment schema readiness check failed; review and apply "
                    + MIGRATION_FILE, exception);
        }
        if (incompatibleTables != null && !incompatibleTables.isEmpty()) {
            throw new IllegalStateException("Payment schema is not ready; transaction operation tables missing "
                    + "ingress IP audit columns: " + String.join(", ", incompatibleTables)
                    + "; review and apply " + MIGRATION_FILE);
        }
    }
}
