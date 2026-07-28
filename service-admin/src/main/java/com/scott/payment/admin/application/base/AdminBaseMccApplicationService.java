package com.scott.payment.admin.application.base;

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
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
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

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseMccApplicationService
 * @date : 2026-06-27 16:49
 * @email : scott_x@163.com
 * @description : Admin Base MCC Application Service 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
public class AdminBaseMccApplicationService {

    /**
     * EXPORT TIME FORMATTER，用于保存 Admin Base MCC Application Service 中与 exporttimeformatter 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /**
     * NOT DELETED，用于保存 Admin Base MCC Application Service 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * ENABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * DISABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int DISABLED = 0;
    /**
     * LEVEL 1，用于保存 Admin Base MCC Application Service 中与 一级分类 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String LEVEL1 = "LEVEL1";
    /**
     * LEVEL 2，用于保存 Admin Base MCC Application Service 中与 二级分类 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String LEVEL2 = "LEVEL2";
    /**
     * MCC CODE，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String MCC_CODE = "MCC_CODE";
    /**
     * APPLY SCOPE ALL，用于保存 Admin Base MCC Application Service 中与 applyscopeall 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String APPLY_SCOPE_ALL = "ALL";
    /**
     * APPLY SCOPE SPECIFIC，用于保存 Admin Base MCC Application Service 中与 applyscopespecific 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String APPLY_SCOPE_SPECIFIC = "SPECIFIC";
    /**
     * CARD BRAND DICT，用于保存 Admin Base MCC Application Service 中与 cardbranddict 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String CARD_BRAND_DICT = "card_brand";
    /**
     * DEFAULT LOCALE，用于保存 Admin Base MCC Application Service 中与 defaultlocale 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_LOCALE = "zh-CN";
    /**
     * FOUR DIGIT MCC，用于保存 Admin Base MCC Application Service 中与 fourdigitmcc 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String FOUR_DIGIT_MCC = "^[0-9]{4}$";

    /**
     * level 1 Mapper 依赖，用于 Admin Base MCC Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMccLevel1Mapper level1Mapper;
    /**
     * level 2 Mapper 依赖，用于 Admin Base MCC Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMccLevel2Mapper level2Mapper;
    /**
     * code Mapper，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMccCodeMapper codeMapper;
    /**
     * risk Policy Mapper 依赖，用于 Admin Base MCC Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMccRiskPolicyMapper riskPolicyMapper;
    /**
     * dict Data Mapper 依赖，用于 Admin Base MCC Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysDictDataMapper dictDataMapper;
    /**
     * admin Dict Service 依赖，用于 Admin Base MCC Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminDictService adminDictService;
    /**
     * ISO Country Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持国家地区；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final IsoCountryMapper isoCountryMapper;
    /**
     * merchant Info Mapper 依赖，用于 Admin Base MCC Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMerchantInfoMapper merchantInfoMapper;
    /**
     * excel Export Service 依赖，用于 Admin Base MCC Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I 18 n Message Resolver，用于保存 Admin Base MCC Application Service 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver，用于保存 Admin Base MCC Application Service 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelLocaleResolver excelLocaleResolver;

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
                                          ExcelLocaleResolver excelLocaleResolver) {
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
    }

    /**
     * 查询 MCC 分类和编码树。
     *
     * <p>搜索命中 MCC Code 时会保留对应二级和一级分类，保证页面仍展示完整树路径。</p>
     */
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
    @Transactional(rollbackFor = Exception.class)
    public MccVO.MccTreeNodeVO saveCategory(MccRequests.MccCategorySaveRequest request) {
        String nodeType = normalizeRequired(request.getNodeType(), "nodeType is required");
        return switch (nodeType) {
            case LEVEL1 -> toLevel1Node(saveLevel1(request));
            case LEVEL2 -> toLevel2Node(saveLevel2(request));
            default -> throw badRequest("分类节点只支持 LEVEL1 / LEVEL2");
        };
    }

