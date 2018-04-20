package digital.neuron.opencvintegration.data

/**
 * Enum that defines receipt detection messages
 */
enum class ScanHint {
    MOVE_AWAY,
    MOVE_CLOSER,
    FIND_RECT,
    ADJUST_ANGLE,
    CAPTURING_IMAGE,
    CAPTURED,
    NO_MESSAGE
}