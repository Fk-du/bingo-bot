package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;

    /**
     * Builds the API profile for a user. Players keep their money in the owning
     * agent's tenant DB (players table) while the master-side users.balance stays
     * at zero, so the wallet fields are overridden with the live tenant values.
     */
    public UserProfileResponse buildProfile(User user) {
        UserProfileResponse profile = UserProfileResponse.from(user);
        if (user == null || user.getRole() != Role.PLAYER) {
            return profile;
        }

        Player player = TenantHelper.withTenant(user, () ->
                playerRepository.findByUserId(user.getId()).orElse(null));

        if (player == null) {
            log.warn("No player record found in tenant DB for player user {}", user.getId());
            return profile;
        }

        String depositInfo = null;
        if (user.getAdminUserId() != null) {
            depositInfo = userRepository.findById(user.getAdminUserId())
                    .map(User::getDepositAccountInfo)
                    .orElse(null);
        }

        return profile.toBuilder()
                .balance(player.getBalance())
                .frozenBalance(player.getFrozenBalance())
                .depositAccountInfo(depositInfo)
                .build();
    }
}
