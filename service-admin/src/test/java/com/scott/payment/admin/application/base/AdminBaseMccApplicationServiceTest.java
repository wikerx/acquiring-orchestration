package com.scott.payment.admin.application.base;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.base.MccRequests;
import com.scott.payment.admin.dto.base.MccVO;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.base.MccEntities;
import com.scott.payment.admin.mapper.BaseMccCodeMapper;
import com.scott.payment.admin.mapper.BaseMccLevel1Mapper;
import com.scott.payment.admin.mapper.BaseMccLevel2Mapper;
import com.scott.payment.admin.mapper.BaseMccRiskPolicyMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseMccApplicationServiceTest
 * @date : 2026-06-27 16:49
 * @email : scott_x@163.com
 * @description : Admin Base MCC Application Service Test 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
class AdminBaseMccApplicationServiceTest {

    @Mock
    /**
     * level 1 Mapper 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BaseMccLevel1Mapper level1Mapper;
    @Mock
    /**
     * level 2 Mapper 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BaseMccLevel2Mapper level2Mapper;
    @Mock
    /**
     * code Mapper，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BaseMccCodeMapper codeMapper;
    @Mock
    /**
     * risk Policy Mapper 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BaseMccRiskPolicyMapper riskPolicyMapper;
    @Mock
    /**
     * dict Data Mapper 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SysDictDataMapper dictDataMapper;
    @Mock
    /**
     * admin Dict Service 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private AdminDictService adminDictService;
    @Mock
    /**
     * ISO Country Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持国家地区；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private IsoCountryMapper isoCountryMapper;
    @Mock
    /**
     * merchant Info Mapper 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BaseMerchantInfoMapper merchantInfoMapper;
    @Mock
    /**
     * excel Export Service 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExcelExportService excelExportService;
    @Mock
    /**
     * excel I 18 n Message Resolver，用于保存 Admin Base MCC Application Service Test 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExcelI18nMessageResolver excelI18nMessageResolver;
    @Mock
    /**
     * excel Locale Resolver，用于保存 Admin Base MCC Application Service Test 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private ExcelLocaleResolver excelLocaleResolver;

    /**
     * service 依赖，用于 Admin Base MCC Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private AdminBaseMccApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminBaseMccApplicationService(
                level1Mapper,
                level2Mapper,
                codeMapper,
                riskPolicyMapper,
                dictDataMapper,
                adminDictService,
                isoCountryMapper,
                merchantInfoMapper,
                excelExportService,
                excelI18nMessageResolver,
                excelLocaleResolver
        );
    }

    @Test
    void shouldBuildTreeWithoutLevel3() {
        when(level1Mapper.selectList(any())).thenReturn(List.of(level1()));
        when(level2Mapper.selectList(any())).thenReturn(List.of(level2()));
        when(codeMapper.selectList(any())).thenReturn(List.of(mccCode()));

        List<MccVO.MccTreeNodeVO> tree = service.tree(new MccRequests.MccTreeQueryRequest());

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getNodeType()).isEqualTo("LEVEL1");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        MccVO.MccTreeNodeVO level2 = tree.get(0).getChildren().get(0);
        assertThat(level2.getNodeType()).isEqualTo("LEVEL2");
        assertThat(level2.getChildren()).hasSize(1);
        assertThat(level2.getChildren().get(0).getNodeType()).isEqualTo("MCC_CODE");
        assertThat(level2.getChildren().get(0).getLevel()).isEqualTo(3);
    }

    @Test
    void shouldRejectAllAsCardScheme() {
        when(dictDataMapper.selectList(any())).thenReturn(cardSchemeRows());

        MccRequests.MccRiskPolicySaveRequest request = basePolicyRequest();
        request.setCardSchemes(List.of("ALL"));

        assertThatThrownBy(() -> service.createPolicies(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("card_brand 不允许使用 ALL");
    }

    @Test
    void shouldRequireChannelCodeWhenChannelScopeSpecific() {
        when(dictDataMapper.selectList(any())).thenReturn(cardSchemeRows());
        when(codeMapper.selectCount(any())).thenReturn(1L);

        MccRequests.MccRiskPolicySaveRequest request = basePolicyRequest();
        request.setChannelScope("SPECIFIC");
        request.setChannelCode("");

        assertThatThrownBy(() -> service.createPolicies(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("channel_code 必填");
    }

    @Test
    void shouldExpandAllCardSchemesIntoRealPolicyRows() {
        when(dictDataMapper.selectList(any())).thenReturn(cardSchemeRows());
        when(codeMapper.selectCount(any())).thenReturn(1L);
        when(riskPolicyMapper.selectCount(any())).thenReturn(0L);
        when(codeMapper.selectOne(any())).thenReturn(mccCode());
        when(adminDictService.listDictData(any())).thenReturn(List.of(dictDataDTO("Visa / 维萨卡")));
        doAnswer(invocation -> {
            MccEntities.BaseMccRiskPolicyDO row = invocation.getArgument(0);
            row.setId(row.getCardScheme().equals("VISA") ? 1L : 2L);
            return 1;
        }).when(riskPolicyMapper).insert(any(MccEntities.BaseMccRiskPolicyDO.class));

        MccRequests.MccRiskPolicySaveRequest request = basePolicyRequest();
        request.setCardSchemes(null);
        request.setSelectAllCardSchemes(true);

        List<MccVO.MccRiskPolicyVO> rows = service.createPolicies(request);

        assertThat(rows).extracting(MccVO.MccRiskPolicyVO::getCardScheme)
                .containsExactlyInAnyOrder("VISA", "MASTERCARD");
    }

    @Test
    void shouldRejectDeletingLevel1WhenLevel2Exists() {
        when(level2Mapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        MccRequests.MccDeleteRequest request = new MccRequests.MccDeleteRequest();
        request.setNodeType("LEVEL1");
        request.setId(1001L);

        assertThatThrownBy(() -> service.deleteCategory(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存在二级分类");
    }

    private MccRequests.MccRiskPolicySaveRequest basePolicyRequest() {
        MccRequests.MccRiskPolicySaveRequest request = new MccRequests.MccRiskPolicySaveRequest();
        request.setMccCode("5411");
        request.setCardSchemes(List.of("VISA"));
        request.setChannelScope("ALL");
        request.setChannelCode("");
        request.setCountryScope("ALL");
        request.setCountryCode("");
        request.setRiskLevel("LOW");
        request.setAllowOnboarding(1);
        request.setAllowAcquiring(1);
        request.setRequireEnhancedReview(0);
        request.setStatus(1);
        return request;
    }

    private List<SysDictDataDO> cardSchemeRows() {
        return List.of(dictData("VISA", "Visa / 维萨卡"), dictData("MASTERCARD", "Mastercard / 万事达卡"));
    }

    private SysDictDataDO dictData(String value, String label) {
        SysDictDataDO row = new SysDictDataDO();
        row.setId((long) value.hashCode());
        row.setDictValue(value);
        row.setDictLabel(label);
        return row;
    }

    private SysDictDataDTO dictDataDTO(String label) {
        SysDictDataDTO dto = new SysDictDataDTO();
        dto.setDictLabel(label);
        return dto;
    }

    private MccEntities.BaseMccCodeDO mccCode() {
        MccEntities.BaseMccCodeDO row = new MccEntities.BaseMccCodeDO();
        row.setId(3001L);
        row.setLevel1Id(1001L);
        row.setLevel2Id(2001L);
        row.setMccCode("5411");
        row.setNameCn("杂货店、超市");
        row.setNameEn("Grocery Stores, Supermarkets");
        row.setMccType("COMMON");
        row.setRiskLevel("LOW");
        row.setStatus(1);
        row.setSortNo(10);
        row.setDeleted(0L);
        return row;
    }

    private MccEntities.BaseMccLevel1DO level1() {
        MccEntities.BaseMccLevel1DO row = new MccEntities.BaseMccLevel1DO();
        row.setId(1001L);
        row.setLevel1Code("RETAIL");
        row.setNameCn("零售");
        row.setNameEn("Retail");
        row.setStatus(1);
        row.setSortNo(10);
        row.setDeleted(0L);
        return row;
    }

    private MccEntities.BaseMccLevel2DO level2() {
        MccEntities.BaseMccLevel2DO row = new MccEntities.BaseMccLevel2DO();
        row.setId(2001L);
        row.setLevel1Id(1001L);
        row.setLevel2Code("GROCERY");
        row.setNameCn("杂货");
        row.setNameEn("Grocery");
        row.setStatus(1);
        row.setSortNo(10);
        row.setDeleted(0L);
        return row;
    }
}
