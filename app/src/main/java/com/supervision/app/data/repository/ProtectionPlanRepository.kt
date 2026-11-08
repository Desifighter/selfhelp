package com.supervision.app.data.repository

import com.supervision.app.data.local.dao.ProtectionPlanDao
import com.supervision.app.data.mapper.toDomain
import com.supervision.app.data.mapper.toEntity
import com.supervision.app.domain.model.ProtectionPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionPlanRepository @Inject constructor(
    private val dao: ProtectionPlanDao
) {
    fun observeActivePlan(): Flow<ProtectionPlan?> =
        dao.observeActivePlan().map { it?.toDomain() }

    fun observeAllPlans(): Flow<List<ProtectionPlan>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getActivePlan(): ProtectionPlan? =
        dao.getActivePlan()?.toDomain()

    suspend fun createPlan(plan: ProtectionPlan): Long {
        val id = dao.insert(plan.toEntity())
        Timber.i("Created protection plan: ${plan.name} (id: $id)")
        return id
    }

    suspend fun updatePlan(plan: ProtectionPlan) =
        dao.update(plan.toEntity())

    suspend fun activatePlan(planId: Long) {
        dao.deactivateAll()
        dao.activate(planId)
        Timber.i("Activated protection plan: $planId")
    }

    suspend fun deactivateAll() {
        dao.deactivateAll()
        Timber.i("Deactivated all protection plans")
    }

    suspend fun deletePlan(planId: Long) = dao.deleteById(planId)
}
