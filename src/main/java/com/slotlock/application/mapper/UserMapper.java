package com.slotlock.application.mapper;

import com.slotlock.application.dto.response.UserLoginResponse;
import com.slotlock.application.dto.response.UserRegistrationResponse;
import com.slotlock.application.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "token", ignore = true)
    UserRegistrationResponse toRegistrationResponse(User user);

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "token", ignore = true)
    UserLoginResponse toLoginResponse(User user);
}
