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
 * @description : AdminBaseMccApplicationServiceTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class AdminBaseMccApplicationServiceTest {

    @Mock
    /**
     * level1 Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private BaseMccLevel1Mapper level1Mapper;
    @Mock
    /**
     * level2 Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private BaseMccLevel2Mapper level2Mapper;
    @Mock
    /**
     * code Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private BaseMccCodeMapper codeMapper;
    @Mock
    /**
     * risk Policy Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private BaseMccRiskPolicyMapper riskPolicyMapper;
    @Mock
    /**
     * dict Data Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private SysDictDataMapper dictDataMapper;
    @Mock
    /**
     * admin Dict Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private AdminDictService adminDictService;
    @Mock
    /**
     * iso Country Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private IsoCountryMapper isoCountryMapper;
    @Mock
    /**
     * merchant Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private BaseMerchantInfoMapper merchantInfoMapper;
    @Mock
    /**
     * excel Export Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private ExcelExportService excelExportService;
    @Mock
    /**
     * excel I18n Message Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private ExcelI18nMessageResolver excelI18nMessageResolver;
    @Mock
    /**
     * excel Locale Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private ExcelLocaleResolver excelLocaleResolver;

    /**
     * service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
