package com.bingo.app.modules.topup.service;

import com.bingo.app.exception.PlayerActionException;
import com.bingo.app.modules.topup.entity.TopUpRequest;
import com.bingo.app.modules.topup.enums.TopUpStatus;
import com.bingo.app.modules.topup.repository.TopUpRequestRepository;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.wallet.entity.Transaction;
import com.bingo.app.modules.wallet.enums.TransactionStatus;
import com.bingo.app.modules.wallet.enums.TransactionType;
import com.bingo.app.modules.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TopUpService {

    private final TopUpRequestRepository topUpRequestRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public TopUpRequest createRequest(Long requesterId, Long approverId, BigDecimal amount, String proofImageFileId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlayerActionException("Invalid amount", "Amount must be greater than zero.");
        }

        TopUpRequest request = TopUpRequest.builder()
                .requesterId(requesterId)
                .approverId(approverId)
                .amount(amount)
                .status(TopUpStatus.PENDING)
                .proofImageFileId(proofImageFileId)
                .createdAt(LocalDateTime.now())
                .build();

        return topUpRequestRepository.save(request);
    }

    @Transactional("tenantTransactionManager")
    public TopUpRequest approveRequest(Long requestId, Long approverId) {
        TopUpRequest request = topUpRequestRepository.findById(requestId)
                .orElseThrow(() -> new PlayerActionException("Request not found", "Top-up request could not be found."));

        if (!request.getApproverId().equals(approverId)) {
            throw new PlayerActionException("Access denied", "You are not the designated approver for this request.");
        }

        if (request.getStatus() != TopUpStatus.PENDING) {
            throw new PlayerActionException("Already processed", "This top-up request has already been processed.");
        }

        User requester = userRepository.findById(request.getRequesterId())
                .orElseThrow(() -> new PlayerActionException("Requester not found", "The requesting user was not found."));

        if (requester.getRole() == Role.PLAYER) {
            User admin = userRepository.findById(approverId)
                    .orElseThrow(() -> new PlayerActionException("Admin not found", "Approving admin was not found."));

            if (admin.getBalance().compareTo(request.getAmount()) < 0) {
                throw new PlayerActionException("Insufficient balance", "You do not have enough coins to approve this request.");
            }

            admin.setBalance(admin.getBalance().subtract(request.getAmount()));
            userRepository.save(admin);

            requester.setBalance(requester.getBalance().add(request.getAmount()));
            userRepository.save(requester);

            recordTransaction(request.getRequesterId(), request.getAmount(), TransactionType.PLAYER_FUND, approverId);
        } else {
            requester.setBalance(requester.getBalance().add(request.getAmount()));
            userRepository.save(requester);

            recordTransaction(request.getRequesterId(), request.getAmount(), TransactionType.AGENT_FUND, approverId);
        }

        request.setStatus(TopUpStatus.APPROVED);
        request.setUpdatedAt(LocalDateTime.now());
        return topUpRequestRepository.save(request);
    }

    @Transactional("tenantTransactionManager")
    public TopUpRequest rejectRequest(Long requestId, Long approverId) {
        TopUpRequest request = topUpRequestRepository.findById(requestId)
                .orElseThrow(() -> new PlayerActionException("Request not found", "Top-up request could not be found."));

        if (!request.getApproverId().equals(approverId)) {
            throw new PlayerActionException("Access denied", "You are not the designated approver for this request.");
        }

        if (request.getStatus() != TopUpStatus.PENDING) {
            throw new PlayerActionException("Already processed", "This top-up request has already been processed.");
        }

        request.setStatus(TopUpStatus.REJECTED);
        request.setUpdatedAt(LocalDateTime.now());
        return topUpRequestRepository.save(request);
    }

    public List<TopUpRequest> getPendingRequestsForApprover(Long approverId) {
        return topUpRequestRepository.findByApproverIdAndStatus(approverId, TopUpStatus.PENDING);
    }

    public List<TopUpRequest> getRequestsForRequester(Long requesterId) {
        return topUpRequestRepository.findByRequesterId(requesterId);
    }

    private void recordTransaction(Long userId, BigDecimal amount, TransactionType type, Long approvedBy) {
        Transaction tx = Transaction.builder()
                .userId(userId)
                .type(type)
                .amount(amount)
                .status(TransactionStatus.APPROVED)
                .approvedBy(approvedBy)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }
}
