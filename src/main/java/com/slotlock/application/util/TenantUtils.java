package com.slotlock.application.util;

import com.slotlock.application.config.SecurityUtils;
import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TenantUtils {

    private TenantUtils() {
    }

    public static void requireSameTenant(Long resourceTenantId) {
        if (!resourceTenantId.equals(SecurityUtils.getCurrentTenantId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ApiErrorCodeEnum.ACCESS_DENIED, "Resource does not belong to your tenant");
        }
    }
}
