package com.scott.payment.admin.application.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.api.auth.AdminAvatarController;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.auth.BrowserSessionCookieSupport;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import jakarta.servlet.http.Cookie;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.scott.payment.component.core.auth.*;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.web.upload.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminAvatarApplicationServiceTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SafeImageProcessor processor = mock(SafeImageProcessor.class);
    private final ManagedImageStore store = mock(ManagedImageStore.class);
    private final AdminAvatarApplicationService service = new AdminAvatarApplicationService(users, processor, store);
    private final MultipartFile file = mock(MultipartFile.class);
    @BeforeEach void setup() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), ""); assistant.setCurrentNamespace(getClass().getName()); TableInfoHelper.initTableInfo(assistant, SysUserDO.class);
        var account = new InternalAuthAccount(); account.setUserId(7L); account.setAccountId(8L); account.setAppCode("ADMIN"); account.setMerchantId("M1"); account.setPermissions(List.of()); InternalAuthContextHolder.set(account);
    }
    @AfterEach void clear() { InternalAuthContextHolder.clear(); }
    private SysUserDO user() { var u = new SysUserDO(); u.setId(7L); u.setStatus(1); u.setDeleted(0L); return u; }
    @Test void unauthorizedAppNeverReadsFileOrStorage() {
        InternalAuthContextHolder.get().setAppCode("OTHER"); assertThatThrownBy(() -> service.replace(file)).hasMessageContaining("登录状态"); verifyNoInteractions(users, file, processor, store);
    }
    @Test void disabledUserFailsBeforeImageValidation() {
        var u = user(); u.setStatus(0); when(users.selectById(7L)).thenReturn(u);
        assertThatThrownBy(() -> service.replace(file)).hasMessageContaining("不可访问"); verifyNoInteractions(processor, file, store);
    }
    @Test void successfulUploadUsesOnlySessionUserAndReturnsOpaqueVersion() {
        when(users.selectById(7L)).thenReturn(user()); byte[] png = {1}; when(processor.process(file, true)).thenReturn(png);
        String key = "images/avatar/7/" + "a".repeat(32) + "-" + "b".repeat(64) + ".png";
        when(store.write("avatar/7", png)).thenReturn(key); when(users.update(isNull(), any())).thenReturn(1);
        assertThat(service.replace(file)).isEqualTo("a".repeat(32)); verify(store).write("avatar/7", png); verify(users, never()).selectById(8L);
    }
    @Test void scanFailureDoesNotWriteStorageOrUpdateUser() {
        when(users.selectById(7L)).thenReturn(user()); when(processor.process(file, true)).thenThrow(new IllegalStateException("scan unavailable"));
        assertThatThrownBy(() -> service.replace(file)).hasMessageContaining("scan unavailable"); verifyNoInteractions(store); verify(users, never()).update(any(), any());
    }
    @Test void staleConcurrentUpdateIsRejected() {
        when(users.selectById(7L)).thenReturn(user()); when(processor.process(file, true)).thenReturn(new byte[]{1});
        when(store.write(eq("avatar/7"), any())).thenReturn("images/avatar/7/" + "a".repeat(32) + "-" + "b".repeat(64) + ".png");
        when(users.update(isNull(), any())).thenReturn(0); assertThatThrownBy(() -> service.replace(file)).hasMessageContaining("已被更新");
    }
    @Test void readUsesSessionOwnershipAndRemovalOfEmptyAvatarIsIdempotent() {
        var u = user(); when(users.selectById(7L)).thenReturn(u); service.remove(); verify(users, never()).update(any(), any());
        u.setAvatarUrl("reference"); service.read(); verify(store).read("reference", "avatar/7");
    }

    @Test
    void profileReadUsesAuthenticatedSubjectEvenWithoutWildcardGrant() throws Exception {
        var currentUser = user();
        currentUser.setAvatarUrl("self-reference");
        byte[] png = {1, 2};
        when(users.selectById(7L)).thenReturn(currentUser);
        when(store.read("self-reference", "avatar/7")).thenReturn(png);
        var mvc = mvcWithWildcardRule();

        mvc.perform(get("/admin/auth/avatar").param("userId", "9")
                        .cookie(new Cookie("VEXRA_ADMIN_SESSION", "test-session")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        verify(users).selectById(7L);
        verifyNoMoreInteractions(users);
    }

    @Test
    void profileUploadAndRemovalUseSessionWithoutWildcardGrant() throws Exception {
        var currentUser = user();
        when(users.selectById(7L)).thenReturn(currentUser);
        byte[] png = {1, 2};
        var upload = new MockMultipartFile("file", "avatar.png", "image/png", png);
        when(processor.process(eq(upload), eq(true))).thenReturn(png);
        String key = "images/avatar/7/" + "a".repeat(32) + "-" + "b".repeat(64) + ".png";
        when(store.write("avatar/7", png)).thenReturn(key);
        when(users.update(isNull(), any())).thenReturn(1);
        var mvc = mvcWithWildcardRule();

        mvc.perform(withCsrf(multipart("/admin/auth/avatar").file(upload).param("userId", "9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("a".repeat(32)));
        currentUser.setAvatarUrl(key);
        mvc.perform(withCsrf(delete("/admin/auth/avatar").param("userId", "9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("T200"));

        verify(processor).process(upload, true);
        verify(store).write("avatar/7", png);
        verify(store).deleteAfterCommit(key);
        verify(users, times(2)).selectById(7L);
        verify(users, times(2)).update(isNull(), any());
        verifyNoMoreInteractions(users);
    }

    @Test
    void profileEndpointsRequireLoginAndRejectCookieMutationsWithoutCsrf() throws Exception {
        var mvc = mvcWithWildcardRule();

        mvc.perform(get("/admin/auth/avatar")).andExpect(status().isUnauthorized());
        var cookie = new Cookie("VEXRA_ADMIN_SESSION", "test-session");
        mvc.perform(multipart("/admin/auth/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1}))
                        .cookie(cookie))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/admin/auth/avatar").cookie(cookie))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users, processor, store);
    }

    private MockMvc mvcWithWildcardRule() {
        var account = InternalAuthContextHolder.get();
        return standaloneSetup(new AdminAvatarController(service))
                .addInterceptors(new InternalAuthInterceptor("ADMIN", (app, auth, method, path, permission) -> {
                    if (path != null && permission == null) throw new ServiceException(ApiResultEnum.FORBIDDEN);
                    return account;
                }, List.of()))
                .build();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("VEXRA_ADMIN_SESSION", "test-session"),
                        new Cookie("VEXRA_ADMIN_CSRF", "test-csrf"))
                .header(BrowserSessionCookieSupport.CSRF_HEADER, "test-csrf");
    }

}
