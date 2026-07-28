package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.admin.service.AdminMerchantUserService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserApplicationService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Admin Merchant User Application Service 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
public class AdminMerchantUserApplicationService {

    /**
     * admin Merchant User Service 依赖，用于 Admin Merchant User Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminMerchantUserService adminMerchantUserService;

    /**
     * 整理admin商户用户applicationservice，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param adminMerchantUserService admin Merchant User Service 输入值，参与 admin商户用户service 的查询、校验、转换、写入或日志摘要
     */
    public AdminMerchantUserApplicationService(AdminMerchantUserService adminMerchantUserService) {
        this.adminMerchantUserService = adminMerchantUserService;
    }

    /**
     * 查询商户用户，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<AdminMerchantUserListDTO> pageMerchantUsers(AdminMerchantUserQueryRequest request) {
        return adminMerchantUserService.pageMerchantUsers(request);
    }

    /**
     * 查询商户用户，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public AdminMerchantUserDetailDTO getMerchantUser(Long accountId) {
        return adminMerchantUserService.getMerchantUser(accountId);
    }
}
