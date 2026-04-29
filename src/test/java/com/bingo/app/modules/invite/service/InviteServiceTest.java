package com.bingo.app.modules.invite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.exception.InviteRegistrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bingo.app.modules.invite.entity.InviteCode;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.invite.repository.InviteCodeRepository;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.user.service.UserService;
import com.bingo.app.modules.invite.service.InviteService;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private InviteService inviteService;

    @Test
    void registerWithInviteRejectsUnknownCode() {
        when(userService.findByTelegramId(123L)).thenReturn(Optional.empty());
        when(inviteCodeRepository.findByCode("missing")).thenReturn(Optional.empty());

        InviteRegistrationException ex = assertThrows(
                InviteRegistrationException.class,
                () -> inviteService.registerWithInvite(123L, "missing")
        );

        assertEquals("This invite link is invalid. Ask your admin for a new link.", ex.getUserMessage());
    }

    @Test
    void registerWithInviteRejectsInactiveCode() {
        InviteCode invite = InviteCode.builder()
                .id(10L)
                .code("agent_old01")
                .adminId(20L)
                .active(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.findByTelegramId(123L)).thenReturn(Optional.empty());
        when(inviteCodeRepository.findByCode("agent_old01")).thenReturn(Optional.of(invite));

        InviteRegistrationException ex = assertThrows(
                InviteRegistrationException.class,
                () -> inviteService.registerWithInvite(123L, "agent_old01")
        );

        assertEquals("This invite link has already been used or was deactivated.", ex.getUserMessage());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void registerWithInviteCreatesAdminFromSuperAdminInviteAndDeactivatesCode() {
        InviteCode invite = InviteCode.builder()
                .id(10L)
                .code("agent_live01")
                .adminId(20L)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        User superAdmin = User.builder()
                .id(20L)
                .telegramId(999L)
                .role(Role.SUPER_ADMIN)
                .createdAt(LocalDateTime.now())
                .build();

        User createdAdmin = User.builder()
                .id(30L)
                .telegramId(123L)
                .role(Role.ADMIN)
                .parentId(20L)
                .balance(BigDecimal.ZERO)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.findByTelegramId(123L)).thenReturn(Optional.empty());
        when(inviteCodeRepository.findByCode("agent_live01")).thenReturn(Optional.of(invite));
        when(userRepository.findById(20L)).thenReturn(Optional.of(superAdmin));
        when(userService.createAdmin(123L, 20L, BigDecimal.ZERO)).thenReturn(createdAdmin);

        User actual = inviteService.registerWithInvite(123L, "agent_live01");

        assertEquals(createdAdmin, actual);

        ArgumentCaptor<InviteCode> captor = ArgumentCaptor.forClass(InviteCode.class);
        verify(inviteCodeRepository).save(captor.capture());
        assertFalse(captor.getValue().isActive());
    }

    @Test
    void registerWithInviteRejectsAlreadyRegisteredUsers() {
        User existingUser = User.builder()
                .id(40L)
                .telegramId(123L)
                .role(Role.PLAYER)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.findByTelegramId(123L)).thenReturn(Optional.of(existingUser));

        InviteRegistrationException ex = assertThrows(
                InviteRegistrationException.class,
                () -> inviteService.registerWithInvite(123L, "agent_live01")
        );

        assertEquals("Your account is already registered. Use /start to open your menu.", ex.getUserMessage());
        verify(inviteCodeRepository, never()).findByCode(any());
    }
}
