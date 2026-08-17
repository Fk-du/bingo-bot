package com.bingo.app.tenant.service;

import com.bingo.app.master.entity.AdminFundRequest;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.FundStatus;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.AdminFundRequestRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.CoinRequestResponse;
import com.bingo.app.tenant.dto.response.TransactionResponse;
import com.bingo.app.tenant.dto.response.WithdrawalResponse;
import com.bingo.app.tenant.entity.CoinRequest;
import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.entity.Transaction;
import com.bingo.app.tenant.entity.Withdrawal;
import com.bingo.app.tenant.enums.RequestStatus;
import com.bingo.app.tenant.enums.TransactionStatus;
import com.bingo.app.tenant.enums.TransactionType;
import com.bingo.app.tenant.repository.CoinRequestRepository;
import com.bingo.app.tenant.repository.PlayerRepository;
import com.bingo.app.tenant.repository.TransactionRepository;
import com.bingo.app.tenant.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final TransactionRepository transactionRepository;
    private final CoinRequestRepository coinRequestRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final AdminFundRequestRepository adminFundRequestRepository;
    private final TenantMapper tenantMapper;

    // =========================================================
    // PLAYER METHODS
    // =========================================================

    @Transactional
    public CoinRequestResponse buyPoints(Long playerId, BigDecimal amount, String screenshotUrl) {
        playerService.findByUserId(playerId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        CoinRequest request = CoinRequest.builder()
                .userId(playerId)
                .amount(amount)
                .screenshotUrl(screenshotUrl)
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        CoinRequest saved = coinRequestRepository.save(request);

        createTransaction(playerId, TransactionType.TOP_UP, amount,
                TransactionStatus.PENDING, saved.getId(), "Points purchase requested");

        log.info("Buy points request created for player {}: amount={}", playerId, amount);
        return tenantMapper.toDto(saved);
    }

    @Transactional
    public WithdrawalResponse createWithdrawRequest(Long playerId, BigDecimal amount, String payoutDetails) {
        Player player = playerRepository.findByUserId(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        if (player.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        playerService.freezeBalance(playerId, amount);

        Withdrawal withdrawal = Withdrawal.builder()
                .userId(playerId)
                .amount(amount)
                .payoutMethod("BANK_TRANSFER")
                .payoutDetails(payoutDetails)
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Withdrawal saved = withdrawalRepository.save(withdrawal);

        createTransaction(playerId, TransactionType.WITHDRAWAL, amount,
                TransactionStatus.PENDING, saved.getId(), "Withdrawal request created");

        log.info("Withdrawal request created for player {}: amount={}", playerId, amount);
        return tenantMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getHistory(Long playerId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(playerId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long playerId) {
        return playerService.getBalance(playerId);
    }

    // =========================================================
    // ADMIN METHODS
    // =========================================================

    @Transactional
    public void fundPlayer(Long adminUserId, Long playerId, BigDecimal amount) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admins can fund players");
        }

        if (admin.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient admin balance");
        }

        Player player = playerRepository.findByUserId(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));

        if (!player.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Player does not belong to this admin");
        }

        admin.setBalance(admin.getBalance().subtract(amount));
        userRepository.save(admin);

        playerService.addBalance(playerId, amount);

        createTransaction(adminUserId, TransactionType.FUND_AGENT_TO_PLAYER, amount,
                TransactionStatus.COMPLETED, null, "Funded player " + playerId);
        createTransaction(playerId, TransactionType.DEPOSIT, amount,
                TransactionStatus.COMPLETED, null, "Received from admin " + adminUserId);

        log.info("Admin {} funded player {} with amount {}", adminUserId, playerId, amount);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponse> getPendingWithdrawsForAdminPlayers(Long adminUserId) {
        List<Player> players = playerRepository.findByAdminUserId(adminUserId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();

        return withdrawalRepository.findByUserIdInAndStatus(playerIds, RequestStatus.PENDING).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional
    public void approveWithdrawal(Long withdrawalId, Long approverId) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new RuntimeException("Withdrawal not found"));

        if (withdrawal.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Withdrawal already processed");
        }

        withdrawal.setStatus(RequestStatus.APPROVED);
        withdrawal.setProcessedBy(approverId);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        playerService.unfreezeBalance(withdrawal.getUserId(), withdrawal.getAmount());

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        withdrawalId, TransactionType.WITHDRAWAL.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(transaction);
        }

        log.info("Withdrawal {} approved by {}", withdrawalId, approverId);
    }

    @Transactional
    public void rejectWithdrawal(Long withdrawalId, Long approverId, String reason) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new RuntimeException("Withdrawal not found"));

        if (withdrawal.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Withdrawal already processed");
        }

        withdrawal.setStatus(RequestStatus.REJECTED);
        withdrawal.setProcessedBy(approverId);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawal.setRejectionReason(reason);
        withdrawalRepository.save(withdrawal);

        playerService.returnFrozenBalance(withdrawal.getUserId(), withdrawal.getAmount());

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        withdrawalId, TransactionType.WITHDRAWAL.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }

        log.info("Withdrawal {} rejected by {}: {}", withdrawalId, approverId, reason);
    }

    // =========================================================
    // SUPER ADMIN METHODS
    // =========================================================

    @Transactional
    public void fundAdmin(Long superAdminId, Long adminUserId, BigDecimal amount) {
        User superAdmin = userRepository.findById(superAdminId)
                .orElseThrow(() -> new RuntimeException("Super admin not found"));

        if (superAdmin.getRole() != Role.SUPER_ADMIN) {
            throw new RuntimeException("Only super admin can fund admins");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("User is not an admin");
        }

        admin.setBalance(admin.getBalance().add(amount));
        userRepository.save(admin);

        createTransaction(superAdminId, TransactionType.FUND_SUPER_ADMIN_TO_AGENT, amount,
                TransactionStatus.COMPLETED, null, "Funded admin " + adminUserId);
        createTransaction(adminUserId, TransactionType.DEPOSIT, amount,
                TransactionStatus.COMPLETED, null, "Received from super admin");

        log.info("Super admin {} funded admin {} with amount {}", superAdminId, adminUserId, amount);
    }

    @Transactional
    public void approveCoinRequest(Long requestId, Long approverId) {
        CoinRequest request = coinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Coin request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        if (approver.getRole() != Role.SUPER_ADMIN || approver.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance to approve request");
        }

        approver.setBalance(approver.getBalance().subtract(request.getAmount()));
        userRepository.save(approver);

        playerService.addBalance(request.getUserId(), request.getAmount());

        request.setStatus(RequestStatus.APPROVED);
        request.setApprovedBy(approverId);
        request.setApprovedAt(LocalDateTime.now());
        coinRequestRepository.save(request);

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        requestId, TransactionType.TOP_UP.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setDescription("Approved by " + approverId);
            transactionRepository.save(transaction);
        }

        createTransaction(approverId, TransactionType.FUND_AGENT_TO_PLAYER, request.getAmount(),
                TransactionStatus.COMPLETED, requestId, "Funded player " + request.getUserId());

        log.info("Coin request {} approved by {} — deducted from approver balance", requestId, approverId);
    }

    @Transactional
    public void rejectCoinRequest(Long requestId, Long approverId, String reason) {
        CoinRequest request = coinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Coin request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setApprovedBy(approverId);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        coinRequestRepository.save(request);

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        requestId, TransactionType.TOP_UP.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }

        log.info("Coin request {} rejected by {}: {}", requestId, approverId, reason);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CoinRequestResponse> getPendingCoinRequestsForAdmin(Long adminUserId) {
        List<Player> players = playerRepository.findByAdminUserId(adminUserId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();

        return coinRequestRepository.findByUserIdInAndStatus(playerIds, RequestStatus.PENDING).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    // =========================================================
    // ADMIN FUND REQUEST METHODS (Admin → Super Admin)
    // =========================================================

    @Transactional
    public AdminFundRequest requestAdminFund(Long adminUserId, BigDecimal amount, String screenshotUrl) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admins can request funds");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        AdminFundRequest request = AdminFundRequest.builder()
                .adminUserId(adminUserId)
                .amount(amount)
                .screenshotUrl(screenshotUrl)
                .status(FundStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        AdminFundRequest saved = adminFundRequestRepository.save(request);

        log.info("Admin {} requested fund of amount {} from super admin", adminUserId, amount);
        return saved;
    }

    @Transactional
    public void approveAdminFundRequest(Long requestId, Long superAdminId) {
        User superAdmin = userRepository.findById(superAdminId)
                .orElseThrow(() -> new RuntimeException("Super admin not found"));

        if (superAdmin.getRole() != Role.SUPER_ADMIN) {
            throw new RuntimeException("Only super admin can approve admin fund requests");
        }

        AdminFundRequest request = adminFundRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Fund request not found"));

        if (request.getStatus() != FundStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        User admin = userRepository.findById(request.getAdminUserId())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        admin.setBalance(admin.getBalance().add(request.getAmount()));
        userRepository.save(admin);

        request.setStatus(FundStatus.APPROVED);
        request.setApprovedBy(superAdminId);
        request.setApprovedAt(LocalDateTime.now());
        adminFundRequestRepository.save(request);

        createTransaction(superAdminId, TransactionType.FUND_SUPER_ADMIN_TO_AGENT, request.getAmount(),
                TransactionStatus.COMPLETED, requestId, "Approved fund request for admin " + request.getAdminUserId());
        createTransaction(request.getAdminUserId(), TransactionType.DEPOSIT, request.getAmount(),
                TransactionStatus.COMPLETED, requestId, "Fund request approved by super admin");

        log.info("Admin fund request {} approved by super admin {} — admin {} funded with {}",
                requestId, superAdminId, request.getAdminUserId(), request.getAmount());
    }

    @Transactional
    public void rejectAdminFundRequest(Long requestId, Long superAdminId, String reason) {
        AdminFundRequest request = adminFundRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Fund request not found"));

        if (request.getStatus() != FundStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        request.setStatus(FundStatus.REJECTED);
        request.setApprovedBy(superAdminId);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        adminFundRequestRepository.save(request);

        log.info("Admin fund request {} rejected by super admin {}: {}", requestId, superAdminId, reason);
    }

    @Transactional(readOnly = true)
    public List<AdminFundRequest> getPendingAdminFundRequests() {
        return adminFundRequestRepository.findByStatusOrderByCreatedAtDesc(FundStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<AdminFundRequest> getAdminFundRequestsByAdmin(Long adminUserId) {
        return adminFundRequestRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId);
    }

    // =========================================================
    // GAME RELATED METHODS
    // =========================================================

    @Transactional
    public void deductBet(Long playerId, BigDecimal amount, Long gameId) {
        playerService.deductBalance(playerId, amount);

        createTransaction(playerId, TransactionType.BET, amount,
                TransactionStatus.COMPLETED, gameId, "Bet placed for game " + gameId);
    }

    @Transactional
    public void creditWinnings(Long playerId, BigDecimal amount, Long gameId) {
        playerService.addBalance(playerId, amount);

        createTransaction(playerId, TransactionType.WIN, amount,
                TransactionStatus.COMPLETED, gameId, "Won from game " + gameId);
    }

    @Transactional
    public void deductPlatformFee(Long adminUserId, BigDecimal amount, Long gameId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getBalance().compareTo(amount) < 0) {
            log.warn("Admin {} has insufficient balance for platform fee {}", adminUserId, amount);
            return;
        }

        admin.setBalance(admin.getBalance().subtract(amount));
        userRepository.save(admin);

        createTransaction(adminUserId, TransactionType.PLATFORM_FEE, amount,
                TransactionStatus.COMPLETED, gameId, "Platform fee for game " + gameId);
    }

    // =========================================================
    // PRIVATE HELPER METHODS
    // =========================================================

    private void createTransaction(Long userId, TransactionType type, BigDecimal amount,
                                   TransactionStatus status, Long referenceId, String description) {
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .type(type.name())
                .amount(amount)
                .status(status)
                .referenceId(referenceId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
    }
}
