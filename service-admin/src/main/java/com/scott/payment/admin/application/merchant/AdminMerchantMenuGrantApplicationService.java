package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;
import com.scott.payment.admin.service.AdminMerchantMenuGrantService;
import org.springframework.stereotype.Service;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantApplicationService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Admin Merchant Menu Grant Application Service 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
public class AdminMerchantMenuGrantApplicationService {

    /**
     * admin Merchant Menu Grant Service 依赖，用于 Admin Merchant Menu Grant Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminMerchantMenuGrantService adminMerchantMenuGrantService;

    /**
     * 创建商户菜单授权应用服务。
     *
     * @param adminMerchantMenuGrantService 商户菜单授权领域服务
     */
    public AdminMerchantMenuGrantApplicationService(AdminMerchantMenuGrantService adminMerchantMenuGrantService) {
        this.adminMerchantMenuGrantService = adminMerchantMenuGrantService;
    }

    /**
     * 查询商户菜单授权。
     *
     * @param merchantId 商户号
     * @return 授权信息
     */
    public AdminMerchantMenuGrantQueryResponse queryGrant(String merchantId) {
        return adminMerchantMenuGrantService.queryGrant(merchantId);
    }

    /**
     * 保存商户菜单授权。
     *
     * @param merchantId 商户号
     * @param request    保存请求
     */
    public void saveGrant(String merchantId, AdminMerchantMenuGrantSaveRequest request) {
        adminMerchantMenuGrantService.saveGrant(merchantId, request);
    }
}
