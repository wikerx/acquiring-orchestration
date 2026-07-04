package com.scott.payment.admin.service;

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
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Email 服务契约，位于 service-admin 的服务契约层，用于定义调用契约和职责边界。
 * @status : create
 */
public interface AdminEmailService {

    PageResult<EmailAccountResponse> pageAccounts(EmailAccountQuery query);

    EmailAccountResponse getAccount(Long id);

    EmailAccountResponse createAccount(EmailAccountSaveRequest request);

    EmailAccountResponse updateAccount(Long id, EmailAccountSaveRequest request);

    EmailAccountResponse updateAccountStatus(Long id, Integer status);

    EmailAccountResponse setDefaultAccount(Long id);

    void deleteAccount(Long id);

    EmailSendResult sendTestEmail(Long accountId, EmailAccountTestRequest request);

    PageResult<EmailTemplateResponse> pageTemplates(EmailTemplateQuery query);

    EmailTemplateResponse getTemplate(Long id);

    EmailTemplateResponse createTemplate(EmailTemplateSaveRequest request);

    EmailTemplateResponse updateTemplate(Long id, EmailTemplateSaveRequest request);

    EmailTemplateResponse copyTemplate(Long id);

    EmailTemplateResponse updateTemplateStatus(Long id, Integer status);

    void deleteTemplate(Long id);

    EmailTemplatePreviewResponse previewTemplate(EmailTemplatePreviewRequest request);

    PageResult<EmailRecordResponse> pageRecords(EmailRecordQuery query);

    EmailRecordResponse getRecord(Long id);

    EmailSendResult sendByTemplate(EmailSendRequest request);

    EmailSendResult resend(Long id);
}
