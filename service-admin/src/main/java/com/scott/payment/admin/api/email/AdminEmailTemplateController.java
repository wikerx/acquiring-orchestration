package com.scott.payment.admin.api.email;

import com.scott.payment.admin.application.email.AdminEmailApplicationService;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailStatusRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateSaveRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/admin/email/templates")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailTemplateController
 * @date : 2026-07-04 16:11
 * @email : scott_x@163.com
 * @description : Admin Email Template Controller 控制器，位于 运营后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class AdminEmailTemplateController {

    /**
     * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private final AdminEmailApplicationService emailApplicationService;

    /**
     * 整理admin邮件templatecontroller，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param emailApplicationService email Application Service 输入值，参与 邮件applicationservice 的查询、校验、转换、写入或日志摘要
     */
    public AdminEmailTemplateController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    /**
     * 分页查询邮件模板。
     *
     * @param query 模板编码、名称、语言和状态等可选条件
     * @return 邮件模板分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("email:template:list")
    public CommonResult<PageResult<EmailTemplateResponse>> pageTemplates(@RequestBody(required = false) EmailTemplateQuery query) {
        return success(emailApplicationService.pageTemplates(query));
    }

    /**
     * 查询指定邮件模板详情。
     *
     * @param id 邮件模板主键
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("email:template:detail")
    public CommonResult<EmailTemplateResponse> getTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.getTemplate(id));
    }

    /**
     * 创建邮件模板，模板编码唯一性和变量语法由应用服务校验。
     *
     * @param request 模板保存请求
     * @return 创建后的模板详情
     */
    @PostMapping
    @RequiresPermission("email:template:add")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "新增邮件模板")
    public CommonResult<EmailTemplateResponse> createTemplate(@Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.createTemplate(request));
    }

    /**
     * 更新指定邮件模板。
     *
     * @param id 邮件模板主键
     * @param request 模板保存请求
     * @return 更新后的模板详情
     */
    @PutMapping("/{id}")
    @RequiresPermission("email:template:edit")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "修改邮件模板")
    public CommonResult<EmailTemplateResponse> updateTemplate(@PathVariable("id") Long id,
                                                              @Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.updateTemplate(id, request));
    }

    /**
     * 复制指定模板并生成独立模板记录。
     *
     * @param id 源邮件模板主键
     * @return 新模板详情
     */
    @PostMapping("/{id}/copy")
    @RequiresPermission("email:template:copy")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "复制邮件模板")
    public CommonResult<EmailTemplateResponse> copyTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.copyTemplate(id));
    }

    /**
     * 切换邮件模板启停状态。
     *
     * @param id 邮件模板主键
     * @param request 目标状态请求
     * @return 更新后的模板详情
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("email:template:status")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "切换邮件模板状态")
    public CommonResult<EmailTemplateResponse> updateTemplateStatus(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody EmailStatusRequest request) {
        return success(emailApplicationService.updateTemplateStatus(id, request.getStatus()));
    }

    /**
     * 使用示例变量渲染模板预览，不产生真实邮件发送记录。
     *
     * @param request 模板内容及预览变量
     * @return 渲染后的主题和正文
     */
    @PostMapping("/preview")
    @RequiresPermission("email:template:preview")
    public CommonResult<EmailTemplatePreviewResponse> previewTemplate(@Valid @RequestBody EmailTemplatePreviewRequest request) {
        return success(emailApplicationService.previewTemplate(request));
    }

    /**
     * 删除指定邮件模板；被业务场景引用时由应用服务拒绝。
     *
     * @param id 邮件模板主键
     * @return 无业务数据的成功响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("email:template:remove")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.DELETE, operation = "删除邮件模板")
    public CommonResult<Void> deleteTemplate(@PathVariable("id") Long id) {
        emailApplicationService.deleteTemplate(id);
        return success();
    }
}
