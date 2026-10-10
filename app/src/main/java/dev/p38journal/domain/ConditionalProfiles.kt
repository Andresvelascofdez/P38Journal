package dev.p38journal.domain

object ConditionalProfiles {
    fun proposed(rule: Rule): Rule? {
        val profile = when (rule.id) {
            "fuel-filter-applicability" -> rule.copy(
                title = "Cambiar filtro de combustible externo confirmado",
                operation = "replace", days = null, months = 48, km = 80000
            )
            "offroad-brakes" -> rule.copy(
                title = "Inspeccionar sensores ABS, pastillas y pinzas en uso severo confirmado",
                operation = "professional_inspection", days = null, months = null, km = 1500
            )
            "offroad-tyres" -> rule.copy(
                title = "Comprobar neumáticos durante uso fuera de carretera confirmado",
                operation = "check", days = 1, months = null, km = null
            )
            else -> return null
        }
        return profile.copy(
            id = "${rule.id}-active", recurrence = "component", scheduleVisibility = "maintenance", initialAction = profile.title,
            condition = "Perfil condicionado: requiere confirmación expresa de aplicabilidad y evidencia del propietario. ${rule.condition}"
        )
    }
}
