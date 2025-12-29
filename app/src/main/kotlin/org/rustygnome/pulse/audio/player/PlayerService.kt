package org.rustygnome.pulse.audio.player

interface PlayerService {
    companion object {
        const val ACTION_PULSE_FIRE = "org.rustygnome.pulse.ACTION_PULSE_FIRE"
        const val ACTION_PLAYER_STARTED = "org.rustygnome.pulse.ACTION_PLAYER_STARTED"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_PITCH = "extra_pitch"
    }
}
