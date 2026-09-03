package com.scott.payment.admin.application.base;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.base.MccRequests;
import com.scott.payment.admin.dto.base.MccVO;
import com.scott.payment.admin.dto.export.MccCodeExportRow;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.base.MccEntities;
import com.scott.payment.admin.mapper.BaseMccCodeMapper;
import com.scott.payment.admin.mapper.BaseMccLevel1Mapper;
import com.scott.payment.admin.mapper.BaseMccLevel2Mapper;
import com.scott.payment.admin.mapper.BaseMccRiskPolicyMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.mcc.service.MccOptionCacheInvalidator;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseMccApplicationService
 * @date : 2026-06-27 16:49
 * @email : scott_x@163.com
 * @description : admin基础mcc应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class AdminBaseMccApplicationService {

    /**
     * {@code EXPORT_TIME_FORMATTER}常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /**
     * {@code NOT_DELETED}常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * {@code DISABLED}，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int DISABLED = 0;
    /**
     * 一级分类常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String LEVEL1 = "LEVEL1";
    /**
     * 二级分类常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String LEVEL2 = "LEVEL2";
    /**
     * {@code MCC_CODE}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String MCC_CODE = "MCC_CODE";
    /**
     * {@code APPLY_SCOPE_ALL}常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String APPLY_SCOPE_ALL = "ALL";
    /**
     * {@code APPLY_SCOPE_SPECIFIC}常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String APPLY_SCOPE_SPECIFIC = "SPECIFIC";
    /**
     * {@code CARD_BRAND_DICT}常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CARD_BRAND_DICT = "card_brand";
    /**
     * 默认语言区域常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String DEFAULT_LOCALE = "zh-CN";
    /**
     * {@code FOUR_DIGIT_MCC}常量，统一 {@code AdminBaseMccApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String FOUR_DIGIT_MCC = "^[0-9]{4}$";

    private final BaseMccLevel1Mapper level1Mapper;
    private final BaseMccLevel2Mapper level2Mapper;
    private final BaseMccCodeMapper codeMapper;
    private final BaseMccRiskPolicyMapper riskPolicyMapper;
    private final SysDictDataMapper dictDataMapper;
    private final AdminDictService adminDictService;
    private final IsoCountryMapper isoCountryMapper;
    private final BaseMerchantInfoMapper merchantInfoMapper;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /** MCC 公共选项常驻缓存失效器。 */
    private final MccOptionCacheInvalidator mccOptionCacheInvalidator;

    /**
     * 创建 MCC 管理后台应用服务。
     */
    public AdminBaseMccApplicationService(BaseMccLevel1Mapper level1Mapper,
                                          BaseMccLevel2Mapper level2Mapper,
                                          BaseMccCodeMapper codeMapper,
                                          BaseMccRiskPolicyMapper riskPolicyMapper,
                                          SysDictDataMapper dictDataMapper,
                                          AdminDictService adminDictService,
                                          IsoCountryMapper isoCountryMapper,
                                          BaseMerchantInfoMapper merchantInfoMapper,
                                          ExcelExportService excelExportService,
                                          ExcelI18nMessageResolver excelI18nMessageResolver,
                                          ExcelLocaleResolver excelLocaleResolver,
                                          MccOptionCacheInvalidator mccOptionCacheInvalidator) {
        this.level1Mapper = level1Mapper;
        this.level2Mapper = level2Mapper;
        this.codeMapper = codeMapper;
        this.riskPolicyMapper = riskPolicyMapper;
        this.dictDataMapper = dictDataMapper;
        this.adminDictService = adminDictService;
        this.isoCountryMapper = isoCountryMapper;
        this.merchantInfoMapper = merchantInfoMapper;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.mccOptionCacheInvalidator = mccOptionCacheInvalidator;
    }

    /**
     * 查询 MCC 分类和编码树。
     *
     * <p>搜索命中 MCC Code 时会保留对应二级和一级分类，保证页面仍展示完整树路径。</p>
     */
    @DS(DataSourceName.SLAVE)
    public List<MccVO.MccTreeNodeVO> tree(MccRequests.MccTreeQueryRequest request) {
        MccRequests.MccTreeQueryRequest query = request == null ? new MccRequests.MccTreeQueryRequest() : request;
        List<MccEntities.BaseMccLevel1DO> level1Rows = level1Mapper.selectList(baseLevel1Query());
        List<MccEntities.BaseMccLevel2DO> level2Rows = level2Mapper.selectList(baseLevel2Query());
        List<MccEntities.BaseMccCodeDO> codeRows = codeMapper.selectList(baseCodeQuery().orderByAsc(MccEntities.BaseMccCodeDO::getSortNo).orderByAsc(MccEntities.BaseMccCodeDO::getId));

        Map<Long, MccVO.MccTreeNodeVO> level1Nodes = level1Rows.stream()
                .map(this::toLevel1Node)
                .collect(Collectors.toMap(MccVO.MccTreeNodeVO::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, MccVO.MccTreeNodeVO> level2Nodes = level2Rows.stream()
                .map(this::toLevel2Node)
                .collect(Collectors.toMap(MccVO.MccTreeNodeVO::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        level2Rows.forEach(row -> addChild(level1Nodes.get(row.getLevel1Id()), level2Nodes.get(row.getId())));
        codeRows.forEach(row -> addChild(level2Nodes.get(row.getLevel2Id()), toMccCodeNode(row)));

        return level1Nodes.values().stream()
                .map(node -> filterTree(node, query))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MccVO.MccTreeNodeVO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    /**
     * 新增或编辑 MCC 一级、二级分类。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MccVO.MccTreeNodeVO saveCategory(MccRequests.MccCategorySaveRequest request) {
        String nodeType = normalizeRequired(request.getNodeType(), "nodeType is required");
        mccOptionCacheInvalidator.evictOptions();
        return switch (nodeType) {
            case LEVEL1 -> toLevel1Node(saveLevel1(request));
            case LEVEL2 -> toLevel2Node(saveLevel2(request));
            default -> throw badRequest("分类节点只支持 LEVEL1 / LEVEL2");
        };
    }

    /**
     * 删除 MCC 分类。存在下级分类或 MCC Code 时不允许删除。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(MccRequests.MccDeleteRequest request) {
        String nodeType = normalizeRequired(request.getNodeType(), "nodeType is required");
        if (LEVEL1.equals(nodeType)) {
            Long children = level2Mapper.selectCount(baseLevel2Query().eq(MccEntities.BaseMccLevel2DO::getLevel1Id, request.getId()));
            if (children != null && children > 0) {
                throw badRequest("一级分类下存在二级分类，不能删除");
            }
            MccEntities.BaseMccLevel1DO row = getLevel1(request.getId());
            mccOptionCacheInvalidator.evictOptions();
            softDeleteLevel1(row);
            return;
        }
        if (LEVEL2.equals(nodeType)) {
            Long children = codeMapper.selectCount(baseCodeQuery().eq(MccEntities.BaseMccCodeDO::getLevel2Id, request.getId()));
            if (children != null && children > 0) {
                throw badRequest("当前二级分类下存在 MCC 编码，不允许删除");
            }
            MccEntities.BaseMccLevel2DO row = getLevel2(request.getId());
            mccOptionCacheInvalidator.evictOptions();
            softDeleteLevel2(row);
            return;
        }
        throw badRequest("分类节点只支持 LEVEL1 / LEVEL2");
    }

    /**
     * 更新分类或 MCC 编码状态。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(MccRequests.MccStatusUpdateRequest request) {
        int status = validStatus(request.getStatus());
        String nodeType = normalizeRequired(request.getNodeType(), "nodeType is required");
        LocalDateTime now = LocalDateTime.now();
        mccOptionCacheInvalidator.evictOptions();
        if (LEVEL1.equals(nodeType)) {
            MccEntities.BaseMccLevel1DO row = getLevel1(request.getId());
            row.setStatus(status);
            row.setUpdateTime(now);
            level1Mapper.updateById(row);
        } else if (LEVEL2.equals(nodeType)) {
            MccEntities.BaseMccLevel2DO row = getLevel2(request.getId());
            row.setStatus(status);
            row.setUpdateTime(now);
            level2Mapper.updateById(row);
        } else if (MCC_CODE.equals(nodeType)) {
            MccEntities.BaseMccCodeDO row = getCodeById(request.getId());
            row.setStatus(status);
            row.setUpdateTime(now);
            codeMapper.updateById(row);
        } else {
            throw badRequest("unsupported nodeType: " + nodeType);
        }
    }

    /**
     * 新增 MCC 编码。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MccVO.MccCodeVO createCode(MccRequests.MccCodeSaveRequest request) {
        validateCodeRequest(request, true);
        if (existsMccCode(request.getMccCode(), null)) {
            throw badRequest("MCC 编码已存在");
        }
        mccOptionCacheInvalidator.evictOptions();
        MccEntities.BaseMccCodeDO row = new MccEntities.BaseMccCodeDO();
        fillCode(row, request);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(row.getCreateTime());
        row.setDeleted(NOT_DELETED);
        codeMapper.insert(row);
        return toCodeVO(row);
    }

    /**
     * 编辑 MCC 编码。编码本身创建后不允许修改，避免破坏风险策略和商户资料引用。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MccVO.MccCodeVO updateCode(MccRequests.MccCodeSaveRequest request) {
        if (request.getId() == null) {
            throw badRequest("id is required");
        }
        MccEntities.BaseMccCodeDO row = getCodeById(request.getId());
        if (StringUtils.hasText(request.getMccCode()) && !row.getMccCode().equals(request.getMccCode().trim())) {
            throw badRequest("编辑 MCC 时不允许修改 MCC 编码");
        }
        validateCodeRequest(request, false);
        mccOptionCacheInvalidator.evictOptions();
        fillCode(row, request);
        row.setUpdateTime(LocalDateTime.now());
        codeMapper.updateById(row);
        return toCodeVO(row);
    }

    /**
     * 查询 MCC 编码详情。
     */
    @DS(DataSourceName.SLAVE)
    public MccVO.MccCodeVO getCode(Long id) {
        return toCodeVO(getCodeById(id));
    }

    /**
     * 删除 MCC 编码。存在风险策略或商户资料引用时不允许删除。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteCode(MccRequests.MccDeleteRequest request) {
        MccEntities.BaseMccCodeDO row = getCodeById(request.getId());
        Long policyCount = riskPolicyMapper.selectCount(basePolicyQuery()
                .eq(MccEntities.BaseMccRiskPolicyDO::getMccCode, row.getMccCode()));
        if (policyCount != null && policyCount > 0) {
            throw badRequest("当前 MCC 编码已被风险策略或商户信息引用，不允许删除");
        }
        Long merchantCount = merchantInfoMapper.selectCount(new QueryWrapper<BaseMerchantInfoDO>()
                .eq("deleted", 0)
                .eq("mcc_code", row.getMccCode()));
        if (merchantCount != null && merchantCount > 0) {
            throw badRequest("当前 MCC 编码已被风险策略或商户信息引用，不允许删除");
        }
        mccOptionCacheInvalidator.evictOptions();
        row.setStatus(DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        codeMapper.updateById(row);
    }

    /**
     * 分页查询 MCC 风险策略。
     */
    @DS(DataSourceName.SLAVE)
    public PageResult<MccVO.MccRiskPolicyVO> pagePolicies(MccRequests.MccRiskPolicyQueryRequest request) {
        MccRequests.MccRiskPolicyQueryRequest query = request == null ? new MccRequests.MccRiskPolicyQueryRequest() : request;
        Page<MccEntities.BaseMccRiskPolicyDO> page = riskPolicyMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildPolicyQuery(query)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toPolicyVO).toList());
    }

    /**
     * 新增 MCC 风险策略。card_brand 不允许保存 ALL，页面“所有卡品牌”会展开为真实卡品牌。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<MccVO.MccRiskPolicyVO> createPolicies(MccRequests.MccRiskPolicySaveRequest request) {
        List<String> cardSchemes = resolveCardSchemes(request);
        validatePolicyBase(request);
        LocalDateTime now = LocalDateTime.now();
        List<MccVO.MccRiskPolicyVO> result = new ArrayList<>();
        for (String cardScheme : cardSchemes) {
            assertPolicyNotExists(null, request, cardScheme);
            MccEntities.BaseMccRiskPolicyDO row = new MccEntities.BaseMccRiskPolicyDO();
            fillPolicy(row, request, cardScheme);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            row.setDeleted(NOT_DELETED);
            riskPolicyMapper.insert(row);
            result.add(toPolicyVO(row));
        }
        return result;
    }

    /**
     * 编辑单条 MCC 风险策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public MccVO.MccRiskPolicyVO updatePolicy(MccRequests.MccRiskPolicySaveRequest request) {
        if (request.getId() == null) {
            throw badRequest("id is required");
        }
        MccEntities.BaseMccRiskPolicyDO row = getPolicy(request.getId());
        List<String> cardSchemes = resolveCardSchemes(request);
        if (cardSchemes.size() != 1) {
            throw badRequest("编辑风险策略时只能选择一个真实卡品牌");
        }
        validatePolicyBase(request);
        String cardScheme = cardSchemes.get(0);
        assertPolicyNotExists(request.getId(), request, cardScheme);
        fillPolicy(row, request, cardScheme);
        row.setUpdateTime(LocalDateTime.now());
        riskPolicyMapper.updateById(row);
        return toPolicyVO(row);
    }

    /**
     * 查询风险策略详情。
     */
    @DS(DataSourceName.SLAVE)
    public MccVO.MccRiskPolicyVO getPolicyDetail(Long id) {
        return toPolicyVO(getPolicy(id));
    }

    /**
     * 更新风险策略状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePolicyStatus(MccRequests.MccStatusUpdateRequest request) {
        MccEntities.BaseMccRiskPolicyDO row = getPolicy(request.getId());
        int status = validStatus(request.getStatus());
        row.setStatus(status);
        row.setPolicyStatus(status);
        row.setUpdateTime(LocalDateTime.now());
        riskPolicyMapper.updateById(row);
    }

    /**
     * 删除风险策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePolicy(MccRequests.MccDeleteRequest request) {
        MccEntities.BaseMccRiskPolicyDO row = getPolicy(request.getId());
        row.setStatus(DISABLED);
        row.setPolicyStatus(DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        riskPolicyMapper.updateById(row);
    }

    /**
     * 查询 MCC 概览统计。
     */
    @DS(DataSourceName.SLAVE)
    public MccVO.MccOverviewVO overview() {
        MccVO.MccOverviewVO overview = new MccVO.MccOverviewVO();
        overview.setLevel1Count(nonNullCount(level1Mapper.selectCount(baseLevel1Query())));
        overview.setLevel2Count(nonNullCount(level2Mapper.selectCount(baseLevel2Query())));
        overview.setMccCodeCount(nonNullCount(codeMapper.selectCount(baseCodeQuery())));
        overview.setEnabledMccCodeCount(nonNullCount(codeMapper.selectCount(baseCodeQuery().eq(MccEntities.BaseMccCodeDO::getStatus, ENABLED))));
        overview.setRiskPolicyCount(nonNullCount(riskPolicyMapper.selectCount(basePolicyQuery())));
        overview.setHighRiskPolicyCount(nonNullCount(riskPolicyMapper.selectCount(basePolicyQuery()
                .in(MccEntities.BaseMccRiskPolicyDO::getRiskLevel, List.of("HIGH", "PROHIBITED")))));
        return overview;
    }

    /**
     * 查询页面下拉选项。
     */
    @DS(DataSourceName.SLAVE)
    public Map<String, Object> options() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level1", level1Mapper.selectList(baseLevel1Query()).stream().map(row -> option(row.getId(), row.getLevel1Code(), row.getNameCn(), row.getNameEn(), LEVEL1, null)).toList());
        result.put("level2", level2Mapper.selectList(baseLevel2Query()).stream().map(row -> option(row.getId(), row.getLevel2Code(), row.getNameCn(), row.getNameEn(), LEVEL2, row.getLevel1Id())).toList());
        result.put("mccCodes", codeMapper.selectList(baseCodeQuery().orderByAsc(MccEntities.BaseMccCodeDO::getMccCode)).stream().map(row -> option(row.getId(), row.getMccCode(), row.getNameCn(), row.getNameEn(), MCC_CODE, row.getLevel2Id())).toList());
        result.put("cardSchemes", cardBrandDictRows().stream().map(row -> option(row.getId(), row.getDictValue(), row.getDictLabel(), row.getDictLabel(), CARD_BRAND_DICT, null)).toList());
        result.put("countries", isoCountryMapper.selectList(Wrappers.<IsoCountryDO>lambdaQuery()
                        .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(IsoCountryDO::getStatus, ENABLED)
                        .orderByAsc(IsoCountryDO::getAlpha2Code))
                .stream().map(row -> option(row.getId(), row.getAlpha2Code(), row.getChineseName(), row.getEnglishName(), "COUNTRY", null)).toList());
        return result;
    }

    /**
     * 导出 MCC 编码。
     */
    @DS(DataSourceName.SLAVE)
    public void exportCodes(MccRequests.MccTreeQueryRequest request, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<MccCodeExportRow> rows = codeMapper.selectList(baseCodeQuery().orderByAsc(MccEntities.BaseMccCodeDO::getMccCode))
                .stream()
                .filter(row -> StringUtils.hasText(request == null ? null : request.getMccCode()) ? row.getMccCode().contains(request.getMccCode().trim()) : true)
                .map(row -> toExportRow(row, locale))
                .toList();
        String exportTitle = excelI18nMessageResolver.resolve("excel.mcc.title", locale);
        excelExportService.export(
                ExcelExportRequest.<MccCodeExportRow>builder()
                        .fileName(exportTitle + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(exportTitle)
                        .titleKey("excel.mcc.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(MccCodeExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    private MccEntities.BaseMccLevel1DO saveLevel1(MccRequests.MccCategorySaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String code = normalizeRequired(request.getCategoryCode(), "categoryCode is required");
        MccEntities.BaseMccLevel1DO row = request.getId() == null ? new MccEntities.BaseMccLevel1DO() : getLevel1(request.getId());
        assertCategoryCodeNotExists(LEVEL1, code, null, row.getId());
        row.setLevel1Code(code);
        fillLevel1(row, request, now);
        if (row.getId() == null) {
            row.setCreateTime(now);
            row.setDeleted(NOT_DELETED);
            level1Mapper.insert(row);
        } else {
            level1Mapper.updateById(row);
        }
        return row;
    }

    private MccEntities.BaseMccLevel2DO saveLevel2(MccRequests.MccCategorySaveRequest request) {
        if (request.getParentId() == null || level1Mapper.selectCount(baseLevel1Query().eq(MccEntities.BaseMccLevel1DO::getId, request.getParentId())) == 0) {
            throw badRequest("新增二级分类时一级分类必须存在");
        }
        LocalDateTime now = LocalDateTime.now();
        String code = normalizeRequired(request.getCategoryCode(), "categoryCode is required");
        MccEntities.BaseMccLevel2DO row = request.getId() == null ? new MccEntities.BaseMccLevel2DO() : getLevel2(request.getId());
        assertCategoryCodeNotExists(LEVEL2, code, request.getParentId(), row.getId());
        row.setLevel1Id(request.getParentId());
        row.setLevel2Code(code);
        fillLevel2(row, request, now);
        if (row.getId() == null) {
            row.setCreateTime(now);
            row.setDeleted(NOT_DELETED);
            level2Mapper.insert(row);
        } else {
            level2Mapper.updateById(row);
        }
        return row;
    }

    private void fillLevel1(MccEntities.BaseMccLevel1DO row, MccRequests.MccCategorySaveRequest request, LocalDateTime now) {
        row.setNameCn(normalizeRequired(request.getNameCn(), "nameCn is required"));
        row.setNameEn(normalizeRequired(request.getNameEn(), "nameEn is required"));
        row.setSortNo(defaultSort(request.getSortNo()));
        row.setStatus(defaultStatus(request.getStatus()));
        row.setRemark(trimToNull(request.getRemark()));
        row.setUpdateTime(now);
    }

    private void fillLevel2(MccEntities.BaseMccLevel2DO row, MccRequests.MccCategorySaveRequest request, LocalDateTime now) {
        row.setNameCn(normalizeRequired(request.getNameCn(), "nameCn is required"));
        row.setNameEn(normalizeRequired(request.getNameEn(), "nameEn is required"));
        row.setSortNo(defaultSort(request.getSortNo()));
        row.setStatus(defaultStatus(request.getStatus()));
        row.setRemark(trimToNull(request.getRemark()));
        row.setUpdateTime(now);
    }

    private void validateCodeRequest(MccRequests.MccCodeSaveRequest request, boolean creating) {
        if (creating && !StringUtils.hasText(request.getMccCode())) {
            throw badRequest("MCC 编码不能为空");
        }
        if (StringUtils.hasText(request.getMccCode()) && !request.getMccCode().trim().matches(FOUR_DIGIT_MCC)) {
            throw badRequest("MCC 编码必须是 4 位数字");
        }
        normalizeRequired(request.getNameCn(), "MCC 中文名称不能为空");
        normalizeRequired(request.getNameEn(), "MCC 英文名称不能为空");
        normalizeRequired(request.getMccType(), "MCC 类型不能为空");
        normalizeRequired(request.getRiskLevel(), "风险等级不能为空");
        if (request.getLevel1Id() == null || request.getLevel2Id() == null) {
            throw badRequest("一级分类和二级分类不能为空");
        }
        MccEntities.BaseMccLevel2DO level2 = getLevel2(request.getLevel2Id());
        if (!Objects.equals(level2.getLevel1Id(), request.getLevel1Id())) {
            throw badRequest("二级分类不属于所选一级分类");
        }
        if (request.getEffectiveTime() != null && request.getExpireTime() != null
                && request.getEffectiveTime().isAfter(request.getExpireTime())) {
            throw badRequest("生效时间不能晚于失效时间");
        }
    }

    private void fillCode(MccEntities.BaseMccCodeDO row, MccRequests.MccCodeSaveRequest request) {
        if (StringUtils.hasText(request.getMccCode())) {
            row.setMccCode(request.getMccCode().trim());
        }
        row.setLevel1Id(request.getLevel1Id());
        row.setLevel2Id(request.getLevel2Id());
        row.setNameCn(normalizeRequired(request.getNameCn(), "MCC 中文名称不能为空"));
        row.setNameEn(normalizeRequired(request.getNameEn(), "MCC 英文名称不能为空"));
        row.setMccType(normalizeRequired(request.getMccType(), "MCC 类型不能为空"));
        row.setRiskLevel(normalizeRequired(request.getRiskLevel(), "风险等级不能为空"));
        row.setDeliveryApplicability(defaultIfBlank(request.getDeliveryApplicability(), "UNKNOWN"));
        row.setSource(trimToNull(request.getSource()));
        row.setVersionNo(trimToNull(request.getVersionNo()));
        row.setEffectiveTime(request.getEffectiveTime());
        row.setExpireTime(request.getExpireTime());
        row.setSortNo(defaultSort(request.getSortNo()));
        row.setStatus(defaultStatus(request.getStatus()));
        row.setRemark(trimToNull(request.getRemark()));
    }

    private void validatePolicyBase(MccRequests.MccRiskPolicySaveRequest request) {
        if (!existsMccCode(request.getMccCode(), null)) {
            throw badRequest("MCC 编码不存在");
        }
        String channelScope = normalizeRequired(request.getChannelScope(), "channelScope is required");
        String countryScope = normalizeRequired(request.getCountryScope(), "countryScope is required");
        validateScope(channelScope, request.getChannelCode(), "channel_code");
        validateScope(countryScope, request.getCountryCode(), "country_code");
        if (APPLY_SCOPE_SPECIFIC.equals(countryScope) && !existsCountryAlpha2(request.getCountryCode())) {
            throw badRequest("country_code 必须是合法 ISO 3166-1 alpha-2 编码");
        }
    }

    private List<String> resolveCardSchemes(MccRequests.MccRiskPolicySaveRequest request) {
        Set<String> allowed = cardBrandValues();
        List<String> schemes = Boolean.TRUE.equals(request.getSelectAllCardSchemes())
                ? new ArrayList<>(allowed)
                : request.getCardSchemes() == null ? List.of() : request.getCardSchemes();
        schemes = schemes.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (schemes.isEmpty()) {
            throw badRequest("cardSchemes 不能为空，除非 selectAllCardSchemes = true");
        }
        if (schemes.stream().anyMatch("ALL"::equalsIgnoreCase)) {
            throw badRequest("card_brand 不允许使用 ALL");
        }
        List<String> invalid = schemes.stream().filter(scheme -> !allowed.contains(scheme)).toList();
        if (!invalid.isEmpty()) {
            throw badRequest("card_brand 必须是真实卡品牌: " + invalid);
        }
        return schemes;
    }

    private void fillPolicy(MccEntities.BaseMccRiskPolicyDO row, MccRequests.MccRiskPolicySaveRequest request, String cardScheme) {
        String channelScope = request.getChannelScope().trim();
        String countryScope = request.getCountryScope().trim();
        int status = defaultStatus(request.getStatus());
        row.setMccCode(request.getMccCode().trim());
        row.setCardScheme(cardScheme);
        row.setChannelScope(channelScope);
        row.setChannelCode(APPLY_SCOPE_SPECIFIC.equals(channelScope) ? request.getChannelCode().trim() : "");
        row.setCountryScope(countryScope);
        row.setCountryCode(APPLY_SCOPE_SPECIFIC.equals(countryScope) ? request.getCountryCode().trim().toUpperCase(Locale.ROOT) : "");
        row.setRiskLevel(normalizeRequired(request.getRiskLevel(), "riskLevel is required"));
        row.setAllowOnboarding(defaultFlag(request.getAllowOnboarding(), ENABLED));
        row.setAllowAcquiring(defaultFlag(request.getAllowAcquiring(), ENABLED));
        row.setRequireEnhancedReview(defaultFlag(request.getRequireEnhancedReview(), DISABLED));
        row.setStatus(status);
        row.setPolicyStatus(status);
        row.setRemark(trimToNull(request.getRemark()));
    }

    private void assertPolicyNotExists(Long excludeId, MccRequests.MccRiskPolicySaveRequest request, String cardScheme) {
        String channelScope = request.getChannelScope().trim();
        String channelCode = APPLY_SCOPE_SPECIFIC.equals(channelScope) ? request.getChannelCode().trim() : "";
        String countryScope = request.getCountryScope().trim();
        String countryCode = APPLY_SCOPE_SPECIFIC.equals(countryScope) ? request.getCountryCode().trim().toUpperCase(Locale.ROOT) : "";
        Long count = riskPolicyMapper.selectCount(basePolicyQuery()
                .eq(MccEntities.BaseMccRiskPolicyDO::getMccCode, request.getMccCode().trim())
                .eq(MccEntities.BaseMccRiskPolicyDO::getCardScheme, cardScheme)
                .eq(MccEntities.BaseMccRiskPolicyDO::getChannelScope, channelScope)
                .eq(MccEntities.BaseMccRiskPolicyDO::getChannelCode, channelCode)
                .eq(MccEntities.BaseMccRiskPolicyDO::getCountryScope, countryScope)
                .eq(MccEntities.BaseMccRiskPolicyDO::getCountryCode, countryCode)
                .ne(excludeId != null, MccEntities.BaseMccRiskPolicyDO::getId, excludeId));
        if (count != null && count > 0) {
            throw badRequest("同一 MCC、卡品牌、渠道范围和国家地区范围的风险策略已存在");
        }
    }

    private LambdaQueryWrapper<MccEntities.BaseMccRiskPolicyDO> buildPolicyQuery(MccRequests.MccRiskPolicyQueryRequest query) {
        LambdaQueryWrapper<MccEntities.BaseMccRiskPolicyDO> wrapper = basePolicyQuery()
                .eq(StringUtils.hasText(query.getMccCode()), MccEntities.BaseMccRiskPolicyDO::getMccCode, trimToNull(query.getMccCode()))
                .eq(StringUtils.hasText(query.getCardScheme()), MccEntities.BaseMccRiskPolicyDO::getCardScheme, trimToNull(query.getCardScheme()))
                .eq(StringUtils.hasText(query.getChannelScope()), MccEntities.BaseMccRiskPolicyDO::getChannelScope, trimToNull(query.getChannelScope()))
                .eq(StringUtils.hasText(query.getChannelCode()), MccEntities.BaseMccRiskPolicyDO::getChannelCode, trimToNull(query.getChannelCode()))
                .eq(StringUtils.hasText(query.getCountryScope()), MccEntities.BaseMccRiskPolicyDO::getCountryScope, trimToNull(query.getCountryScope()))
                .eq(StringUtils.hasText(query.getCountryCode()), MccEntities.BaseMccRiskPolicyDO::getCountryCode, trimToNull(query.getCountryCode()))
                .eq(StringUtils.hasText(query.getRiskLevel()), MccEntities.BaseMccRiskPolicyDO::getRiskLevel, trimToNull(query.getRiskLevel()))
                .eq(query.getAllowOnboarding() != null, MccEntities.BaseMccRiskPolicyDO::getAllowOnboarding, query.getAllowOnboarding())
                .eq(query.getAllowAcquiring() != null, MccEntities.BaseMccRiskPolicyDO::getAllowAcquiring, query.getAllowAcquiring())
                .eq(query.getRequireEnhancedReview() != null, MccEntities.BaseMccRiskPolicyDO::getRequireEnhancedReview, query.getRequireEnhancedReview())
                .eq(query.getStatus() != null, MccEntities.BaseMccRiskPolicyDO::getStatus, query.getStatus())
                .orderByDesc(MccEntities.BaseMccRiskPolicyDO::getUpdateTime);
        if (StringUtils.hasText(query.getMccName())) {
            String keyword = query.getMccName().trim();
            List<String> mccCodes = codeMapper.selectList(baseCodeQuery()
                            .and(w -> w.like(MccEntities.BaseMccCodeDO::getNameCn, keyword)
                                    .or().like(MccEntities.BaseMccCodeDO::getNameEn, keyword)))
                    .stream().map(MccEntities.BaseMccCodeDO::getMccCode).toList();
            wrapper.in(!mccCodes.isEmpty(), MccEntities.BaseMccRiskPolicyDO::getMccCode, mccCodes);
            if (mccCodes.isEmpty()) {
                wrapper.eq(MccEntities.BaseMccRiskPolicyDO::getMccCode, "__NO_MATCH__");
            }
        }
        return wrapper;
    }

    private void validateScope(String scope, String code, String codeFieldName) {
        if (!APPLY_SCOPE_ALL.equals(scope) && !APPLY_SCOPE_SPECIFIC.equals(scope)) {
            throw badRequest(scope + " is not supported");
        }
        if (APPLY_SCOPE_SPECIFIC.equals(scope) && !StringUtils.hasText(code)) {
            throw badRequest(codeFieldName + " 必填");
        }
        if (StringUtils.hasText(code) && "ALL".equalsIgnoreCase(code.trim())) {
            throw badRequest(codeFieldName + " 不允许使用 ALL");
        }
    }

    private Set<String> cardBrandValues() {
        List<SysDictDataDO> rows = cardBrandDictRows();
        return rows.stream().map(SysDictDataDO::getDictValue).filter(StringUtils::hasText).collect(Collectors.toSet());
    }

    private List<SysDictDataDO> cardBrandDictRows() {
        List<SysDictDataDO> rows = dictDataMapper.selectList(Wrappers.<SysDictDataDO>lambdaQuery()
                .eq(SysDictDataDO::getDictType, CARD_BRAND_DICT)
                .eq(SysDictDataDO::getLocale, DEFAULT_LOCALE)
                .eq(SysDictDataDO::getStatus, ENABLED)
                .eq(SysDictDataDO::getDeleted, NOT_DELETED)
                .ne(SysDictDataDO::getDictValue, "ALL")
                .orderByAsc(SysDictDataDO::getDictSort)
                .orderByAsc(SysDictDataDO::getId));
        return rows == null ? List.of() : rows;
    }

    private boolean existsCountryAlpha2(String alpha2) {
        if (!StringUtils.hasText(alpha2)) {
            return false;
        }
        Long count = isoCountryMapper.selectCount(Wrappers.<IsoCountryDO>lambdaQuery()
                .eq(IsoCountryDO::getAlpha2Code, alpha2.trim().toUpperCase(Locale.ROOT))
                .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED));
        return count != null && count > 0;
    }

    private void assertCategoryCodeNotExists(String nodeType, String code, Long parentId, Long excludeId) {
        Long count;
        if (LEVEL1.equals(nodeType)) {
            count = level1Mapper.selectCount(baseLevel1Query().eq(MccEntities.BaseMccLevel1DO::getLevel1Code, code)
                    .ne(excludeId != null, MccEntities.BaseMccLevel1DO::getId, excludeId));
        } else if (LEVEL2.equals(nodeType)) {
            count = level2Mapper.selectCount(baseLevel2Query().eq(MccEntities.BaseMccLevel2DO::getLevel1Id, parentId)
                    .eq(MccEntities.BaseMccLevel2DO::getLevel2Code, code)
                    .ne(excludeId != null, MccEntities.BaseMccLevel2DO::getId, excludeId));
        } else {
            throw badRequest("分类节点只支持 LEVEL1 / LEVEL2");
        }
        if (count != null && count > 0) {
            throw badRequest("同一层级分类编码不能重复");
        }
    }

    private boolean existsMccCode(String mccCode, Long excludeId) {
        if (!StringUtils.hasText(mccCode)) {
            return false;
        }
        Long count = codeMapper.selectCount(baseCodeQuery()
                .eq(MccEntities.BaseMccCodeDO::getMccCode, mccCode.trim())
                .ne(excludeId != null, MccEntities.BaseMccCodeDO::getId, excludeId));
        return count != null && count > 0;
    }

    private MccVO.MccTreeNodeVO filterTree(MccVO.MccTreeNodeVO node, MccRequests.MccTreeQueryRequest query) {
        List<MccVO.MccTreeNodeVO> children = node.getChildren().stream()
                .map(child -> filterTree(child, query))
                .filter(Objects::nonNull)
                .toList();
        boolean matches = matches(node, query);
        if (!matches && children.isEmpty()) {
            return null;
        }
        node.setChildren(new ArrayList<>(children));
        return node;
    }

    private boolean matches(MccVO.MccTreeNodeVO node, MccRequests.MccTreeQueryRequest query) {
        return containsAny(node, query.getKeyword())
                && fieldContains(node.getMccCode(), query.getMccCode())
                && fieldContains(node.getNameCn(), query.getNameCn())
                && fieldContains(node.getNameEn(), query.getNameEn())
                && fieldEquals(node.getNodeType(), query.getNodeType())
                && fieldEquals(node.getRiskLevel(), query.getRiskLevel())
                && (query.getStatus() == null || Objects.equals(node.getStatus(), query.getStatus()));
    }

    private boolean containsAny(MccVO.MccTreeNodeVO node, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return fieldContains(node.getCode(), keyword)
                || fieldContains(node.getNameCn(), keyword)
                || fieldContains(node.getNameEn(), keyword)
                || fieldContains(node.getMccCode(), keyword)
                || fieldContains(node.getMccType(), keyword)
                || fieldContains(node.getRiskLevel(), keyword);
    }

    private boolean fieldContains(String source, String target) {
        return !StringUtils.hasText(target) || (source != null && source.toLowerCase(Locale.ROOT).contains(target.trim().toLowerCase(Locale.ROOT)));
    }

    private boolean fieldEquals(String source, String target) {
        return !StringUtils.hasText(target) || Objects.equals(source, target.trim());
    }

    private MccVO.MccTreeNodeVO toLevel1Node(MccEntities.BaseMccLevel1DO row) {
        return baseNode(LEVEL1, row.getId(), null, 1, row.getLevel1Code(), row.getNameCn(), row.getNameEn(), row.getStatus(), row.getSortNo(), row.getRemark(), row.getCreateTime(), row.getUpdateTime());
    }

    private MccVO.MccTreeNodeVO toLevel2Node(MccEntities.BaseMccLevel2DO row) {
        return baseNode(LEVEL2, row.getId(), LEVEL1 + ":" + row.getLevel1Id(), 2, row.getLevel2Code(), row.getNameCn(), row.getNameEn(), row.getStatus(), row.getSortNo(), row.getRemark(), row.getCreateTime(), row.getUpdateTime());
    }

    private MccVO.MccTreeNodeVO toMccCodeNode(MccEntities.BaseMccCodeDO row) {
        MccVO.MccTreeNodeVO node = baseNode(MCC_CODE, row.getLevel2Id(), LEVEL2 + ":" + row.getLevel2Id(), 3, row.getMccCode(), row.getNameCn(), row.getNameEn(), row.getStatus(), row.getSortNo(), row.getRemark(), row.getCreateTime(), row.getUpdateTime());
        node.setNodeKey(MCC_CODE + ":" + row.getId());
        node.setId(row.getId());
        node.setMccCode(row.getMccCode());
        node.setMccNameCn(row.getNameCn());
        node.setMccNameEn(row.getNameEn());
        node.setRiskLevel(row.getRiskLevel());
        node.setMccType(row.getMccType());
        node.setDeliveryApplicability(row.getDeliveryApplicability());
        node.setSource(row.getSource());
        node.setVersionNo(row.getVersionNo());
        node.setEffectiveTime(row.getEffectiveTime());
        node.setExpireTime(row.getExpireTime());
        node.setLabel(row.getMccCode() + " - " + row.getNameEn());
        return node;
    }

    private MccVO.MccTreeNodeVO baseNode(String type, Long id, String parentNodeKey, Integer level, String code,
                                         String nameCn, String nameEn, Integer status, Integer sortNo, String remark,
                                         LocalDateTime createTime, LocalDateTime updateTime) {
        MccVO.MccTreeNodeVO node = new MccVO.MccTreeNodeVO();
        node.setNodeKey(type + ":" + id);
        node.setNodeType(type);
        node.setId(id);
        node.setParentNodeKey(parentNodeKey);
        node.setLevel(level);
        node.setCode(code);
        node.setNameCn(nameCn);
        node.setNameEn(nameEn);
        node.setLabel(StringUtils.hasText(nameCn) ? nameCn : nameEn);
        node.setStatus(status);
        node.setSortNo(sortNo);
        node.setRemark(remark);
        node.setCreateTime(createTime);
        node.setUpdateTime(updateTime);
        return node;
    }

    private void addChild(MccVO.MccTreeNodeVO parent, MccVO.MccTreeNodeVO child) {
        if (parent == null || child == null) {
            return;
        }
        parent.getChildren().add(child);
        parent.getChildren().sort(Comparator.comparing(MccVO.MccTreeNodeVO::getSortNo, Comparator.nullsLast(Integer::compareTo)));
    }

    private MccVO.MccCodeVO toCodeVO(MccEntities.BaseMccCodeDO row) {
        MccVO.MccCodeVO vo = new MccVO.MccCodeVO();
        vo.setId(row.getId());
        vo.setLevel1Id(row.getLevel1Id());
        vo.setLevel2Id(row.getLevel2Id());
        vo.setMccCode(row.getMccCode());
        vo.setNameCn(row.getNameCn());
        vo.setNameEn(row.getNameEn());
        vo.setMccType(row.getMccType());
        vo.setRiskLevel(row.getRiskLevel());
        vo.setDeliveryApplicability(row.getDeliveryApplicability());
        vo.setSource(row.getSource());
        vo.setVersionNo(row.getVersionNo());
        vo.setEffectiveTime(row.getEffectiveTime());
        vo.setExpireTime(row.getExpireTime());
        vo.setSortNo(row.getSortNo());
        vo.setStatus(row.getStatus());
        vo.setRemark(row.getRemark());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        fillCategoryPath(vo, row.getLevel1Id(), row.getLevel2Id());
        return vo;
    }

    private void fillCategoryPath(MccVO.MccCodeVO vo, Long level1Id, Long level2Id) {
        if (level2Id != null) {
            MccEntities.BaseMccLevel2DO level2 = level2Mapper.selectById(level2Id);
            if (level2 != null) {
                vo.setLevel2Name(level2.getNameCn());
                vo.setLevel1Id(level2.getLevel1Id());
            }
        }
        Long resolvedLevel1Id = vo.getLevel1Id() == null ? level1Id : vo.getLevel1Id();
        if (resolvedLevel1Id != null) {
            MccEntities.BaseMccLevel1DO level1 = level1Mapper.selectById(resolvedLevel1Id);
            if (level1 != null) {
                vo.setLevel1Name(level1.getNameCn());
            }
        }
    }

    private MccVO.MccRiskPolicyVO toPolicyVO(MccEntities.BaseMccRiskPolicyDO row) {
        MccVO.MccRiskPolicyVO vo = new MccVO.MccRiskPolicyVO();
        vo.setId(row.getId());
        vo.setMccCode(row.getMccCode());
        MccEntities.BaseMccCodeDO code = codeMapper.selectOne(baseCodeQuery().eq(MccEntities.BaseMccCodeDO::getMccCode, row.getMccCode()).last("LIMIT 1"));
        if (code != null) {
            vo.setMccNameCn(code.getNameCn());
            vo.setMccNameEn(code.getNameEn());
        }
        vo.setCardScheme(row.getCardScheme());
        vo.setCardSchemeName(cardSchemeLabel(row.getCardScheme()));
        vo.setChannelScope(row.getChannelScope());
        vo.setChannelCode(row.getChannelCode());
        vo.setCountryScope(row.getCountryScope());
        vo.setCountryCode(row.getCountryCode());
        fillCountryName(vo, row.getCountryCode());
        vo.setRiskLevel(row.getRiskLevel());
        vo.setAllowOnboarding(row.getAllowOnboarding());
        vo.setAllowAcquiring(row.getAllowAcquiring());
        vo.setRequireEnhancedReview(row.getRequireEnhancedReview());
        vo.setStatus(row.getStatus());
        vo.setRemark(row.getRemark());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    private void fillCountryName(MccVO.MccRiskPolicyVO vo, String countryCode) {
        if (!StringUtils.hasText(countryCode)) {
            return;
        }
        IsoCountryDO country = isoCountryMapper.selectOne(Wrappers.<IsoCountryDO>lambdaQuery()
                .eq(IsoCountryDO::getAlpha2Code, countryCode)
                .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (country != null) {
            vo.setCountryNameCn(country.getChineseName());
            vo.setCountryNameEn(country.getEnglishName());
        }
    }

    private String cardSchemeLabel(String cardScheme) {
        SysDictDataQueryRequest query = new SysDictDataQueryRequest();
        query.setDictType(CARD_BRAND_DICT);
        query.setDictValue(cardScheme);
        query.setLocale(DEFAULT_LOCALE);
        List<SysDictDataDTO> rows = adminDictService.listDictData(query);
        return rows.isEmpty() ? cardScheme : rows.get(0).getDictLabel();
    }

    private MccCodeExportRow toExportRow(MccEntities.BaseMccCodeDO row, Locale locale) {
        MccVO.MccCodeVO code = toCodeVO(row);
        MccCodeExportRow exportRow = new MccCodeExportRow();
        exportRow.setMccCode(code.getMccCode());
        exportRow.setNameCn(code.getNameCn());
        exportRow.setNameEn(code.getNameEn());
        exportRow.setLevel1Name(code.getLevel1Name());
        exportRow.setLevel2Name(code.getLevel2Name());
        exportRow.setMccType(code.getMccType());
        exportRow.setRiskLevel(code.getRiskLevel());
        exportRow.setDeliveryApplicability(code.getDeliveryApplicability());
        exportRow.setStatus(excelI18nMessageResolver.resolve(
                Objects.equals(code.getStatus(), ENABLED) ? "excel.common.enabled" : "excel.common.disabled",
                locale
        ));
        exportRow.setSource(code.getSource());
        exportRow.setVersionNo(code.getVersionNo());
        exportRow.setEffectiveTime(code.getEffectiveTime());
        exportRow.setExpireTime(code.getExpireTime());
        exportRow.setRemark(code.getRemark());
        return exportRow;
    }

    private MccVO.MccOptionVO option(Long id, String code, String nameCn, String nameEn, String nodeType, Long parentId) {
        MccVO.MccOptionVO option = new MccVO.MccOptionVO();
        option.setId(id);
        option.setCode(code);
        option.setNameCn(nameCn);
        option.setNameEn(nameEn);
        option.setLabel(code + " - " + (StringUtils.hasText(nameCn) ? nameCn : nameEn));
        option.setNodeType(nodeType);
        option.setParentId(parentId);
        return option;
    }

    private void softDeleteLevel1(MccEntities.BaseMccLevel1DO row) {
        row.setStatus(DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        level1Mapper.updateById(row);
    }

    private void softDeleteLevel2(MccEntities.BaseMccLevel2DO row) {
        row.setStatus(DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        level2Mapper.updateById(row);
    }

    private MccEntities.BaseMccLevel1DO getLevel1(Long id) {
        MccEntities.BaseMccLevel1DO row = level1Mapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("一级分类不存在");
        }
        return row;
    }

    private MccEntities.BaseMccLevel2DO getLevel2(Long id) {
        MccEntities.BaseMccLevel2DO row = level2Mapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("二级分类不存在");
        }
        return row;
    }

    private MccEntities.BaseMccCodeDO getCodeById(Long id) {
        MccEntities.BaseMccCodeDO row = codeMapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("MCC 编码不存在");
        }
        return row;
    }

    private MccEntities.BaseMccRiskPolicyDO getPolicy(Long id) {
        MccEntities.BaseMccRiskPolicyDO row = riskPolicyMapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("风险策略不存在");
        }
        return row;
    }

    private LambdaQueryWrapper<MccEntities.BaseMccLevel1DO> baseLevel1Query() {
        return Wrappers.<MccEntities.BaseMccLevel1DO>lambdaQuery()
                .eq(MccEntities.BaseMccLevel1DO::getDeleted, NOT_DELETED)
                .orderByAsc(MccEntities.BaseMccLevel1DO::getSortNo)
                .orderByAsc(MccEntities.BaseMccLevel1DO::getId);
    }

    private LambdaQueryWrapper<MccEntities.BaseMccLevel2DO> baseLevel2Query() {
        return Wrappers.<MccEntities.BaseMccLevel2DO>lambdaQuery()
                .eq(MccEntities.BaseMccLevel2DO::getDeleted, NOT_DELETED)
                .orderByAsc(MccEntities.BaseMccLevel2DO::getSortNo)
                .orderByAsc(MccEntities.BaseMccLevel2DO::getId);
    }

    private LambdaQueryWrapper<MccEntities.BaseMccCodeDO> baseCodeQuery() {
        return Wrappers.<MccEntities.BaseMccCodeDO>lambdaQuery()
                .eq(MccEntities.BaseMccCodeDO::getDeleted, NOT_DELETED);
    }

    private LambdaQueryWrapper<MccEntities.BaseMccRiskPolicyDO> basePolicyQuery() {
        return Wrappers.<MccEntities.BaseMccRiskPolicyDO>lambdaQuery()
                .eq(MccEntities.BaseMccRiskPolicyDO::getDeleted, NOT_DELETED);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int defaultSort(Integer sortNo) {
        return sortNo == null ? 100 : sortNo;
    }

    private int defaultStatus(Integer status) {
        return status == null ? ENABLED : validStatus(status);
    }

    private int validStatus(Integer status) {
        if (!Objects.equals(status, ENABLED) && !Objects.equals(status, DISABLED)) {
            throw badRequest("status must be 0 or 1");
        }
        return status;
    }

    private int defaultFlag(Integer flag, int defaultValue) {
        if (flag == null) {
            return defaultValue;
        }
        if (!Objects.equals(flag, ENABLED) && !Objects.equals(flag, DISABLED)) {
            throw badRequest("flag must be 0 or 1");
        }
        return flag;
    }

    private long nonNullCount(Long count) {
        return count == null ? 0L : count;
    }

    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }
}
