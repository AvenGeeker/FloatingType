package com.avengeeker.floatingtype.overlay

/** Voice-input state machine shown by the floating ball. */
enum class BallState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    INJECTING,
    ERROR,
}
