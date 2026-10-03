package de.thermo.lueftung

/** A user report, never a measured device status or a device command. */
enum class ManualDehumidifierOperation { UNKNOWN, RUNNING, STOPPED }

data class ManualDehumidifierPlacement(
    val roomId: String? = null,
    val operation: ManualDehumidifierOperation = ManualDehumidifierOperation.UNKNOWN
) {
    init { require(roomId != null || operation == ManualDehumidifierOperation.UNKNOWN) }
    fun movedTo(roomId: String?) = if (roomId == this.roomId) this else ManualDehumidifierPlacement(roomId)
    fun reported(operation: ManualDehumidifierOperation): ManualDehumidifierPlacement {
        require(roomId != null) { "Assign a room before reporting operation" }
        return copy(operation = operation)
    }
}
