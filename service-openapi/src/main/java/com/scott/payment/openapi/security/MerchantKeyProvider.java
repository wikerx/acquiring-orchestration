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
     * 查询商户密钥，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户开放接口服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    String getMerchantKey(String merchantId);
}
