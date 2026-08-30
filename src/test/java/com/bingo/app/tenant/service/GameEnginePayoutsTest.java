package com.bingo.app.tenant.service;

import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.entity.BingoClaim;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.entity.GameCard;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.repository.BingoClaimRepository;
import com.bingo.app.tenant.repository.CalledNumberRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Payout-rule tests for the shared-winner flow using mocked repositories.
 * Verifies: commission taken once, cent-perfect equal shares, game ends
 * immediately, winner cards flagged, and the 3-winner cap enforced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameEnginePayoutsTest {

    @Mock GameRepository gameRepository;
    @Mock CalledNumberRepository calledNumberRepository;
    @Mock GameCardRepository gameCardRepository;
    @Mock BingoClaimRepository bingoClaimRepository;
    @Mock WalletService walletService;
    @Mock CardService cardService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock TenantMapper tenantMapper;
    @Mock TenantRegistryHolder tenantRegistryHolder;
    @Mock TransactionTemplate transactionTemplate;
    @Mock UserRepository userRepository;

    GameEngineService engine;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        engine = new GameEngineService(gameRepository, calledNumberRepository, gameCardRepository,
                bingoClaimRepository, walletService, cardService, objectMapper,
                transactionTemplate, messagingTemplate, tenantMapper, null, userRepository);
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(mockTransaction()));
    }

    private TransactionStatus mockTransaction() {
        return org.mockito.Mockito.mock(TransactionStatus.class);
    }

    /** Holder shim so the constructor keeps its 11-arg shape without the real registry bean. */
    interface TenantRegistryHolder {}

    private Game game(long id, BigDecimal pot, String commission) {
        Game g = new Game();
        g.setId(id);
        g.setAdminUserId(2L);
        g.setPrizePool(pot);
        g.setCommissionPercent(new BigDecimal(commission));
        g.setStatus(com.bingo.app.tenant.enums.GameStatus.CLAIM_PENDING);
        return g;
    }

    private BingoClaim claim(long id, long playerId) {
        BingoClaim c = new BingoClaim();
        c.setId(id);
        c.setGameId(30L);
        c.setPlayerId(playerId);
        c.setResult("VALID");
        return c;
    }

    private void stubPending(Game g, BingoClaim... claims) {
        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(claims));
        for (BingoClaim c : claims) {
            when(bingoClaimRepository.claimForProcessing(eq(c.getId()), any(), any())).thenReturn(1);
            when(bingoClaimRepository.save(c)).thenReturn(c);
        }
        when(gameCardRepository.findByGameIdAndPlayerId(eq(g.getId()), anyLong()))
                .thenReturn(Optional.of(new GameCard()));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("two shared winners: pot 20, 10% fee -> admin 2 once, winners 9 each, ENDED")
    void approveAllTwoWinners() throws Exception {
        Game g = game(30L, new BigDecimal("20.00"), "10.00");
        BingoClaim w1 = claim(1L, 101L);
        BingoClaim w2 = claim(2L, 102L);
        stubPending(g, w1, w2);

        var result = engine.approveAllClaims(g.getId(), 2L);

        assertAll(
                () -> assertEquals(2, result.getApprovedCount()),
                () -> assertEquals(0, new BigDecimal("9.00").compareTo(result.getRewardAmount())),
                () -> assertTrue(result.isGameEnded()),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus())
        );
        // commission credited exactly once, from the pot
        verify(walletService, times(1)).creditAgentCommission(eq(2L), eq(new BigDecimal("2.00")), eq(30L));
        // each winner paid exactly their share of the net pool
        verify(walletService).creditWinnings(101L, new BigDecimal("9.00"), 30L);
        verify(walletService).creditWinnings(102L, new BigDecimal("9.00"), 30L);
        verifyNoMoreInteractions(walletService);
        // both cards flagged as winners
        verify(cardService, times(2)).markCardAsWinner(eq(30L), anyLong());
        // both claims stored with their share
        ArgumentCaptor<BingoClaim> saved = ArgumentCaptor.forClass(BingoClaim.class);
        verify(bingoClaimRepository, times(2)).save(saved.capture());
        saved.getAllValues().forEach(c ->
                assertEquals(0, new BigDecimal("9.00").compareTo(c.getRewardAmount())));
    }

    @Test
    @DisplayName("four simultaneous winners: approve-all refused, nothing paid")
    void approveAllRefusedBeyondCap() {
        Game g = game(31L, new BigDecimal("40.00"), "10.00");
        stubPending(g, claim(1L, 101L), claim(2L, 102L), claim(3L, 103L), claim(4L, 104L));

        assertThrows(GameProgressException.class, () -> engine.approveAllClaims(g.getId(), 2L));

        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
        verify(walletService, never()).creditAgentCommission(anyLong(), any(), anyLong());
        assertEquals(com.bingo.app.tenant.enums.GameStatus.CLAIM_PENDING, g.getStatus(),
                "game must stay in review so the admin can restart instead");
    }

    @Test
    @DisplayName("claim submission: completed line is accepted for review and pauses the game")
    void claimSubmissionAccepted() throws Exception {
        Game g = game(32L, new BigDecimal("20.00"), "10.00");
        g.setWinningPattern("SINGLE_LINE");

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(calledNumberRepository.findCalledNumbersByGameId(g.getId()))
                .thenReturn(List.of(1, 2, 3, 4, 5));
        when(gameCardRepository.findByGameIdAndPlayerId(g.getId(), 101L))
                .thenReturn(Optional.of(GameCard.builder()
                        .card(com.bingo.app.tenant.entity.Card.builder()
                                .numbers("[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]")
                                .build())
                        .build()));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = engine.claimBingo(g.getId(), 101L, Collections.<Integer>emptyList(), false);

        assertTrue(result.isValid() || result.isPendingReview(),
                "a completed SINGLE_LINE must be accepted");
        assertEquals(com.bingo.app.tenant.enums.GameStatus.CLAIM_PENDING, g.getStatus());
        // no money moves until the admin approves
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("rejected claim bans the player for the game and resumes the game")
    void rejectClaimBansPlayer() {
        Game g = game(33L, new BigDecimal("20.00"), "10.00");
        BingoClaim claim = claim(7L, 101L);
        claim.setGameId(g.getId());
        GameCard playerCard = new GameCard();

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.findById(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findById(7L)).thenReturn(Optional.of(claim));
        when(bingoClaimRepository.claimForProcessing(eq(7L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID")).thenReturn(0L);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndPlayerId(g.getId(), 101L)).thenReturn(Optional.of(playerCard));
        when(gameCardRepository.save(any(GameCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.rejectClaim(g.getId(), 7L, 2L, "invalid pattern");

        assertAll(
                () -> assertEquals("REJECTED", claim.getResult()),
                () -> assertEquals("invalid pattern", claim.getRejectionReason()),
                () -> assertTrue(playerCard.isBanned(), "rejected player must be banned for the game"),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.IN_PROGRESS, g.getStatus())
        );
        verify(gameCardRepository).save(playerCard);
        verify(bingoClaimRepository).save(claim);
    }

    @Test
    @DisplayName("single winner approval: winner takes full net pool; game ends instantly")
    void singleWinnerApprovalTakesNetPool() throws Exception {
        Game g = game(32L, new BigDecimal("20.00"), "10.00");
        BingoClaim w = claim(9L, 101L);
        w.setGameId(g.getId());

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.claimForProcessing(eq(9L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(w));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndPlayerId(g.getId(), 101L))
                .thenReturn(Optional.of(new GameCard()));
        when(gameCardRepository.findByGameIdAndWinnerTrue(g.getId())).thenReturn(List.of());
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = engine.approveAllClaims(g.getId(), 2L);

        assertAll(
                () -> assertTrue(result.isGameEnded()),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus())
        );
        verify(walletService).creditAgentCommission(eq(2L), eq(new BigDecimal("2.00")), eq(32L));
        verify(walletService).creditWinnings(101L, new BigDecimal("18.00"), 32L);
        verifyNoMoreInteractions(walletService);
    }

    @Test
    @DisplayName("game with no winner: every registered player refunded the entry fee, game ENDED")
    void refundNoWinner() {
        Game g = game(40L, new BigDecimal("20.00"), "10.00");
        g.setEntryFee(new BigDecimal("10.00"));
        g.setStatus(com.bingo.app.tenant.enums.GameStatus.IN_PROGRESS);

        GameCard p1 = new GameCard(); p1.setPlayerId(101L);
        GameCard p2 = new GameCard(); p2.setPlayerId(102L);

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndWinnerTrue(g.getId())).thenReturn(List.of());
        when(gameCardRepository.findByGameId(g.getId())).thenReturn(List.of(p1, p2));

        engine.endGameWithoutWinner(g.getId(), "nobody claimed");

        assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus());
        verify(walletService).refundPlayer(101L, new BigDecimal("10.00"), 40L);
        verify(walletService).refundPlayer(102L, new BigDecimal("10.00"), 40L);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("game with a winner ending early: no entry-fee refunds (winner already took the pot)")
    void noRefundWhenWinnerExists() {
        Game g = game(41L, new BigDecimal("20.00"), "10.00");
        g.setEntryFee(new BigDecimal("10.00"));

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndWinnerTrue(g.getId()))
                .thenReturn(List.of(new GameCard()));

        engine.endGameWithoutWinner(g.getId(), "forced");

        verify(walletService, never()).refundPlayer(anyLong(), any(BigDecimal.class), anyLong());
        assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus());
    }
}
