package com.scott.payment.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 币种管理控制器 — 提供 ISO 4217 币种的增删改查分页接口
 */
@RestController
@RequestMapping("/admin/base/currencies")
public class AdminBaseCurrencyController {

    private final IsoCurrencyMapper isoCurrencyMapper;

    public AdminBaseCurrencyController(IsoCurrencyMapper isoCurrencyMapper) {
        this.isoCurrencyMapper = isoCurrencyMapper;
    }

    /** 分页查询币种列表。 */
    @GetMapping("/list")
    @RequiresPermission("base:currency:list")
    public CommonResult<PageResult<IsoCurrencyDO>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status) {

        LambdaQueryWrapper<IsoCurrencyDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(wrapper -> wrapper
                    .like(IsoCurrencyDO::getAlpha3Code, keyword.trim())
                    .or().like(IsoCurrencyDO::getNumericCode, keyword.trim())
                    .or().like(IsoCurrencyDO::getEnglishName, keyword.trim())
                    .or().like(IsoCurrencyDO::getChineseName, keyword.trim())
            );
        }
        w.eq(status != null, IsoCurrencyDO::getStatus, status);
        w.eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED);
        w.orderByAsc(IsoCurrencyDO::getAlpha3Code);

        Page<IsoCurrencyDO> page = isoCurrencyMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return success(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
    }

    /** 查询单条币种详情。 */
    @GetMapping("/{id}")
    @RequiresPermission("base:currency:query")
    public CommonResult<IsoCurrencyDO> detail(@PathVariable("id") Long id) {
        return success(isoCurrencyMapper.selectById(id));
    }

    /** 导出币种列表。 */
    @GetMapping("/export")
    @RequiresPermission("base:currency:export")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.EXPORT, operation = "导出币种")
    public CommonResult<List<IsoCurrencyDO>> export() {
        return success(isoCurrencyMapper.selectList(
                new LambdaQueryWrapper<IsoCurrencyDO>()
                        .eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(IsoCurrencyDO::getAlpha3Code)
        ));
    }

    /** 新增币种。 */
    @PostMapping
    @RequiresPermission("base:currency:add")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.CREATE, operation = "新增币种")
    public CommonResult<IsoCurrencyDO> create(@RequestBody IsoCurrencyDO row) {
        row.setId(null);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setDeleted(0);
        if (row.getStatus() == null) row.setStatus(1);
        isoCurrencyMapper.insert(row);
        return success(row);
    }

    /** 修改币种。 */
    @PutMapping("/{id}")
    @RequiresPermission("base:currency:edit")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑币种")
    public CommonResult<IsoCurrencyDO> update(@PathVariable("id") Long id, @RequestBody IsoCurrencyDO input) {
        IsoCurrencyDO row = isoCurrencyMapper.selectById(id);
        if (row == null) {
            return CommonResult.error(com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "currency not found");
        }
        mergeCurrency(row, input);
        row.setUpdatedAt(LocalDateTime.now());
        isoCurrencyMapper.updateById(row);
        return success(row);
    }

    /** 切换币种状态。 */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:currency:changeStatus")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "切换币种状态")
    public CommonResult<IsoCurrencyDO> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        IsoCurrencyDO row = isoCurrencyMapper.selectById(id);
        if (row == null) {
            return CommonResult.error(com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "currency not found");
        }
        row.setStatus(body.get("status"));
        row.setUpdatedAt(LocalDateTime.now());
        isoCurrencyMapper.updateById(row);
        return success(row);
    }

    /** 逻辑删除币种。 */
    @DeleteMapping("/{id}")
    @RequiresPermission("base:currency:remove")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.DELETE, operation = "删除币种")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        IsoCurrencyDO row = isoCurrencyMapper.selectById(id);
        if (row != null) {
            row.setDeleted(1);
            row.setUpdatedAt(LocalDateTime.now());
            isoCurrencyMapper.updateById(row);
        }
        return success(null);
    }

    private void mergeCurrency(IsoCurrencyDO row, IsoCurrencyDO input) {
        if (input.getAlpha3Code() != null) row.setAlpha3Code(input.getAlpha3Code());
        if (input.getNumericCode() != null) row.setNumericCode(input.getNumericCode());
        if (input.getEnglishName() != null) row.setEnglishName(input.getEnglishName());
        if (input.getChineseName() != null) row.setChineseName(input.getChineseName());
        if (input.getCurrencySymbol() != null) row.setCurrencySymbol(input.getCurrencySymbol());
        if (input.getFractionDigits() != null) row.setFractionDigits(input.getFractionDigits());
        if (input.getMinorUnitMultiplier() != null) row.setMinorUnitMultiplier(input.getMinorUnitMultiplier());
        if (input.getMinimumAmount() != null) row.setMinimumAmount(input.getMinimumAmount());
        if (input.getStatus() != null) row.setStatus(input.getStatus());
    }
}