    /**
     * 删除 MCC 分类。存在下级分类或 MCC Code 时不允许删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(MccRequests.MccDeleteRequest request) {
        String nodeType = normalizeRequired(request.getNodeType(), "nodeType is required");
        if (LEVEL1.equals(nodeType)) {
            Long children = level2Mapper.selectCount(baseLevel2Query().eq(MccEntities.BaseMccLevel2DO::getLevel1Id, request.getId()));
            if (children != null && children > 0) {
                throw badRequest("一级分类下存在二级分类，不能删除");
            }
            MccEntities.BaseMccLevel1DO row = getLevel1(request.getId());
            softDeleteLevel1(row);
            return;
        }
        if (LEVEL2.equals(nodeType)) {
            Long children = codeMapper.selectCount(baseCodeQuery().eq(MccEntities.BaseMccCodeDO::getLevel2Id, request.getId()));
            if (children != null && children > 0) {
                throw badRequest("当前二级分类下存在 MCC 编码，不允许删除");
            }
            MccEntities.BaseMccLevel2DO row = getLevel2(request.getId());
            softDeleteLevel2(row);
            return;
        }
        throw badRequest("分类节点只支持 LEVEL1 / LEVEL2");
    }

    /**
     * 更新分类或 MCC 编码状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(MccRequests.MccStatusUpdateRequest request) {
        int status = validStatus(request.getStatus());
        String nodeType = normalizeRequired(request.getNodeType(), "nodeType is required");
        LocalDateTime now = LocalDateTime.now();
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
    @Transactional(rollbackFor = Exception.class)
    public MccVO.MccCodeVO createCode(MccRequests.MccCodeSaveRequest request) {
        validateCodeRequest(request, true);
        if (existsMccCode(request.getMccCode(), null)) {
            throw badRequest("MCC 编码已存在");
        }
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
        fillCode(row, request);
        row.setUpdateTime(LocalDateTime.now());
        codeMapper.updateById(row);
        return toCodeVO(row);
    }

    /**
     * 查询 MCC 编码详情。
     */
    public MccVO.MccCodeVO getCode(Long id) {
        return toCodeVO(getCodeById(id));
    }

    /**
     * 删除 MCC 编码。存在风险策略或商户资料引用时不允许删除。
     */
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
        row.setStatus(DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        codeMapper.updateById(row);
    }

    /**
     * 分页查询 MCC 风险策略。
     */
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

