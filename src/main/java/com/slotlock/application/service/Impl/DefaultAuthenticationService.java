package com.slotlock.application.service.Impl;

import com.slotlock.application.config.JwtTokenProvider;
import com.slotlock.application.dto.request.UserLoginRequest;
import com.slotlock.application.dto.request.UserRegistrationRequest;
import com.slotlock.application.dto.response.UserLoginResponse;
import com.slotlock.application.dto.response.UserRegistrationResponse;
import com.slotlock.application.entity.User;
import com.slotlock.application.enums.UserRoleEnum;
import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.ApiException;
import com.slotlock.application.exception.BusinessLogicViolationException;
import com.slotlock.application.mapper.UserMapper;
import com.slotlock.application.repository.UserRepository;
import com.slotlock.application.service.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuthenticationService implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public DefaultAuthenticationService(UserRepository userRepository,
                                         PasswordEncoder passwordEncoder,
                                         JwtTokenProvider jwtTokenProvider,
                                         UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
    }

    @Override
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessLogicViolationException(
                    HttpStatus.CONFLICT, ApiErrorCodeEnum.EMAIL_ALREADY_EXISTS, "Email already registered");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .tenantId(request.tenantId())
                .role(request.role() != null ? request.role() : UserRoleEnum.CUSTOMER)
                .build();

        user = userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getTenantId(), user.getRole().name());

        UserRegistrationResponse mapped = userMapper.toRegistrationResponse(user);
        return new UserRegistrationResponse(mapped.userId(), mapped.tenantId(), mapped.role(), token);
    }

    @Override
    public UserLoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, ApiErrorCodeEnum.INVALID_CREDENTIALS, "Invalid email or password"));

        String token = jwtTokenProvider.generateToken(user.getId(), user.getTenantId(), user.getRole().name());

        UserLoginResponse mapped = userMapper.toLoginResponse(user);
        return new UserLoginResponse(mapped.userId(), mapped.tenantId(), mapped.role(), token);
    }
}
