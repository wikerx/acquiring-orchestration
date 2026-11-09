package com.scott.payment.data.service.export;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 管理端导出任务创建时冻结的商户范围，空的有限集合不具有全量语义。
 */
public class AdminExportScopeSnapshot {
    private boolean allMerchants;
    private Set<String> merchantIds = new LinkedHashSet<>();

    /**
     * 返回任务是否具有全部商户范围。
     *
     * @return 全部商户范围标志
     */
    public boolean isAllMerchants() {
        return allMerchants;
    }

    /**
     * 设置任务创建时的全部商户范围标志。
     *
     * @param allMerchants 全部商户范围标志
     */
    public void setAllMerchants(boolean allMerchants) {
        this.allMerchants = allMerchants;
    }

    /**
     * 返回任务创建时冻结的商户号集合。
     *
     * @return 商户号集合
     */
    public Set<String> getMerchantIds() {
        return merchantIds;
    }

    /**
     * 防御性复制任务创建时冻结的商户号集合。
     *
     * @param merchantIds 商户号集合
     */
    public void setMerchantIds(Set<String> merchantIds) {
        this.merchantIds = merchantIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(merchantIds);
    }
}
