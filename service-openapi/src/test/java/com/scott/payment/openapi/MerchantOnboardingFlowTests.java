package com.scott.payment.openapi;

import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.dto.security.MerchantInfoDTO;
import com.scott.payment.openapi.dto.security.MerchantKeyRevisionDTO;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.ServerSecurityMaterialDTO;
import com.scott.payment.openapi.service.MerchantSecurityService;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOnboardingFlowTests
 * @date : 2026-05-30 09:30
 * @email : scott_x@163.com
 * @description : 商户开户、密钥交付、密钥查询和密钥迭代记录集成测试
 * @status : create
 */
@Slf4j
@ActiveProfiles("mysql-test")
@SpringBootTest(classes = OpenApiApplication.class)
@Sql(scripts = "/sql/openapi-merchant-security-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MerchantOnboardingFlowTests {

    /**
     * 主测试商户号。
     */
    private static final String MERCHANT_ID = "210001";

    /**
     * 第二个测试商户号，用于验证多商户查询。
     */
    private static final String SECOND_MERCHANT_ID = "210002";

    /**
     * 商户基础数据和密钥服务，内部使用 MyBatisPlus Mapper 访问 MySQL。
     */
    private final MerchantSecurityService merchantSecurityService;

    /**
     * JDBC 模板只用于清理当前测试商户数据。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 密钥材料工厂用于计算日志指纹，避免输出完整密钥。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    MerchantOnboardingFlowTests(MerchantSecurityService merchantSecurityService,
                                JdbcTemplate jdbcTemplate,
                                OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.merchantSecurityService = merchantSecurityService;
        this.jdbcTemplate = jdbcTemplate;
        this.keyMaterialFactory = keyMaterialFactory;
    }

    /**
     * 每个用例执行前只清理本类测试商户，避免历史执行数据影响断言。
     */
    @BeforeEach
    void cleanMerchantData() {
        MerchantOpenApiTestSupport.cleanMerchantSecurityData(
                jdbcTemplate,
                List.of(MERCHANT_ID, SECOND_MERCHANT_ID)
        );
    }

    /**
     * 验证开户时系统生成哪些密钥、哪些交付给商户、哪些由平台服务端保留。
     */
    @Test
    void shouldProvisionMerchantAndExplainDeliveredMaterials() {
        MerchantSecurityMaterialDTO materialDTO = provisionPrimaryMerchant();

        assertThat(materialDTO.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(materialDTO.getMerchantKey()).isNotBlank();
        assertThat(materialDTO.getPlatformPublicKeyX509Base64()).isNotBlank();
        assertThat(materialDTO.getMerchantResponsePublicKeyX509Base64()).isNotBlank();
        assertThat(materialDTO.getMerchantResponsePrivateKeyPkcs8Base64()).isNotBlank();

        log.info("开户成功-商户号：{}，商户必拿材料：merchantKey指纹: {}，平台公钥指纹: {}，商户响应私钥指纹: {}",
                materialDTO.getMerchantId(),
                keyMaterialFactory.fingerprint(materialDTO.getMerchantKey()),
                keyMaterialFactory.fingerprint(materialDTO.getPlatformPublicKeyX509Base64()),
                keyMaterialFactory.fingerprint(materialDTO.getMerchantResponsePrivateKeyPkcs8Base64()));
        log.info("开户成功-平台保留材料：平台私钥只由服务端保存；商户响应私钥只交付商户，平台只保存商户响应公钥；所有密钥按merchantId关联。");
    }

    /**
     * 验证可以根据商户号查询商户侧密钥材料和服务端内部密钥材料。
     */
    @Test
    void shouldQueryClientAndServerSecurityMaterialByMerchantId() {
        MerchantSecurityMaterialDTO provisionedMaterial = provisionPrimaryMerchant();

        MerchantSecurityMaterialDTO clientMaterial = merchantSecurityService.getMerchantClientSecurityMaterial(MERCHANT_ID);
        ServerSecurityMaterialDTO serverMaterial = merchantSecurityService.getServerSecurityMaterial(MERCHANT_ID);

        assertThat(clientMaterial.getMerchantKey()).isEqualTo(provisionedMaterial.getMerchantKey());
        assertThat(clientMaterial.getPlatformPublicKeyX509Base64()).isEqualTo(provisionedMaterial.getPlatformPublicKeyX509Base64());
        assertThat(clientMaterial.getMerchantResponsePrivateKeyPkcs8Base64()).isNull();
        assertThat(clientMaterial.getMerchantResponsePublicKeyX509Base64()).isEqualTo(provisionedMaterial.getMerchantResponsePublicKeyX509Base64());
        assertThat(serverMaterial.getMerchantKey()).isEqualTo(provisionedMaterial.getMerchantKey());
        assertThat(serverMaterial.getPlatformPrivateKeyPkcs8Base64()).isNotBlank();
        assertThat(serverMaterial.getMerchantResponsePublicKeyX509Base64()).isEqualTo(provisionedMaterial.getMerchantResponsePublicKeyX509Base64());

        log.info("商户侧查询成功-商户号：{}，merchantKey指纹: {}，平台公钥指纹: {}，响应公钥指纹: {}",
                clientMaterial.getMerchantId(),
                keyMaterialFactory.fingerprint(clientMaterial.getMerchantKey()),
                keyMaterialFactory.fingerprint(clientMaterial.getPlatformPublicKeyX509Base64()),
                keyMaterialFactory.fingerprint(clientMaterial.getMerchantResponsePublicKeyX509Base64()));
        log.info("服务端查询成功-商户号：{}，merchantKey指纹: {}，平台私钥存在: {}，响应公钥指纹: {}",
                serverMaterial.getMerchantId(),
                serverMaterial.getMerchantKeyFingerprint(),
                serverMaterial.getPlatformPrivateKeyPkcs8Base64() != null,
                serverMaterial.getMerchantResponseKeyFingerprint());
    }

    /**
     * 验证商户基础资料列表、密钥迭代记录列表和 JWT 密钥轮换记录。
     */
    @Test
    void shouldQueryMerchantListAndKeyRevisionRecords() {
        provisionPrimaryMerchant();
        merchantSecurityService.provisionMerchantSecurityMaterial(MerchantOpenApiTestSupport.buildMerchantSeed(SECOND_MERCHANT_ID));

        List<MerchantInfoDTO> merchantInfoList = merchantSecurityService.listMerchantInfos();
        List<MerchantKeyRevisionDTO> beforeRevisionList = merchantSecurityService.listMerchantKeyRevisions(MERCHANT_ID);
        MerchantKeyRevisionDTO rotatedRevision = merchantSecurityService.rotateMerchantJwtKey(MERCHANT_ID, "jwt-v2");
        List<MerchantKeyRevisionDTO> afterRevisionList = merchantSecurityService.listMerchantKeyRevisions(MERCHANT_ID);

        assertThat(merchantInfoList)
                .extracting(MerchantInfoDTO::getMerchantId)
                .contains(MERCHANT_ID, SECOND_MERCHANT_ID);
        assertThat(beforeRevisionList).hasSizeGreaterThanOrEqualTo(2);
        assertThat(rotatedRevision.getKeyVersion()).isEqualTo("jwt-v2");
        assertThat(afterRevisionList)
                .extracting(MerchantKeyRevisionDTO::getKeyVersion)
                .contains("jwt-v1", "jwt-v2", MERCHANT_ID);

        log.info("商户列表查询成功-本次测试商户：{}",
                merchantInfoList.stream()
                        .filter(item -> List.of(MERCHANT_ID, SECOND_MERCHANT_ID).contains(item.getMerchantId()))
                        .map(MerchantInfoDTO::getMerchantId)
                        .toList());
        log.info("密钥迭代记录查询成功-轮换前记录数：{}，轮换后记录数：{}，新JWT密钥指纹：{}",
                beforeRevisionList.size(),
                afterRevisionList.size(),
                rotatedRevision.getKeyFingerprint());
    }

    /**
     * 验证 SDK live 联调商户不会被测试开户服务误重建密钥。
     */
    @Test
    void shouldRejectProvisionForSdkLiveMerchant() {
        assertThatThrownBy(() -> merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(MerchantOpenApiTestSupport.SDK_LIVE_MERCHANT_ID)
        ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reserved for SDK live tests");
    }

    /**
     * 验证测试数据清理工具不会误删 SDK live 联调商户。
     */
    @Test
    void shouldRejectCleanupForSdkLiveMerchant() {
        assertThatThrownBy(() -> MerchantOpenApiTestSupport.cleanMerchantSecurityData(
                jdbcTemplate,
                List.of(MerchantOpenApiTestSupport.SDK_LIVE_MERCHANT_ID)
        ))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must not be cleaned by automated tests");
    }

    /**
     * 创建主测试商户并返回开户交付材料。
     *
     * @return 商户开户交付材料
     */
    private MerchantSecurityMaterialDTO provisionPrimaryMerchant() {
        return merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(MERCHANT_ID)
        );
    }
}
