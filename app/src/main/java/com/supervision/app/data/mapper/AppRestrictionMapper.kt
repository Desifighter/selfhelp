package com.supervision.app.data.mapper

import com.supervision.app.data.local.entity.AppRestrictionEntity
import com.supervision.app.domain.model.AppRestriction
import com.supervision.app.domain.model.RestrictionType

fun AppRestrictionEntity.toDomain(): AppRestriction {
    return AppRestriction(
        packageName = packageName,
        appName = appName,
        restrictionType = RestrictionType.valueOf(restrictionType),
        dailyLimitMinutes = dailyLimitMinutes,
        isEssential = isEssential
    )
}

fun AppRestriction.toEntity(): AppRestrictionEntity {
    return AppRestrictionEntity(
        packageName = packageName,
        appName = appName,
        restrictionType = restrictionType.name,
        dailyLimitMinutes = dailyLimitMinutes,
        isEssential = isEssential
    )
}
