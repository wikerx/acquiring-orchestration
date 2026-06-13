package com.scott.payment.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
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
 * 国家/地区管理控制器 — 提供 ISO 3166 国家地区的增删改查分页接口
 */
@RestController
@RequestMapping("/admin/base/countries")
public class AdminBaseCountryController {

    private final IsoCountryMapper isoCountryMapper;

    public AdminBaseCountryController(IsoCountryMapper isoCountryMapper) {
        this.isoCountryMapper = isoCountryMapper;
    }

    /**
     * 分页查询国家/地区列表，支持按关键字、大洲筛选。
     */
    @GetMapping("/list")
    @RequiresPermission("base:country:list")
    public CommonResult<PageResult<IsoCountryDO>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "continentCode", required = false) String continentCode,
            @RequestParam(value = "status", required = false) Integer status) {

        LambdaQueryWrapper<IsoCountryDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(wrapper -> wrapper
                    .like(IsoCountryDO::getAlpha2Code, keyword.trim())
                    .or().like(IsoCountryDO::getAlpha3Code, keyword.trim())
                    .or().like(IsoCountryDO::getEnglishName, keyword.trim())
                    .or().like(IsoCountryDO::getChineseName, keyword.trim())
            );
        }
        w.eq(StringUtils.hasText(continentCode), IsoCountryDO::getContinentCode, continentCode);
        w.eq(status != null, IsoCountryDO::getStatus, status);
        w.eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED);
        w.orderByAsc(IsoCountryDO::getAlpha2Code);

        Page<IsoCountryDO> page = isoCountryMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return success(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
    }

    /** 查询单条国家/地区详情。 */
    @GetMapping("/{id}")
    @RequiresPermission("base:country:query")
    public CommonResult<IsoCountryDO> detail(@PathVariable("id") Long id) {
        return success(isoCountryMapper.selectById(id));
    }

    /** 导出国家/地区列表。 */
    @GetMapping("/export")
    @RequiresPermission("base:country:export")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.EXPORT, operation = "导出国家地区")
    public CommonResult<List<IsoCountryDO>> export() {
        return success(isoCountryMapper.selectList(
                new LambdaQueryWrapper<IsoCountryDO>()
                        .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(IsoCountryDO::getAlpha2Code)
        ));
    }

    /** 新增国家/地区。 */
    @PostMapping
    @RequiresPermission("base:country:add")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.CREATE, operation = "新增国家地区")
    public CommonResult<IsoCountryDO> create(@RequestBody IsoCountryDO row) {
        row.setId(null);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setDeleted(0);
        if (row.getStatus() == null) row.setStatus(1);
        isoCountryMapper.insert(row);
        return success(row);
    }

    /** 修改国家/地区。 */
    @PutMapping("/{id}")
    @RequiresPermission("base:country:edit")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "编辑国家地区")
    public CommonResult<IsoCountryDO> update(@PathVariable("id") Long id, @RequestBody IsoCountryDO input) {
        IsoCountryDO row = isoCountryMapper.selectById(id);
        if (row == null) {
            return CommonResult.error(com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        mergeCountry(row, input);
        row.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(row);
        return success(row);
    }

    /** 切换国家/地区状态。 */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:country:changeStatus")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "切换国家地区状态")
    public CommonResult<IsoCountryDO> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        IsoCountryDO row = isoCountryMapper.selectById(id);
        if (row == null) {
            return CommonResult.error(com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        row.setStatus(body.get("status"));
        row.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(row);
        return success(row);
    }

    /** 逻辑删除国家/地区。 */
    @DeleteMapping("/{id}")
    @RequiresPermission("base:country:remove")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.DELETE, operation = "删除国家地区")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        IsoCountryDO row = isoCountryMapper.selectById(id);
        if (row != null) {
            row.setDeleted(1);
            row.setUpdatedAt(LocalDateTime.now());
            isoCountryMapper.updateById(row);
        }
        return success(null);
    }

    private void mergeCountry(IsoCountryDO row, IsoCountryDO input) {
        if (input.getContinentCode() != null) row.setContinentCode(input.getContinentCode());
        if (input.getContinentName() != null) row.setContinentName(input.getContinentName());
        if (input.getAlpha2Code() != null) row.setAlpha2Code(input.getAlpha2Code());
        if (input.getAlpha3Code() != null) row.setAlpha3Code(input.getAlpha3Code());
        if (input.getNumericCode() != null) row.setNumericCode(input.getNumericCode());
        if (input.getEnglishName() != null) row.setEnglishName(input.getEnglishName());
        if (input.getShortEnglishName() != null) row.setShortEnglishName(input.getShortEnglishName());
        if (input.getChineseName() != null) row.setChineseName(input.getChineseName());
        if (input.getFlagEmoji() != null) row.setFlagEmoji(input.getFlagEmoji());
        if (input.getPrimaryLanguageCode() != null) row.setPrimaryLanguageCode(input.getPrimaryLanguageCode());
        if (input.getPrimaryLanguageEnglish() != null) row.setPrimaryLanguageEnglish(input.getPrimaryLanguageEnglish());
        if (input.getPrimaryLanguageChinese() != null) row.setPrimaryLanguageChinese(input.getPrimaryLanguageChinese());
        if (input.getCurrencyAlpha3Code() != null) row.setCurrencyAlpha3Code(input.getCurrencyAlpha3Code());
        if (input.getStatus() != null) row.setStatus(input.getStatus());
    }
}
