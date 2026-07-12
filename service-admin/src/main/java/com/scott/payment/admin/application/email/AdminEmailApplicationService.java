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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 邮件管理Admin Email Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
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
     * 查询邮件管理列表或分页数据，供页面筛选和展示使用。
     * @param query 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public PageResult<EmailAccountResponse> pageAccounts(EmailAccountQuery query) {
        return adminEmailService.pageAccounts(query);
    }

    /**
     * 获取邮件管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailAccountResponse getAccount(Long id) {
        return adminEmailService.getAccount(id);
    }

    /**
     * 创建或保存邮件管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailAccountResponse createAccount(EmailAccountSaveRequest request) {
        return adminEmailService.createAccount(request);
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailAccountResponse updateAccount(Long id, EmailAccountSaveRequest request) {
        return adminEmailService.updateAccount(id, request);
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailAccountResponse updateAccountStatus(Long id, Integer status) {
        return adminEmailService.updateAccountStatus(id, status);
    }

    /**
     * 执行邮件管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailAccountResponse setDefaultAccount(Long id) {
        return adminEmailService.setDefaultAccount(id);
    }

    /**
     * 删除邮件管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void deleteAccount(Long id) {
        adminEmailService.deleteAccount(id);
    }

    /**
     * 发送邮件管理消息或外部请求，并记录必要的执行结果。
     * @param accountId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailSendResult sendTestEmail(Long accountId, EmailAccountTestRequest request) {
        return adminEmailService.sendTestEmail(accountId, request);
    }

    /**
     * 查询邮件管理列表或分页数据，供页面筛选和展示使用。
     * @param query 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public PageResult<EmailTemplateResponse> pageTemplates(EmailTemplateQuery query) {
        return adminEmailService.pageTemplates(query);
    }

    /**
     * 获取邮件管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailTemplateResponse getTemplate(Long id) {
        return adminEmailService.getTemplate(id);
    }

    /**
     * 创建或保存邮件管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailTemplateResponse createTemplate(EmailTemplateSaveRequest request) {
        return adminEmailService.createTemplate(request);
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailTemplateResponse updateTemplate(Long id, EmailTemplateSaveRequest request) {
        return adminEmailService.updateTemplate(id, request);
    }

    /**
     * 执行邮件管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailTemplateResponse copyTemplate(Long id) {
        return adminEmailService.copyTemplate(id);
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailTemplateResponse updateTemplateStatus(Long id, Integer status) {
        return adminEmailService.updateTemplateStatus(id, status);
    }

    /**
     * 删除邮件管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void deleteTemplate(Long id) {
        adminEmailService.deleteTemplate(id);
    }

    /**
     * 执行邮件管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailTemplatePreviewResponse previewTemplate(EmailTemplatePreviewRequest request) {
        return adminEmailService.previewTemplate(request);
    }

    /**
     * 查询邮件管理列表或分页数据，供页面筛选和展示使用。
     * @param query 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public PageResult<EmailRecordResponse> pageRecords(EmailRecordQuery query) {
        return adminEmailService.pageRecords(query);
    }

    /**
     * 获取邮件管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailRecordResponse getRecord(Long id) {
        return adminEmailService.getRecord(id);
    }

    /**
     * 发送邮件管理消息或外部请求，并记录必要的执行结果。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailSendResult sendByTemplate(EmailSendRequest request) {
        return adminEmailService.sendByTemplate(request);
    }

    /**
     * 执行邮件管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public EmailSendResult resend(Long id) {
        return adminEmailService.resend(id);
    }
}
