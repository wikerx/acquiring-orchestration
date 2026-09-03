package com.scott.payment.openapi.security;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyProvider
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 商户密钥获取接口
 * @status : create
 */
public interface MerchantKeyProvider {

    /**
     * 查询商户密钥；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户开放接口服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param merchantId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 查询得到的业务对象、分页结果或空结果
     */
    String getMerchantKey(String merchantId);
}
