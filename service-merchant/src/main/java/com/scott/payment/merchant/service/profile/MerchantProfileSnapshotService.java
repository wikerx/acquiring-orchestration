package com.scott.payment.merchant.service.profile;

import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileSnapshotService
 * @date : 2026-09-20 12:00
 * @email : scott_x@163.com
 * @description : 商户敏感资料快照端口，隔离申请状态机与正式资料读取和生效逻辑
 * @status : create
 */
public interface MerchantProfileSnapshotService {

    /** 读取当前正式生效的敏感资料快照。 */
    MerchantProfileChangeDTOs.ProfileSnapshot loadCurrent(String merchantId);

    /** 将审核通过的快照原子应用到正式商户资料。 */
    void apply(String merchantId, MerchantProfileChangeDTOs.ProfileSnapshot snapshot);
}
