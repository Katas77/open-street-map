package com.example.open_street_map

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OsmMapScreen(
    viewModel: LocationViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationPermissionState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Запускаем получение локации, только если разрешения уже предоставлены.
    // Подавляем предупреждение Lint здесь, потому что мы явно проверили permission state.
    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted) {
            @Suppress("MissingPermission")
            viewModel.getCurrentLocation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!locationPermissionState.allPermissionsGranted) {
            Text(
                text = "Для использования карты необходимы разрешения на геолокацию",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { locationPermissionState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Разрешить доступ к геолокации")
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val location by viewModel.currentLocation.collectAsState(initial = null)
                val isLoading by viewModel.isLoading.collectAsState(initial = false)
                val error by viewModel.error.collectAsState(initial = null)

                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "Ошибка: $error",
                                color = Color.Red,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = {
                                    @Suppress("MissingPermission")
                                    viewModel.getCurrentLocation()
                                }
                            ) {
                                Text("Повторить запрос")
                            }
                        }
                    }
                    else -> {
                        OsmMap(
                            location = location,
                            lifecycleOwner = lifecycleOwner,
                            onMapReady = {
                                // при необходимости обновить UI или сделать что-то после создания карты
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    @Suppress("MissingPermission")
                    viewModel.getCurrentLocation()
                },
                enabled = locationPermissionState.allPermissionsGranted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Обновить местоположение")
            }
        }
    }
}

@Composable
private fun OsmMap(
    location: Location?,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onMapReady: () -> Unit
) {
    val context = LocalContext.current

    // Храним MapView в remember, чтобы не пересоздавать его при recomposition
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(true)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }

    // Управление жизненным циклом MapView: onResume/onPause
    DisposableEffect(lifecycleOwner, mapView) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            // Дополнительно безопасно вызывать onPause/onDetach
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, update = { mv ->
        // Центрируем карту и добавляем маркер, если есть location
        if (location != null) {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            mv.controller.animateTo(geoPoint)

            // убираем старые маркеры (чтобы не дублировать)
            val overlays = mv.overlays
            overlays.removeAll { it is Marker }

            val marker = Marker(mv).apply {
                position = geoPoint
                title = "Вы здесь"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            overlays.add(marker)
        }
        onMapReady()
    })
}
