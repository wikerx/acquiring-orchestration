package com.scott.payment.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 地区币种配置控制器 — 管理国家/地区与币种的关联关系
 */
@RestController
@RequestMapping("/admin/base/region-currencies")
public class AdminBaseRegionCurrencyController {

    private final IsoCountryMapper isoCountryMapper;
    private final IsoCurrencyMapper isoCurrencyMapper;

    public AdminBaseRegionCurrencyController(IsoCountryMapper isoCountryMapper, IsoCurrencyMapper isoCurrencyMapper) {
        this.isoCountryMapper = isoCountryMapper;
        this.isoCurrencyMapper = isoCurrencyMapper;
    }

    /** 分页查询国家/地区与币种的关联列表。 */
    @GetMapping("/list")
    @RequiresPermission("base:countryCurrency:list")
    public CommonResult<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "continentCode", required = false) String continentCode) {

        // 预加载币种映射
        Map<String, IsoCurrencyDO> currencyMap = new LinkedHashMap<>();
        isoCurrencyMapper.selectList(new LambdaQueryWrapper<IsoCurrencyDO>()
                        .eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(IsoCurrencyDO::getStatus, 1))
                .forEach(c -> currencyMap.put(c.getAlpha3Code(), c));

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
        w.eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED);
        w.orderByAsc(IsoCountryDO::getAlpha2Code);

        Page<IsoCountryDO> page = isoCountryMapper.selectPage(new Page<>(pageNo, pageSize), w);

        List<Map<String, Object>> records = page.getRecords().stream().map(country -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", country.getId());
            row.put("alpha2Code", country.getAlpha2Code());
            row.put("alpha3Code", country.getAlpha3Code());
            row.put("countryName", country.getChineseName());
            row.put("countryEnglishName", country.getEnglishName());
            row.put("continentCode", country.getContinentCode());
            row.put("continentName", country.getContinentName());
            row.put("currencyAlpha3Code", country.getCurrencyAlpha3Code());
            IsoCurrencyDO currency = currencyMap.get(country.getCurrencyAlpha3Code());
            row.put("currencyName", currency != null ? currency.getChineseName() : "-");
            row.put("currencySymbol", currency != null ? currency.getCurrencySymbol() : "");
            row.put("status", country.getStatus());
            return row;
        }).toList();

        return success(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records));
    }

    /** 查询国家/地区与币种关联详情。 */
    @GetMapping("/{id}")
    @RequiresPermission("base:countryCurrency:query")
    public CommonResult<Map<String, Object>> detail(@PathVariable("id") Long id) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        Map<String, IsoCurrencyDO> currencyMap = new LinkedHashMap<>();
        isoCurrencyMapper.selectList(new LambdaQueryWrapper<IsoCurrencyDO>()
                        .eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED))
                .forEach(c -> currencyMap.put(c.getAlpha3Code(), c));
        return success(toRegionCurrencyRow(country, currencyMap));
    }

    /** 导出国家/地区与币种关联列表。 */
    @GetMapping("/export")
    @RequiresPermission("base:countryCurrency:export")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.EXPORT, operation = "导出地区币种配置")
    public CommonResult<List<Map<String, Object>>> export() {
        Map<String, IsoCurrencyDO> currencyMap = new LinkedHashMap<>();
        isoCurrencyMapper.selectList(new LambdaQueryWrapper<IsoCurrencyDO>()
                        .eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED))
                .forEach(c -> currencyMap.put(c.getAlpha3Code(), c));
        return success(isoCountryMapper.selectList(new LambdaQueryWrapper<IsoCountryDO>()
                        .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(IsoCountryDO::getAlpha2Code))
                .stream()
                .map(country -> toRegionCurrencyRow(country, currencyMap))
                .toList());
    }

    /** 新增国家/地区默认币种关联。 */
    @PostMapping
    @RequiresPermission("base:countryCurrency:add")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.CREATE, operation = "新增地区币种配置")
    public CommonResult<Void> createCurrency(@RequestBody Map<String, String> body) {
        return updateCountryCurrency(Long.valueOf(body.get("countryId")), body.get("currencyAlpha3Code"));
    }

    /** 更新国家/地区的默认币种。 */
    @PutMapping("/{id}")
    @RequiresPermission("base:countryCurrency:edit")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.UPDATE, operation = "更新地区币种")
    public CommonResult<Void> updateCurrency(@PathVariable("id") Long id,
                                              @RequestBody Map<String, String> body) {
        return updateCountryCurrency(id, body.get("currencyAlpha3Code"));
    }

    /** 删除国家/地区默认币种关联。 */
    @DeleteMapping("/{id}")
    @RequiresPermission("base:countryCurrency:remove")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.DELETE, operation = "删除地区币种配置")
    public CommonResult<Void> removeCurrency(@PathVariable("id") Long id) {
        return updateCountryCurrency(id, "");
    }

    /** 切换国家/地区关联状态。 */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:countryCurrency:changeStatus")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.UPDATE, operation = "切换地区币种配置状态")
    public CommonResult<Void> changeStatus(@PathVariable("id") Long id,
                                           @RequestBody Map<String, Integer> body) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        country.setStatus(body.get("status"));
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        return success(null);
    }

    private CommonResult<Void> updateCountryCurrency(Long id, String currencyCode) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        country.setCurrencyAlpha3Code(StringUtils.hasText(currencyCode) ? currencyCode.trim() : "");
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        return success(null);
    }

    private Map<String, Object> toRegionCurrencyRow(IsoCountryDO country, Map<String, IsoCurrencyDO> currencyMap) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", country.getId());
        row.put("alpha2Code", country.getAlpha2Code());
        row.put("alpha3Code", country.getAlpha3Code());
        row.put("countryName", country.getChineseName());
        row.put("countryEnglishName", country.getEnglishName());
        row.put("continentCode", country.getContinentCode());
        row.put("continentName", country.getContinentName());
        row.put("currencyAlpha3Code", country.getCurrencyAlpha3Code());
        IsoCurrencyDO currency = currencyMap.get(country.getCurrencyAlpha3Code());
        row.put("currencyName", currency != null ? currency.getChineseName() : "-");
        row.put("currencySymbol", currency != null ? currency.getCurrencySymbol() : "");
        row.put("status", country.getStatus());
        return row;
    }
}
