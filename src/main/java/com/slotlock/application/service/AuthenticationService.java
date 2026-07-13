package com.slotlock.application.service;

import com.slotlock.application.dto.request.UserLoginRequest;
import com.slotlock.application.dto.request.UserRegistrationRequest;
import com.slotlock.application.dto.response.UserLoginResponse;
import com.slotlock.application.dto.response.UserRegistrationResponse;

public interface AuthenticationService {

    UserRegistrationResponse register(UserRegistrationRequest request);

    UserLoginResponse login(UserLoginRequest request);
}
