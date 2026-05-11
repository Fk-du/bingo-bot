package com.bingo.app.modules.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.bingo.app.exception.PlayerActionException;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.wallet.entity.Transaction;
import com.bingo.app.modules.wallet.enums.TransactionStatus;
import com.bingo.app.modules.wallet.enums.TransactionType;
import com.bingo.app.modules.wallet.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void transferPointsRejectsCrossTreeTransfer() {
        User owner = User.builder()
                .id(1L)
                .telegramId(100L)
                .role(Role.SUPER_ADMIN)
                .balance(new BigDecimal("100.00"))
                .build();

        User unrelatedAdmin = User.builder()
                .id(2L)
                .telegramId(200L)
                .role(Role.ADMIN)
                .parentId(99L)
                .balance(BigDecimal.ZERO)
                .build();

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(unrelatedAdmin));

        PlayerActionException ex = assertThrows(
                PlayerActionException.class,
                () -> walletService.transferPoints(1L, 2L, new BigDecimal("10.00"))
        );

        assertEquals(
                "You can only transfer points to direct children in your hierarchy.",
                ex.getUserMessage()
        );
    }

    @Test
    void fundAgentCreditsDirectChildAndRecordsTransaction() {
        User agent = User.builder()
                .id(2L)
                .telegramId(200L)
                .role(Role.ADMIN)
                .parentId(1L)
                .balance(BigDecimal.ZERO)
                .build();

        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(agent));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction tx = walletService.fundAgent(1L, 2L, new BigDecimal("25.00"));

        assertEquals(TransactionType.AGENT_FUND, tx.getType());
        assertEquals(TransactionStatus.APPROVED, tx.getStatus());
        assertEquals(new BigDecimal("25.00"), tx.getAmount());
    }
}
