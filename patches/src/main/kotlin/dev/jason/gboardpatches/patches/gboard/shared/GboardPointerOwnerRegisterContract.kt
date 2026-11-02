package dev.jason.gboardpatches.patches.gboard.shared

/**
 * descriptor is supplied by generated bindings;
 * these values describe the independently verified word layout only.
 */
internal object GboardPointerOwnerRegisterContract {
    const val stockRegisterCount = 21
    const val expandedRegisterCount = 28

    const val legacyP0Register = 14
    const val legacyP1Register = 15
    const val legacyP2Register = 16
    const val legacyP3Register = 17
    const val legacyP4Register = 18
    const val legacyP6Register = 20

    const val expandedP0Register = 21
    const val expandedP1Register = 22
    const val expandedP2Register = 23
    const val expandedP3Register = 24
    const val expandedP4Register = 25
    const val expandedP6Register = 27

    fun delegateRegisters(registerCount: Int): GboardPointerOwnerDelegateRegisters =
        when (registerCount) {
            stockRegisterCount -> GboardPointerOwnerDelegateRegisters(
                receiver = "p0",
                softKey = "p1",
            )
            expandedRegisterCount -> GboardPointerOwnerDelegateRegisters(
                receiver = "v$legacyP0Register",
                softKey = "v$legacyP1Register",
            )
            else -> error("Unsupported Gboard pointer-owner register count: $registerCount")
        }
}

internal data class GboardPointerOwnerDelegateRegisters(
    val receiver: String,
    val softKey: String,
)
