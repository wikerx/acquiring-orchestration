package com.scott.payment.admin.application.email;

import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountSaveRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountTestRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateSaveRequest;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailApplicationService
 * @date : 2026-07-04 16:11
 * @email : scott_x@163.com
 * @description : admin邮件应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class AdminEmailApplicationService {

    /**
     * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private final AdminEmailService adminEmailService;

    public AdminEmailApplicationService(AdminEmailService adminEmailService) {
        this.adminEmailService = adminEmailService;
    }

    /**
     * 查询账号；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<EmailAccountResponse> pageAccounts(EmailAccountQuery query) {
        return adminEmailService.pageAccounts(query);
    }

    /**
     * 查询账号；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public EmailAccountResponse getAccount(Long id) {
        return adminEmailService.getAccount(id);
    }

    /**
     * 创建账号，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public EmailAccountResponse createAccount(EmailAccountSaveRequest request) {
        return adminEmailService.createAccount(request);
    }

    /**
     * 更新账号，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public EmailAccountResponse updateAccount(Long id, EmailAccountSaveRequest request) {
        return adminEmailService.updateAccount(id, request);
    }

    /**
     * 更新账号状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public EmailAccountResponse updateAccountStatus(Long id, Integer status) {
        return adminEmailService.updateAccountStatus(id, status);
    }

    /**
     * 将指定邮件账号设为默认发送账号。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 更新后的默认邮件账号
     */
    public EmailAccountResponse setDefaultAccount(Long id) {
        return adminEmailService.setDefaultAccount(id);
    }

    /**
     * 删除或停用账号，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteAccount(Long id) {
        adminEmailService.deleteAccount(id);
    }

    /**
     * 使用指定邮件账号发送连通性测试邮件。
     * <p>
     * 跨进程调用必须透传 traceId 和原业务标识，并按 运营后台服务 规则转换超时与失败结果。
     * </p>
     * @param accountId 邮件发送账号主键
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 测试邮件任务及投递状态
     */
    public EmailSendResult sendTestEmail(Long accountId, EmailAccountTestRequest request) {
        return adminEmailService.sendTestEmail(accountId, request);
    }

    /**
     * 查询模板；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<EmailTemplateResponse> pageTemplates(EmailTemplateQuery query) {
        return adminEmailService.pageTemplates(query);
    }

    /**
     * 查询模板；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public EmailTemplateResponse getTemplate(Long id) {
        return adminEmailService.getTemplate(id);
    }

    /**
     * 创建模板，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public EmailTemplateResponse createTemplate(EmailTemplateSaveRequest request) {
        return adminEmailService.createTemplate(request);
    }

    /**
     * 更新模板，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public EmailTemplateResponse updateTemplate(Long id, EmailTemplateSaveRequest request) {
        return adminEmailService.updateTemplate(id, request);
    }

    /**
     * 复制现有邮件模板为新的草稿模板。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 新建的模板副本
     */
    public EmailTemplateResponse copyTemplate(Long id) {
        return adminEmailService.copyTemplate(id);
    }

    /**
     * 更新邮件模板启停状态。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public EmailTemplateResponse updateTemplateStatus(Long id, Integer status) {
        return adminEmailService.updateTemplateStatus(id, status);
    }

    /**
     * 删除或停用模板，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteTemplate(Long id) {
        adminEmailService.deleteTemplate(id);
    }

    /**
     * 使用样例变量渲染邮件模板预览，不创建发送记录。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 渲染后的主题、正文和缺失变量信息
     */
    public EmailTemplatePreviewResponse previewTemplate(EmailTemplatePreviewRequest request) {
        return adminEmailService.previewTemplate(request);
    }

    /**
     * 查询记录；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<EmailRecordResponse> pageRecords(EmailRecordQuery query) {
        return adminEmailService.pageRecords(query);
    }

    /**
     * 查询记录；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public EmailRecordResponse getRecord(Long id) {
        return adminEmailService.getRecord(id);
    }

    /**
     * 按启用模板创建邮件发送记录并进入可靠投递队列。
     * <p>
     * 跨进程调用必须透传 traceId 和原业务标识，并按 运营后台服务 规则转换超时与失败结果。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 新建邮件任务及初始投递状态
     */
    public EmailSendResult sendByTemplate(EmailSendRequest request) {
        return adminEmailService.sendByTemplate(request);
    }

    /**
     * 从允许重发的历史邮件创建新的发送任务。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 新建重发任务及初始投递状态
     */
    public EmailSendResult resend(Long id) {
        return adminEmailService.resend(id);
    }
}
