package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.security.openapi.OpenApiKeyCopyResponse;
import com.scott.payment.component.security.openapi.OpenApiKeyDownloadFile;
import com.scott.payment.component.security.openapi.OpenApiKeyExportFormat;
import com.scott.payment.component.security.openapi.OpenApiKeyExportRequest;
import com.scott.payment.component.security.openapi.OpenApiKeyType;
import com.scott.payment.component.security.openapi.OpenApiKeyAuditService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.SysOperLogDTO;
import com.scott.payment.merchant.dto.SysOperLogQueryRequest;
import com.scott.payment.merchant.application.openapi.MerchantOpenApiKeyApplicationService;
import com.scott.payment.merchant.service.MerchantOperLogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/merchant/openapi/keys")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiKeyController
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Merchant Open API Key Controller 控制器，位于 商户后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class MerchantOpenApiKeyController {

    /**
     * OPENAPI KEY MODULE NAME，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String OPENAPI_KEY_MODULE_NAME = "商户OpenAPI密钥";

    /**
     * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final MerchantOpenApiKeyApplicationService keyApplicationService;
    /**
     * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final OpenApiKeyAuditService keyAuditService;
    /**
     * merchant Oper Log Service 依赖，用于 Merchant Open API Key Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantOperLogService merchantOperLogService;

    /**
     * 创建商户端 OpenAPI 密钥管理接口。
     *
     * @param keyApplicationService 密钥材料应用服务
     * @param keyAuditService       密钥审计辅助服务
     * @param merchantOperLogService 商户操作日志服务
     */
    public MerchantOpenApiKeyController(MerchantOpenApiKeyApplicationService keyApplicationService,
                                        OpenApiKeyAuditService keyAuditService,
                                        MerchantOperLogService merchantOperLogService) {
        this.keyApplicationService = keyApplicationService;
        this.keyAuditService = keyAuditService;
        this.merchantOperLogService = merchantOperLogService;
    }

    /**
     * 查询当前商户 OpenAPI 对接材料概要。
     *
     * @return 当前商户对接材料概要
     */
    @GetMapping
    @RequiresPermission("merchant:openapi:key:view")
    public CommonResult<OpenApiMerchantKeyMaterialVO> getMaterial() {
        return success(keyApplicationService.queryMaterial(currentMerchantId()));
    }

    /**
     * 复制当前商户 OpenAPI 接入材料。私钥复制需要单独权限并记录操作日志。
     *
     * @param request 复制请求
     * @return 可复制文本
     */
    @PostMapping("/copy")
    @RequiresPermission("merchant:openapi:key:copy")
    @OperationLog(moduleName = "商户OpenAPI密钥", businessType = OperationTypeConstants.EXPORT, operation = "复制OpenAPI接入材料")
    public CommonResult<OpenApiKeyCopyResponse> copy(@RequestBody OpenApiKeyExportRequest request) {
        requireCopyPermission(request == null ? null : request.getKeyType());
        return success(keyApplicationService.copy(currentMerchantId(), request));
    }

    /**
     * 下载当前商户 OpenAPI 接入材料文件。
     *
     * @param keyType 材料类型
     * @param format  下载格式
     * @return 附件文件
     */
    @GetMapping("/download")
    @RequiresPermission("merchant:openapi:key:download")
    @OperationLog(moduleName = "商户OpenAPI密钥", businessType = OperationTypeConstants.EXPORT, operation = "下载OpenAPI接入材料")
    public ResponseEntity<byte[]> download(@RequestParam("keyType") OpenApiKeyType keyType,
                                           @RequestParam(value = "format", required = false) OpenApiKeyExportFormat format) {
        requireDownloadPermission(keyType);
        return toDownloadResponse(keyApplicationService.download(currentMerchantId(), keyType, format));
    }

    /**
     * 轮换当前商户 OpenAPI 密钥。
     *
     * @param request 轮换请求
     * @return 轮换后的密钥概要
     */
    @PostMapping("/rotate")
    @RequiresPermission("merchant:openapi:key:view")
    @OperationLog(moduleName = "商户OpenAPI密钥", businessType = OperationTypeConstants.UPDATE, operation = "轮换OpenAPI密钥")
    public CommonResult<OpenApiMerchantKeyMaterialVO> rotate(@RequestBody OpenApiKeyExportRequest request) {
        OpenApiKeyType keyType = request == null ? null : request.getKeyType();
        requireRotatePermission(keyType);
        return success(keyApplicationService.rotate(currentMerchantId(), keyType));
    }

    /**
     * 查询当前商户 OpenAPI 密钥操作记录。仅返回脱敏审计字段，不返回请求或响应正文。
     *
     * @param request 查询条件
     * @return 当前商户 OpenAPI 密钥操作记录
     */
    @PostMapping("/logs")
    @RequiresPermission("merchant:openapi:key:log")
    public CommonResult<PageResult<SysOperLogDTO>> logs(@RequestBody(required = false) SysOperLogQueryRequest request) {
        return success(queryLogs(request));
    }

    /**
     * 查询当前商户 OpenAPI 密钥操作记录。兼容前端 GET 分页调用。
     *
     * @param request 查询条件
     * @return 当前商户 OpenAPI 密钥操作记录
     */
    @GetMapping("/logs")
    @RequiresPermission("merchant:openapi:key:log")
    public CommonResult<PageResult<SysOperLogDTO>> getLogs(@ModelAttribute SysOperLogQueryRequest request) {
        return success(queryLogs(request));
    }

    /**
     * 查询当前商户的 OpenAPI 密钥操作日志。
     * <p>
     * 服务端强制覆盖 merchantId 和模块名称，不接受请求参数扩大数据范围。
     * </p>
     *
     * @param request 分页和状态等非租户查询条件
     * @return 当前认证商户的密钥操作日志
     */
    private PageResult<SysOperLogDTO> queryLogs(SysOperLogQueryRequest request) {
        SysOperLogQueryRequest query = request == null ? new SysOperLogQueryRequest() : request;
        query.setMerchantId(currentMerchantId());
        query.setModuleName(OPENAPI_KEY_MODULE_NAME);
        return merchantOperLogService.pageOperLogs(query);
    }

    /**
     * 从内部认证上下文读取当前商户号。
     *
     * @return 已认证商户号
     * @throws ServiceException 上下文不存在或未绑定商户时抛出
     */
    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }

    /**
     * 校验copy权限输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 商户后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param keyType 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    private void requireCopyPermission(OpenApiKeyType keyType) {
        rejectPlatformPrivateKey(keyType);
        if (keyAuditService.isPublicMaterial(keyType)) {
            return;
        }
        requirePermission("merchant:openapi:key:download-private", "缺少敏感材料复制权限");
    }

    /**
     * 校验download权限输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 商户后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param keyType 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    private void requireDownloadPermission(OpenApiKeyType keyType) {
        rejectPlatformPrivateKey(keyType);
        if (keyAuditService.isPublicMaterial(keyType)) {
            return;
        }
        requirePermission("merchant:openapi:key:download-private", "缺少敏感材料下载权限");
    }

    /**
     * 校验rotate权限输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 商户后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param keyType 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    private void requireRotatePermission(OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.JWT_KEY) {
            requirePermission("merchant:openapi:key:rotate-jwt", "缺少 JWT 密钥轮换权限");
            return;
        }
        if (keyType == OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY) {
            requirePermission("merchant:openapi:key:rotate-response", "缺少响应密钥轮换权限");
            return;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户端仅允许轮换 JWT_KEY 或 MERCHANT_RESPONSE_PRIVATE_KEY");
    }

    /**
     * 校验权限输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 商户后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param permission permission 输入值，参与 权限 的查询、校验、转换、写入或日志摘要
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void requirePermission(String permission, String message) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        List<String> permissions = account == null ? List.of() : account.getPermissions();
        if (!permissions.contains("*:*:*") && !permissions.contains(permission)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), message);
        }
    }

    /**
     * 整理拒绝平台私钥密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param keyType 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    private void rejectPlatformPrivateKey(OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.PLATFORM_PRIVATE_KEY) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "商户端不允许查看或导出平台请求解密私钥");
        }
    }

    /**
     * 构造download响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param file file 输入值，参与 file 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ResponseEntity<byte[]> toDownloadResponse(OpenApiKeyDownloadFile file) {
        String encodedFileName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .body(file.getContent());
    }
}
