package com.scott.payment.admin.application.channel;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.api.channel.AdminChannelPresentationController;
import com.scott.payment.admin.dto.channel.ChannelPresentationDTOs.*;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.web.upload.ManagedImageStore;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminChannelPresentationApplicationServiceTest {
    private final ChannelInfoMapper mapper = mock(ChannelInfoMapper.class);
    private final ManagedImageStore images = mock(ManagedImageStore.class);
    private final AdminChannelPresentationApplicationService service = new AdminChannelPresentationApplicationService(mapper, images);
    private static final String VERSION = "a".repeat(32);
    private static final String KEY = "images/channel/10/" + VERSION + "-" + "b".repeat(64) + ".png";

    @BeforeEach void setup() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName()); TableInfoHelper.initTableInfo(assistant, ChannelInfoDO.class);
        var account = new InternalAuthAccount(); account.setUserId(1L); account.setAccountId(2L); account.setAppCode("ADMIN");
        account.setPermissions(List.of("*:*:*")); InternalAuthContextHolder.set(account);
    }
    @AfterEach void clear() { InternalAuthContextHolder.clear(); }
    private ChannelInfoDO channel() {
        var entity = new ChannelInfoDO(); entity.setId(10L); entity.setChannelCode("MPGS");
        entity.setChannelCnName("测试渠道"); entity.setChannelEnName("Test Channel"); entity.setLogoObjectKey(KEY);
        return entity;
    }
    @ParameterizedTest @EnumSource(Scope.class)
    void correspondingPageListAndDetailPermissionsAllowOnlyPresentationReads(Scope scope) {
        when(mapper.selectList(any())).thenReturn(List.of(channel()));
        when(mapper.selectOne(any())).thenReturn(channel());
        when(images.read(KEY, "channel/10")).thenReturn(new byte[]{1, 2});
        for (String permission : List.of(scope.listPermission(), scope.detailPermission())) {
            InternalAuthContextHolder.get().setPermissions(List.of(permission));
            var rows = service.lookup(scope, new LookupRequest(List.of(10L), List.of()));
            assertThat(rows).hasSize(1); assertThat(rows.get(0).logoVersion()).isEqualTo(VERSION);
            assertThat(service.logo(scope, 10L, VERSION)).containsExactly(1, 2);
        }
        verify(images, times(2)).read(KEY, "channel/10");
    }
    @Test void wrongPagePermissionCannotChooseAnotherScope() {
        InternalAuthContextHolder.get().setPermissions(List.of("channel:capability:list"));
        assertThatThrownBy(() -> service.lookup(Scope.ORDER, new LookupRequest(List.of(10L), List.of()))).hasMessageContaining("无权");
        assertThatThrownBy(() -> service.logo(Scope.ORDER, 10L, VERSION)).hasMessageContaining("无权");
        verifyNoInteractions(mapper, images);
    }
    @Test void anonymousAndMerchantContextsAreRejectedBeforeDatabaseOrStorage() {
        InternalAuthContextHolder.get().setAppCode("MERCHANT");
        assertThatThrownBy(() -> service.lookup(Scope.INFO, new LookupRequest(List.of(), List.of("MPGS")))).hasMessageContaining("无权");
        InternalAuthContextHolder.clear();
        assertThatThrownBy(() -> service.logo(Scope.INFO, 10L, VERSION)).hasMessageContaining("无权");
        verifyNoInteractions(mapper, images);
    }
    @Test void boundedBatchUsesOneDeletedFilteredQueryAndProjectsOnlyDisplayFields() throws Exception {
        when(mapper.selectList(any())).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> query = call.getArgument(0);
            assertThat(query.getSqlSelect()).doesNotContain("metadata", "credential");
            assertThat(query.getSqlSegment()).contains("deleted", "id", "channel_code");
            return List.of(channel());
        });
        var result = service.lookup(Scope.ORDER, new LookupRequest(List.of(10L), List.of("MPGS")));
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).contains("channelCnName", "channelEnName", VERSION).doesNotContain("images/", "logoObjectKey", "metadata");
        verify(mapper).selectList(any()); verifyNoMoreInteractions(mapper); verifyNoInteractions(images);
    }
    @Test void emptyLookupDoesNotReadAllChannels() {
        assertThat(service.lookup(Scope.INFO, new LookupRequest(null, null))).isEmpty();
        verifyNoInteractions(mapper, images);
    }
    @Test void rejectsUnboundedAndInvalidReferences() {
        for (var request : List.of(new LookupRequest(Collections.nCopies(101, 10L), null),
                new LookupRequest(List.of(-1L), null), new LookupRequest(null, List.of(" ")),
                new LookupRequest(null, List.of("a".repeat(65))), new LookupRequest(null, List.of("bad\ncode")))) {
            assertThatThrownBy(() -> service.lookup(Scope.INFO, request)).hasMessageContaining("不合法");
        }
        verifyNoInteractions(mapper, images);
    }
    @Test void staleOrMissingLogoDoesNotReadStorage() {
        when(mapper.selectOne(any())).thenReturn(channel());
        assertThatThrownBy(() -> service.logo(Scope.INFO, 10L, "c".repeat(32))).hasMessageContaining("不可访问");
        when(mapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.logo(Scope.INFO, 10L, VERSION)).hasMessageContaining("不可访问");
        verifyNoInteractions(images);
    }
    @Test void matchingVersionReadsOnlyOwnedPrivateChannelImage() {
        InternalAuthContextHolder.get().setPermissions(List.of("transaction:order:detail"));
        when(mapper.selectOne(any())).thenReturn(channel());
        when(images.read(KEY, "channel/10")).thenReturn(new byte[]{1, 2});
        assertThat(service.logo(Scope.ORDER, 10L, VERSION)).containsExactly(1, 2);
        verify(images).read(KEY, "channel/10");
    }

    /** 经 MVC 校验显式参数绑定、字段投影及私有图片响应头，覆盖实际打包后的调用边界。 */
    @Test void mvcBindsScopeIdAndVersionAndReturnsOnlyPrivateDisplayContract() throws Exception {
        InternalAuthContextHolder.get().setPermissions(List.of("transaction:order:list"));
        when(mapper.selectList(any())).thenReturn(List.of(channel()));
        when(mapper.selectOne(any())).thenReturn(channel());
        when(images.read(KEY, "channel/10")).thenReturn(new byte[]{1, 2});
        var mvc = standaloneSetup(new AdminChannelPresentationController(service)).build();
        mvc.perform(post("/admin/channel/presentation/search").param("scope", "ORDER")
                        .contentType("application/json").content("{\"ids\":[10],\"codes\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].logoVersion").value(VERSION))
                .andExpect(jsonPath("$.data[0].logoObjectKey").doesNotExist());
        mvc.perform(get("/admin/channel/presentation/{id}/logo", 10).param("scope", "ORDER").param("version", VERSION))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[]{1, 2}));
    }

    /** 未定义页面范围必须在 MVC 参数转换时拒绝，不能退化为任意权限读取。 */
    @Test void mvcRejectsUnknownScopeBeforeReadingChannelData() throws Exception {
        var mvc = standaloneSetup(new AdminChannelPresentationController(service)).build();
        mvc.perform(post("/admin/channel/presentation/search").param("scope", "UNKNOWN")
                        .contentType("application/json").content("{\"ids\":[10]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mapper, images);
    }
}