    /**
     * 创建一级分类，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
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

    /**
     * 创建二级分类，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
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

    /**
     * 构造一级分类对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void fillLevel1(MccEntities.BaseMccLevel1DO row, MccRequests.MccCategorySaveRequest request, LocalDateTime now) {
        row.setNameCn(normalizeRequired(request.getNameCn(), "nameCn is required"));
        row.setNameEn(normalizeRequired(request.getNameEn(), "nameEn is required"));
        row.setSortNo(defaultSort(request.getSortNo()));
        row.setStatus(defaultStatus(request.getStatus()));
        row.setRemark(trimToNull(request.getRemark()));
        row.setUpdateTime(now);
    }

    /**
     * 构造二级分类对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void fillLevel2(MccEntities.BaseMccLevel2DO row, MccRequests.MccCategorySaveRequest request, LocalDateTime now) {
        row.setNameCn(normalizeRequired(request.getNameCn(), "nameCn is required"));
        row.setNameEn(normalizeRequired(request.getNameEn(), "nameEn is required"));
        row.setSortNo(defaultSort(request.getSortNo()));
        row.setStatus(defaultStatus(request.getStatus()));
        row.setRemark(trimToNull(request.getRemark()));
        row.setUpdateTime(now);
    }

    /**
     * 校验编码请求输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param creating creating 输入值，参与 creating 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 构造编码对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
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

    /**
     * 校验policybase输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
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

    /**
     * 解析resolvecardschemes，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造策略配置对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param cardScheme 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
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

    /**
     * 校验断言policynotexists输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param cardScheme 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
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

    /**
     * 构造policyquery对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 校验scope输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param scope scope 输入值，参与 scope 的查询、校验、转换、写入或日志摘要
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param codeFieldName code Field Name 输入值，参与 编码fieldname 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 整理card品牌值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<String> cardBrandValues() {
        List<SysDictDataDO> rows = cardBrandDictRows();
        return rows.stream().map(SysDictDataDO::getDictValue).filter(StringUtils::hasText).collect(Collectors.toSet());
    }

    /**
     * 整理card品牌dict行，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 判断 exists country alpha 2 条件是否成立，用于控制 Admin Base MCC Application Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param alpha2 alpha 2 输入值，参与 alpha2 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean existsCountryAlpha2(String alpha2) {
        if (!StringUtils.hasText(alpha2)) {
            return false;
        }
        Long count = isoCountryMapper.selectCount(Wrappers.<IsoCountryDO>lambdaQuery()
                .eq(IsoCountryDO::getAlpha2Code, alpha2.trim().toUpperCase(Locale.ROOT))
                .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED));
        return count != null && count > 0;
    }

    /**
     * 校验断言category编码notexists输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param nodeType node Type 输入值，参与 nodetype 的查询、校验、转换、写入或日志摘要
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param parentId parent ID 输入值，参与 parentID 的查询、校验、转换、写入或日志摘要
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 判断 exists mcc code 条件是否成立，用于控制 Admin Base MCC Application Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param mccCode MCC Code 输入值，参与 mcc编码 的查询、校验、转换、写入或日志摘要
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean existsMccCode(String mccCode, Long excludeId) {
        if (!StringUtils.hasText(mccCode)) {
            return false;
        }
        Long count = codeMapper.selectCount(baseCodeQuery()
                .eq(MccEntities.BaseMccCodeDO::getMccCode, mccCode.trim())
                .ne(excludeId != null, MccEntities.BaseMccCodeDO::getId, excludeId));
        return count != null && count > 0;
    }

    /**
     * 构建筛选树，按层级关系组装树形业务视图。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param node node 输入值，参与 node 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 规范化matches，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param node node 输入值，参与 node 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean matches(MccVO.MccTreeNodeVO node, MccRequests.MccTreeQueryRequest query) {
        return containsAny(node, query.getKeyword())
                && fieldContains(node.getMccCode(), query.getMccCode())
                && fieldContains(node.getNameCn(), query.getNameCn())
                && fieldContains(node.getNameEn(), query.getNameEn())
                && fieldEquals(node.getNodeType(), query.getNodeType())
                && fieldEquals(node.getRiskLevel(), query.getRiskLevel())
                && (query.getStatus() == null || Objects.equals(node.getStatus(), query.getStatus()));
    }

    /**
     * 规范化containsany，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param node node 输入值，参与 node 的查询、校验、转换、写入或日志摘要
     * @param keyword 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 规范化fieldcontains，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean fieldContains(String source, String target) {
        return !StringUtils.hasText(target) || (source != null && source.toLowerCase(Locale.ROOT).contains(target.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * 规范化fieldequals，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean fieldEquals(String source, String target) {
        return !StringUtils.hasText(target) || Objects.equals(source, target.trim());
    }

    /**
     * 构造level1node对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    private MccVO.MccTreeNodeVO toLevel1Node(MccEntities.BaseMccLevel1DO row) {
        return baseNode(LEVEL1, row.getId(), null, 1, row.getLevel1Code(), row.getNameCn(), row.getNameEn(), row.getStatus(), row.getSortNo(), row.getRemark(), row.getCreateTime(), row.getUpdateTime());
    }

    /**
     * 构造level2node对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    private MccVO.MccTreeNodeVO toLevel2Node(MccEntities.BaseMccLevel2DO row) {
        return baseNode(LEVEL2, row.getId(), LEVEL1 + ":" + row.getLevel1Id(), 2, row.getLevel2Code(), row.getNameCn(), row.getNameEn(), row.getStatus(), row.getSortNo(), row.getRemark(), row.getCreateTime(), row.getUpdateTime());
    }

    /**
     * 构造mcc编码node对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
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

/**
 * 整理基础node，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param type type 输入值，参与 type 的查询、校验、转换、写入或日志摘要
 * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
 * @param parentNodeKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
 * @param level level 输入值，参与 level 的查询、校验、转换、写入或日志摘要
 * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
 * @param nameCn name Cn 输入值，参与 namecn 的查询、校验、转换、写入或日志摘要
 * @param nameEn name En 输入值，参与 nameen 的查询、校验、转换、写入或日志摘要
 * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param sortNo sort No 输入值，参与 sortno 的查询、校验、转换、写入或日志摘要
 * @param remark remark 输入值，参与 remark 的查询、校验、转换、写入或日志摘要
 * @param createTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param updateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
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

    /**
     * 创建子节点，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param parent parent 输入值，参与 parent 的查询、校验、转换、写入或日志摘要
     * @param child child 输入值，参与 子节点 的查询、校验、转换、写入或日志摘要
     */
    private void addChild(MccVO.MccTreeNodeVO parent, MccVO.MccTreeNodeVO child) {
        if (parent == null || child == null) {
            return;
        }
        parent.getChildren().add(child);
        parent.getChildren().sort(Comparator.comparing(MccVO.MccTreeNodeVO::getSortNo, Comparator.nullsLast(Integer::compareTo)));
    }

