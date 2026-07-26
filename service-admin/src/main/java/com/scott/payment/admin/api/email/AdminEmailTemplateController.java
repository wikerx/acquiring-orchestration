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
 * @description : AdminEmailTemplateController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminEmailTemplateController {

    /**
     * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private final AdminEmailApplicationService emailApplicationService;

    /**
     * 创建 AdminEmailTemplateController 实例并注入其运行所需依赖。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param emailApplicationService email Application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminEmailTemplateController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("email:template:list")
    /**
     * 完成 page Templates 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<EmailTemplateResponse>> pageTemplates(@RequestBody(required = false) EmailTemplateQuery query) {
        return success(emailApplicationService.pageTemplates(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("email:template:detail")
    /**
     * 完成 get Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<EmailTemplateResponse> getTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.getTemplate(id));
    }

    @PostMapping
    @RequiresPermission("email:template:add")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "新增邮件模板")
    /**
     * 完成 create Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<EmailTemplateResponse> createTemplate(@Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("email:template:edit")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "修改邮件模板")
/**
 * 写入或更新 update Template 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param id id 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<EmailTemplateResponse> updateTemplate(@PathVariable("id") Long id,
                                                              @Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.updateTemplate(id, request));
    }

    @PostMapping("/{id}/copy")
    @RequiresPermission("email:template:copy")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "复制邮件模板")
    /**
     * 完成 copy Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<EmailTemplateResponse> copyTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.copyTemplate(id));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("email:template:status")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "切换邮件模板状态")
/**
 * 写入或更新 update Template Status 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param id id 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<EmailTemplateResponse> updateTemplateStatus(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody EmailStatusRequest request) {
        return success(emailApplicationService.updateTemplateStatus(id, request.getStatus()));
    }

    @PostMapping("/preview")
    @RequiresPermission("email:template:preview")
    /**
     * 完成 preview Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<EmailTemplatePreviewResponse> previewTemplate(@Valid @RequestBody EmailTemplatePreviewRequest request) {
        return success(emailApplicationService.previewTemplate(request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("email:template:remove")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.DELETE, operation = "删除邮件模板")
    /**
     * 完成 delete Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> deleteTemplate(@PathVariable("id") Long id) {
        emailApplicationService.deleteTemplate(id);
        return success();
    }
}
