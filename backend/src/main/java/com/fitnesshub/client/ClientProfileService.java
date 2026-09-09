package com.fitnesshub.client;

import com.fitnesshub.client.dto.ClientDetailDto;
import com.fitnesshub.client.dto.UpdateClientProfileRequest;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ClientProfileService {

    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final CurrentUser currentUser;

    public ClientProfileService(ClientProfileRepository clientProfileRepository, UserRepository userRepository,
                                 AuthorizationService authorizationService, CurrentUser currentUser) {
        this.clientProfileRepository = clientProfileRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ClientDetailDto getDetail(UUID clientId) {
        authorizationService.assertCanAccessClient(clientId);
        User user = userRepository.findById(clientId).orElseThrow(() -> NotFoundException.of("User", clientId));
        ClientProfile profile = clientProfileRepository.findByUserId(clientId)
                .orElseThrow(() -> NotFoundException.of("ClientProfile", clientId));

        return new ClientDetailDto(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                profile.getCoachId(), profile.getDateOfBirth(), profile.getHeightCm(), profile.getSex(),
                profile.getFitnessGoal(), profile.getActivityLevel(), profile.getStartingWeight(),
                profile.getTargetWeight(), profile.getDailyCalorieTarget(), profile.getDailyProteinTarget(),
                profile.getDailyStepTarget(), profile.getUnitSystem());
    }

    @Transactional
    public ClientDetailDto updateTargets(UUID clientId, UpdateClientProfileRequest request) {
        authorizationService.assertCoachOwnsClient(currentUser.id(), clientId);
        ClientProfile profile = clientProfileRepository.findByUserId(clientId)
                .orElseThrow(() -> NotFoundException.of("ClientProfile", clientId));

        if (request.targetWeight() != null) profile.setTargetWeight(request.targetWeight());
        if (request.dailyCalorieTarget() != null) profile.setDailyCalorieTarget(request.dailyCalorieTarget());
        if (request.dailyProteinTarget() != null) profile.setDailyProteinTarget(request.dailyProteinTarget());
        if (request.dailyStepTarget() != null) profile.setDailyStepTarget(request.dailyStepTarget());
        clientProfileRepository.save(profile);
        return getDetail(clientId);
    }
}
