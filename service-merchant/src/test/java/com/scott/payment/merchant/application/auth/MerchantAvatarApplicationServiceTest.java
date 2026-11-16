package com.scott.payment.merchant.application.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.scott.payment.component.core.auth.*;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.web.upload.*;
import org.junit.jupiter.api.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantAvatarApplicationServiceTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SafeImageProcessor processor = mock(SafeImageProcessor.class);
    private final ManagedImageStore store = mock(ManagedImageStore.class);
    private final MerchantAvatarApplicationService service = new MerchantAvatarApplicationService(users, processor, store);
    private final MultipartFile file = mock(MultipartFile.class);
    @BeforeEach void setup() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), ""); assistant.setCurrentNamespace(getClass().getName()); TableInfoHelper.initTableInfo(assistant, SysUserDO.class);
        var account = new InternalAuthAccount(); account.setUserId(7L); account.setAccountId(8L); account.setAppCode("MERCHANT"); account.setMerchantId("M1"); account.setPermissions(List.of()); InternalAuthContextHolder.set(account);
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
}
