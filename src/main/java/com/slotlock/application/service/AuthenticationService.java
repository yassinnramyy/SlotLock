package com.slotlock.application.service;

import com.slotlock.application.dto.request.AdminCreationRequest;
import com.slotlock.application.dto.request.StaffCreationRequest;
import com.slotlock.application.dto.request.UserLoginRequest;
import com.slotlock.application.dto.request.UserRegistrationRequest;
import com.slotlock.application.dto.response.UserLoginResponse;
import com.slotlock.application.dto.response.UserRegistrationResponse;
import com.slotlock.application.dto.response.UserSummaryResponse;

public interface AuthenticationService {

    UserRegistrationResponse register(UserRegistrationRequest request);

    UserLoginResponse login(UserLoginRequest request);

    UserSummaryResponse createTenantAdmin(AdminCreationRequest request);

    UserSummaryResponse createStaff(StaffCreationRequest request);
}
