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
 * @description : AdminMerchantMenuGrantApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminMerchantMenuGrantApplicationService {

    /**
     * admin Merchant Menu Grant Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
