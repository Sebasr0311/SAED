package com.saed.backend.dashboard.service;

import com.saed.backend.dashboard.dto.OrgAnalyticsDTO;
import com.saed.backend.dashboard.dto.PropertyAnalyticsDTO;
import com.saed.backend.platform.dto.PlatformAnalyticsDTO;

public interface AnalyticsService {

    OrgAnalyticsDTO getOrgAnalytics(Long orgId, Integer meses, Integer anio);

    PropertyAnalyticsDTO getPropertyAnalytics(Integer meses);

    PlatformAnalyticsDTO getPlatformAnalytics(Integer meses);
}