    /**
     * 构造编码vo对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造categorypath对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param vo VO 输入值，参与 vo 的查询、校验、转换、写入或日志摘要
     * @param level1Id level 1 ID 输入值，参与 level1ID 的查询、校验、转换、写入或日志摘要
     * @param level2Id level 2 ID 输入值，参与 level2ID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 构造policyvo对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造countryname对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param vo VO 输入值，参与 vo 的查询、校验、转换、写入或日志摘要
     * @param countryCode country Code 输入值，参与 country编码 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 规范化cardschemelabel，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param cardScheme 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String cardSchemeLabel(String cardScheme) {
        SysDictDataQueryRequest query = new SysDictDataQueryRequest();
        query.setDictType(CARD_BRAND_DICT);
        query.setDictValue(cardScheme);
        query.setLocale(DEFAULT_LOCALE);
        List<SysDictDataDTO> rows = adminDictService.listDictData(query);
        return rows.isEmpty() ? cardScheme : rows.get(0).getDictLabel();
    }

    /**
     * 构造exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 规范化option，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param nameCn name Cn 输入值，参与 namecn 的查询、校验、转换、写入或日志摘要
     * @param nameEn name En 输入值，参与 nameen 的查询、校验、转换、写入或日志摘要
     * @param nodeType node Type 输入值，参与 nodetype 的查询、校验、转换、写入或日志摘要
     * @param parentId parent ID 输入值，参与 parentID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 更新softdeletelevel1，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     */
    private void softDeleteLevel1(MccEntities.BaseMccLevel1DO row) {
        row.setStatus(DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        level1Mapper.updateById(row);
    }

    /**
     * 更新softdeletelevel2，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     */
    private void softDeleteLevel2(MccEntities.BaseMccLevel2DO row) {
        row.setStatus(DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateTime(LocalDateTime.now());
        level2Mapper.updateById(row);
    }

    /**
     * 查询一级分类，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private MccEntities.BaseMccLevel1DO getLevel1(Long id) {
        MccEntities.BaseMccLevel1DO row = level1Mapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("一级分类不存在");
        }
        return row;
    }

    /**
     * 查询二级分类，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private MccEntities.BaseMccLevel2DO getLevel2(Long id) {
        MccEntities.BaseMccLevel2DO row = level2Mapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("二级分类不存在");
        }
        return row;
    }

    /**
     * 查询按 ID 定位的编码，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private MccEntities.BaseMccCodeDO getCodeById(Long id) {
        MccEntities.BaseMccCodeDO row = codeMapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("MCC 编码不存在");
        }
        return row;
    }

    /**
     * 查询策略配置，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private MccEntities.BaseMccRiskPolicyDO getPolicy(Long id) {
        MccEntities.BaseMccRiskPolicyDO row = riskPolicyMapper.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), NOT_DELETED)) {
            throw badRequest("风险策略不存在");
        }
        return row;
    }

    /**
     * 整理基础level1查询，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LambdaQueryWrapper<MccEntities.BaseMccLevel1DO> baseLevel1Query() {
        return Wrappers.<MccEntities.BaseMccLevel1DO>lambdaQuery()
                .eq(MccEntities.BaseMccLevel1DO::getDeleted, NOT_DELETED)
                .orderByAsc(MccEntities.BaseMccLevel1DO::getSortNo)
                .orderByAsc(MccEntities.BaseMccLevel1DO::getId);
    }

    /**
     * 整理基础level2查询，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LambdaQueryWrapper<MccEntities.BaseMccLevel2DO> baseLevel2Query() {
        return Wrappers.<MccEntities.BaseMccLevel2DO>lambdaQuery()
                .eq(MccEntities.BaseMccLevel2DO::getDeleted, NOT_DELETED)
                .orderByAsc(MccEntities.BaseMccLevel2DO::getSortNo)
                .orderByAsc(MccEntities.BaseMccLevel2DO::getId);
    }

    /**
     * 整理基础编码查询，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LambdaQueryWrapper<MccEntities.BaseMccCodeDO> baseCodeQuery() {
        return Wrappers.<MccEntities.BaseMccCodeDO>lambdaQuery()
                .eq(MccEntities.BaseMccCodeDO::getDeleted, NOT_DELETED);
    }

    /**
     * 整理基础policy查询，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LambdaQueryWrapper<MccEntities.BaseMccRiskPolicyDO> basePolicyQuery() {
        return Wrappers.<MccEntities.BaseMccRiskPolicyDO>lambdaQuery()
                .eq(MccEntities.BaseMccRiskPolicyDO::getDeleted, NOT_DELETED);
    }

    /**
     * 解析normalizerequired，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(message);
        }
        return value.trim();
    }

    /**
     * 整理默认ifblank，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param defaultValue default Value 输入值，参与 默认value 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 规范化trimtonull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 整理默认sort，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param sortNo sort No 输入值，参与 sortno 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int defaultSort(Integer sortNo) {
        return sortNo == null ? 100 : sortNo;
    }

    /**
     * 整理默认状态，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int defaultStatus(Integer status) {
        return status == null ? ENABLED : validStatus(status);
    }

    /**
     * 整理有效状态，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int validStatus(Integer status) {
        if (!Objects.equals(status, ENABLED) && !Objects.equals(status, DISABLED)) {
            throw badRequest("status must be 0 or 1");
        }
        return status;
    }

    /**
     * 整理默认flag，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param flag flag 输入值，参与 flag 的查询、校验、转换、写入或日志摘要
     * @param defaultValue default Value 输入值，参与 默认value 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int defaultFlag(Integer flag, int defaultValue) {
        if (flag == null) {
            return defaultValue;
        }
        if (!Objects.equals(flag, ENABLED) && !Objects.equals(flag, DISABLED)) {
            throw badRequest("flag must be 0 or 1");
        }
        return flag;
    }

    /**
     * 整理nonnull计数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param count count 输入值，参与 计数 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long nonNullCount(Long count) {
        return count == null ? 0L : count;
    }

    /**
     * 整理bad请求，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }
}
