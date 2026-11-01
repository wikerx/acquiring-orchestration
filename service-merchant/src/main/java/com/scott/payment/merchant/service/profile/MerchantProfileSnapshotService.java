package com.scott.payment.merchant.service.profile;

import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;

/**
 * 商户敏感资料快照端口，隔离申请状态机与正式资料读取和生效逻辑
 */
public interface MerchantProfileSnapshotService {

    /**
     * 读取当前正式生效的敏感资料快照。
     */
    MerchantProfileChangeDTOs.ProfileSnapshot loadCurrent(String merchantId);

    /**
     * 将审核通过的快照原子应用到正式商户资料。
     */
    void apply(String merchantId, MerchantProfileChangeDTOs.ProfileSnapshot snapshot);
}
