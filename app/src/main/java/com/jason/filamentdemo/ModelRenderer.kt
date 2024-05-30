package com.jason.filamentdemo

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.view.Choreographer
import android.view.SurfaceView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelRenderer {
    private lateinit var surfaceView: SurfaceView
    private lateinit var lifecycle: Lifecycle

    private lateinit var choreographer: Choreographer
    private lateinit var uiHelper: UiHelper

    private lateinit var modelViewer: ModelViewer

    private lateinit var assets: AssetManager

    private val frameScheduler = FrameCallback()

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            choreographer.postFrameCallback(frameScheduler)
        }

        override fun onPause(owner: LifecycleOwner) {
            choreographer.removeFrameCallback(frameScheduler)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            choreographer.removeFrameCallback(frameScheduler)
            lifecycle.removeObserver(this)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun onSurfaceAvailable(surfaceView: SurfaceView, lifecycle: Lifecycle) {
        this.surfaceView = surfaceView
        this.lifecycle = lifecycle
        assets = surfaceView.context.assets

        lifecycle.addObserver(lifecycleObserver)
        choreographer = Choreographer.getInstance()
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            isOpaque = false
        }

        modelViewer = ModelViewer(surfaceView = surfaceView, uiHelper = uiHelper)

        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            true
        }

        modelViewer.scene.skybox = null
        modelViewer.view.blendMode = View.BlendMode.TRANSLUCENT
        modelViewer.renderer.clearOptions = modelViewer.renderer.clearOptions.apply {
            clear = true
        }

        modelViewer.view.apply {
            renderQuality = renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }
        }

        createRenderAbles()
    }

    private fun createRenderAbles() {
        val buffer = assets.open("models/an.glb").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }

        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()

        // Create a new directional light pointing from the front
        val entityManager = EntityManager.get()
        val light = entityManager.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(100000.0f)
            .direction(0.0f, 0.0f, -1.0f) // Light direction: from front to back
            .castShadows(true)
            .build(modelViewer.engine, light)

        // Add the light to the scene
        modelViewer.scene.addEntity(light)

        // Remove the default light
        modelViewer.scene.skybox = null
        modelViewer.view.blendMode = View.BlendMode.TRANSLUCENT
        modelViewer.renderer.clearOptions = modelViewer.renderer.clearOptions.apply {
            clear = true
        }

        // Set render quality
        modelViewer.view.apply {
            renderQuality = renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }
        }
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)
        }
    }
}