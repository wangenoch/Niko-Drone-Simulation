package com.horizon.nikonikodronesimulator.ui.hud

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.StickInputState
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun JoystickOverlay(
    state: DroneState,
    stickState: StickInputState
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp, start = 40.dp)) {
            VirtualJoystick(
                stickX = stickState.stickLX(state), 
                stickY = stickState.stickLY(state),
                onDragStateChange = { stickState.isTouchingLeft = it },
                onValueChange = { x, y -> stickState.touchLX = x; stickState.touchLY = y }
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp, end = 40.dp)) {
            VirtualJoystick(
                stickX = stickState.stickRX(state), 
                stickY = stickState.stickRY(state),
                onDragStateChange = { stickState.isTouchingRight = it },
                onValueChange = { x, y -> stickState.touchRX = x; stickState.touchRY = y }
            )
        }
    }
}
