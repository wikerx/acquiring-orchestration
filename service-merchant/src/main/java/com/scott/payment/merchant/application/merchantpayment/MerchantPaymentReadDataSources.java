package com.scott.payment.merchant.application.merchantpayment;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentRepository;
import com.scott.payment.capability.governance.MerchantPaymentRepository.ReadVersion;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/** 用代理明确选库，副本版本和业务数据在同一只读快照里验证。 */
@Service
public class MerchantPaymentReadDataSources {
    private final MerchantPaymentRepository repository;
    private final MerchantPaymentGovernanceService governance;

    public MerchantPaymentReadDataSources(MerchantPaymentRepository repository, MerchantPaymentGovernanceService governance) {
        this.repository = repository;
        this.governance = governance;
    }

    /**
     * 从权威数据源核验当前会话的商户与账号归属。
     */
    @DS(DataSourceName.MASTER)
    public Scope verifyMerchant(String merchant, long account, long user) {
        return governance.merchantScope(merchant, account, user);
    }

    /**
     * 读取主库已提交版本，用于判断备库读取是否足够新。
     */
    @DS(DataSourceName.MASTER)
    public ReadVersion committedVersion(String merchant) {
        return repository.readVersion(merchant);
    }

    @DS(DataSourceName.SLAVE)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public <T> ReplicaResult<T> readReplica(String merchant, ReadVersion committed, Supplier<T> query) {
        if (!committed.equals(repository.readVersion(merchant))) {
            return new ReplicaResult<>(false, null);
        }
        return new ReplicaResult<>(true, query.get());
    }

    @DS(DataSourceName.MASTER)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public <T> T readMaster(Supplier<T> query) {
        return query.get();
    }

    public record ReplicaResult<T>(boolean matched, T value) {}
}
