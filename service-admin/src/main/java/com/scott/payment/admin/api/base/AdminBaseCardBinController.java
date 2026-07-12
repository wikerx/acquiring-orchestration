package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseCardBinApplicationService;
import com.scott.payment.admin.dto.base.CardBinDTOs;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseCardBinController
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 卡 BIN 库管理接口，位于 service-admin 接口层，负责权限控制、操作日志和 HTTP 请求映射。
 * @status : create
 */
@RestController
@RequestMapping("/admin/base/card-bin")
public class AdminBaseCardBinController {

    /**
     * 卡 BIN 管理应用服务。
     */
    private final AdminBaseCardBinApplicationService cardBinApplicationService;

    /**
     * 创建卡 BIN 管理控制器。
     *
     * @param cardBinApplicationService 卡 BIN 管理应用服务
     */
    public AdminBaseCardBinController(AdminBaseCardBinApplicationService cardBinApplicationService) {
        this.cardBinApplicationService = cardBinApplicationService;
    }

    /**
     * 分页查询卡 BIN 区间。
     *
     * @param request 查询请求
     * @return 卡 BIN 分页数据
     */
    @PostMapping("/page")
    @RequiresPermission("base:cardBin:list")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询卡BIN库")
    public CommonResult<PageResult<CardBinDTOs.CardBinResponse>> page(@RequestBody(required = false) CardBinDTOs.CardBinQueryRequest request) {
        return success(cardBinApplicationService.page(request));
    }

    /**
     * 查询卡 BIN 详情。
     *
     * @param id 主键 ID
     * @return 卡 BIN 详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("base:cardBin:query")
    public CommonResult<CardBinDTOs.CardBinResponse> detail(@PathVariable("id") Long id) {
        return success(cardBinApplicationService.detail(id));
    }

    /**
     * 新增卡 BIN 区间。
     *
     * @param request 保存请求
     * @return 保存后的卡 BIN 数据
     */
    @PostMapping
    @RequiresPermission("base:cardBin:add")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.CREATE, operation = "新增卡BIN")
    public CommonResult<CardBinDTOs.CardBinResponse> create(@Valid @RequestBody CardBinDTOs.CardBinSaveRequest request) {
        return success(cardBinApplicationService.create(request));
    }

    /**
     * 修改卡 BIN 区间。
     *
     * @param id 主键 ID
     * @param request 保存请求
     * @return 更新后的卡 BIN 数据
     */
    @PutMapping("/{id}")
    @RequiresPermission("base:cardBin:edit")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑卡BIN")
    public CommonResult<CardBinDTOs.CardBinResponse> update(@PathVariable("id") Long id,
                                                            @Valid @RequestBody CardBinDTOs.CardBinSaveRequest request) {
        return success(cardBinApplicationService.update(id, request));
    }

    /**
     * 删除卡 BIN 区间。
     *
     * @param id 主键 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("base:cardBin:remove")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.DELETE, operation = "删除卡BIN")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        cardBinApplicationService.remove(id);
        return success();
    }

    /**
     * 更新卡 BIN 状态。
     *
     * @param id 主键 ID
     * @param request 状态请求
     * @return 更新后的卡 BIN 数据
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:cardBin:status")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.UPDATE, operation = "更新卡BIN状态")
    public CommonResult<CardBinDTOs.CardBinResponse> updateStatus(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody CardBinDTOs.CardBinStatusRequest request) {
        return success(cardBinApplicationService.updateStatus(id, request));
    }

    /**
     * 执行卡 BIN 匹配测试。
     *
     * @param request 匹配测试请求
     * @return 匹配结果
     */
    @PostMapping("/match")
    @RequiresPermission("base:cardBin:match")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.QUERY, operation = "卡BIN匹配测试")
    public CommonResult<CardBinDTOs.CardBinMatchResponse> match(@Valid @RequestBody CardBinDTOs.CardBinMatchRequest request) {
        return success(cardBinApplicationService.match(request));
    }

    /**
     * 查询卡 BIN 导入批次。
     *
     * @param request 分页请求
     * @return 导入批次分页数据
     */
    @PostMapping("/import-batches")
    @RequiresPermission("base:cardBin:list")
    public CommonResult<PageResult<CardBinDTOs.CardBinImportBatchResponse>> importBatches(@RequestBody(required = false) CardBinDTOs.CardBinQueryRequest request) {
        return success(cardBinApplicationService.importBatches(request));
    }

    /**
     * 从旧表初始化导入卡 BIN。
     *
     * @return 初始化批次结果
     */
    @PostMapping("/init-from-legacy-db")
    @RequiresPermission("base:cardBin:init")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.UPDATE, operation = "初始化旧库卡BIN数据")
    public CommonResult<CardBinDTOs.CardBinImportBatchResponse> initFromLegacyDb() {
        return success(cardBinApplicationService.initFromLegacyDb());
    }

    /**
     * 导出卡 BIN 区间。
     *
     * @param request 查询请求
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("base:cardBin:export")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.EXPORT, operation = "导出卡BIN库")
    public void export(@RequestBody(required = false) CardBinDTOs.CardBinQueryRequest request,
                       HttpServletResponse response) {
        cardBinApplicationService.export(request, response);
    }

    /**
     * 查询页面下拉选项。
     *
     * @return 页面下拉选项
     */
    @GetMapping("/options")
    @RequiresPermission("base:cardBin:list")
    public CommonResult<CardBinDTOs.CardBinOptionsResponse> options() {
        return success(cardBinApplicationService.options());
    }
}
