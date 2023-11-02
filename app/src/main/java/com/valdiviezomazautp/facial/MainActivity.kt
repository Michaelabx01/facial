package com.valdiviezomazautp.facial

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.face.Face
import com.google.android.gms.vision.face.FaceDetector

class MainActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_REQUEST_CODE = 101
    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private lateinit var faceDetector: FaceDetector
    private lateinit var cameraSource: CameraSource

    private lateinit var btnStartCamera: Button
    private lateinit var btnStopCamera: Button
    private lateinit var btnPauseResumeCamera: Button
    private var isCameraPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        btnStartCamera = findViewById(R.id.btnPauseResumeCamera)
        btnStopCamera = findViewById(R.id.btnStopCamera)
        btnPauseResumeCamera = findViewById(R.id.btnPauseResumeCamera)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                if (checkCameraPermission()) {
                    openFrontCamera()
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false
        }

        btnStartCamera.setOnClickListener {
            if (checkCameraPermission()) {
                openFrontCamera()
            }
        }

        btnStopCamera.setOnClickListener {
            closeCamera()
        }

        btnPauseResumeCamera.setOnClickListener {
            if (isCameraPaused) {
                startCameraPreview()
                isCameraPaused = false
                btnPauseResumeCamera.text = "Pause Camera"
            } else {
                stopCameraPreview()
                isCameraPaused = true
                btnPauseResumeCamera.text = "Resume Camera"
            }
        }

        // Configure the face detector
        val detectorOptions = FaceDetector.Builder(this)
            .setTrackingEnabled(true)
            .setMode(FaceDetector.ACCURATE_MODE)
            .setLandmarkType(FaceDetector.ALL_LANDMARKS)
            .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
            .build()

        faceDetector = detectorOptions
        if (!faceDetector.isOperational) {
            // Handle detector initialization errors
        }

        // Configure the camera source for face detection
        cameraSource = CameraSource.Builder(this, faceDetector)
            .setRequestedPreviewSize(1280, 720)
            .setFacing(CameraSource.CAMERA_FACING_FRONT) // Use the front-facing camera
            .setAutoFocusEnabled(true)
            .build()
    }

    private fun checkCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    private fun openFrontCamera() {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            createCameraPreviewSession()
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            cameraDevice?.close()
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            cameraDevice?.close()
                            cameraDevice = null
                        }
                    }, backgroundHandler)
                    break
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val surfaceTexture = textureView.surfaceTexture
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(textureView.width, textureView.height)
            }
            val surface = Surface(surfaceTexture)

            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    val captureRequest = captureRequestBuilder?.build()
                    if (captureRequest != null) {
                        session.setRepeatingRequest(captureRequest, null, backgroundHandler)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun startCameraPreview() {
        if (checkCameraPermission()) {
            openFrontCamera()
        }
    }

    private fun stopCameraPreview() {
        closeCamera()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

    override fun onPause() {
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }
}







