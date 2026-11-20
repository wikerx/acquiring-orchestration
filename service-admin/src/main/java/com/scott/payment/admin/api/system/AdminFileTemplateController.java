package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminFileTemplateApplicationService;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.CreateRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Detail;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Download;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.FormOptions;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.PublishRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Query;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Summary;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.UpdateRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Version;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.template.ManagedFileTemplateEndpoint;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 系统管理 - 模板中心接口。 */
@RestController
@RequestMapping("/admin/system/file-templates")
public class AdminFileTemplateController {

    private final AdminFileTemplateApplicationService applicationService;

    public AdminFileTemplateController(AdminFileTemplateApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 以模板列表权限分页查询定义及发布摘要，不返回私有存储键。 */
    @PostMapping("/search")
    @RequiresPermission("system:file-template:list")
    public CommonResult<PageResult<Summary>> page(@Valid @RequestBody(required = false) Query request) {
        return success(applicationService.page(request));
    }

    /** 查询新增和编辑表单使用的受控模块及有效权限。 */
    @GetMapping("/options")
    @RequiresPermission("system:file-template:list")
    public CommonResult<FormOptions> options() {
        return success(applicationService.formOptions());
    }

    /** 以详情权限读取模板定义与版本历史，正文通过受控下载入口读取。 */
    @GetMapping("/{id:\\d+}")
    @RequiresPermission("system:file-template:detail")
    public CommonResult<Detail> detail(@PathVariable("id") Long id) {
        return success(applicationService.detail(id));
    }

    /** 校验新增权限和模板元数据，建立稳定模板编码供业务引用。 */
    @PostMapping
    @RequiresPermission("system:file-template:add")
    @OperationLog(moduleName = "模板中心", businessType = OperationTypeConstants.CREATE, operation = "新增模板定义")
    public CommonResult<Detail> create(@Valid @RequestBody CreateRequest request) {
        return success(applicationService.create(request));
    }

    /** 按编辑权限和请求版本修改定义，已发布资产保持不可变。 */
    @PutMapping("/{id:\\d+}")
    @RequiresPermission("system:file-template:edit")
    @OperationLog(moduleName = "模板中心", businessType = OperationTypeConstants.UPDATE, operation = "修改模板定义")
    public CommonResult<Detail> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody UpdateRequest request) {
        return success(applicationService.update(id, request));
    }

    /** 按上传权限接收中英文文件和幂等请求号，统一校验后生成新的不可变版本。 */
    @PostMapping(value = "/{id:\\d+}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("system:file-template:upload")
    @OperationLog(moduleName = "模板中心", businessType = OperationTypeConstants.CREATE, operation = "上传模板版本")
    public CommonResult<Version> uploadVersion(@PathVariable("id") Long id,
                                               @RequestParam("versionNo") Integer versionNo,
                                               @RequestParam(value = "schemaVersion", required = false) String schemaVersion,
                                               @RequestParam("requestId") String requestId,
                                               @RequestParam(value = "releaseNote", required = false) String releaseNote,
                                               @RequestParam("zhFile") MultipartFile zhFile,
                                               @RequestParam("enFile") MultipartFile enFile) {
        return success(applicationService.uploadVersion(id, versionNo, schemaVersion, requestId,
                releaseNote, zhFile, enFile));
    }

    /** 按发布权限和预期版本切换当前指针，禁止覆盖旧版本正文。 */
    @PostMapping("/{id:\\d+}/versions/{versionId:\\d+}/publish")
    @RequiresPermission("system:file-template:publish")
    @OperationLog(moduleName = "模板中心", businessType = OperationTypeConstants.UPDATE, operation = "发布模板版本")
    public CommonResult<Detail> publish(@PathVariable("id") Long id,
                                        @PathVariable("versionId") Long versionId,
                                        @Valid @RequestBody PublishRequest request) {
        return success(applicationService.publish(id, versionId, request));
    }

    /** 以回滚权限将指针切回可用历史版本，保留全部版本与操作审计。 */
    @PostMapping("/{id:\\d+}/versions/{versionId:\\d+}/rollback")
    @RequiresPermission("system:file-template:rollback")
    @OperationLog(moduleName = "模板中心", businessType = OperationTypeConstants.UPDATE, operation = "回滚模板版本")
    public CommonResult<Detail> rollback(@PathVariable("id") Long id,
                                         @PathVariable("versionId") Long versionId,
                                         @Valid @RequestBody PublishRequest request) {
        return success(applicationService.rollback(id, versionId, request));
    }

    /** 下载当前发布版本，不返回对象存储直链。 */
    @GetMapping("/code/{templateCode}/download")
    @RequiresPermission("system:file-template:download")
    @ManagedFileTemplateEndpoint("DYNAMIC")
    public ResponseEntity<byte[]> download(@PathVariable("templateCode") String templateCode,
                                           @RequestParam(value = "locale", required = false) String locale) {
        Download file = applicationService.downloadCurrent(templateCode, locale);
        String encodedFilename = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .body(file.content());
    }
}
