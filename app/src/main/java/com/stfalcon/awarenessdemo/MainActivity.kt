package com.stfalcon.awarenessdemo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.gms.awareness.Awareness
import com.google.android.gms.awareness.FenceClient
import com.google.android.gms.awareness.SnapshotClient
import com.google.android.gms.awareness.fence.*
import com.google.android.gms.awareness.state.HeadphoneState
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.stfalcon.awarenessdemo.extensions.asHtml
import com.stfalcon.awarenessdemo.extensions.stateString
import com.stfalcon.awarenessdemo.extensions.toDateTimeFormat
import com.stfalcon.awarenessdemo.extensions.toString
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object {
        const val FENCE_KEY = "qwe"
        const val FENCE_RECEIVER_ACTION = "FENCE_RECEIVER_ACTION"
    }

    private lateinit var fenceClient: FenceClient
    private lateinit var snapshotClient: SnapshotClient

    private var fenceReceiver = FenceReceiver()
    private var isTrackingNow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions(onEndAction = { init() })
        registerReceiver(fenceReceiver, IntentFilter(FENCE_RECEIVER_ACTION))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(fenceReceiver)
    }

    private fun checkPermissions(
        doIfSuccess: (() -> Unit)? = null,
        onEndAction: (() -> Unit)? = null
    ) {
        Dexter.withActivity(this)
            .withPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) android.Manifest.permission.ACTIVITY_RECOGNITION else android.Manifest.permission.INTERNET // INTERNET just for some permission which no need a user agreed (using like an empty string)
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }

                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let {
                        if (it.areAllPermissionsGranted()) {
                            doIfSuccess?.invoke()
                            return
                        }
                        if (it.deniedPermissionResponses?.isNotEmpty() == true) {
                            toast(R.string.permission_denied)
                        }
                        onEndAction?.invoke()
                    }
                }
            })
            .check()
    }

    private fun init() {
        fenceClient = Awareness.getFenceClient(this)
        snapshotClient = Awareness.getSnapshotClient(this)

        initListeners()

        snapshotCheckHeadphones()
        snapshotGetLocation()
        snapShotGetUserActivity()
    }

    private fun changeTrackingState() {
        isTrackingNow = !isTrackingNow
        if (isTrackingNow) {
            initFence()
        } else {
            unregisterFence()
        }
    }

    private fun unregisterFence() {
        fenceClient.updateFences(
            FenceUpdateRequest.Builder()
                .removeFence(FENCE_KEY).build()
        )
        fenceLogTv.text = ""
        fenceLogTv.animate().alpha(0f).start()
        fenceSubtitle.animate().alpha(0f).start()
        fenceParametersScrollContainer.animate().alpha(1f)
            .withStartAction { fenceParametersScrollContainer.isVisible = true }
        fenceTrackingBt.setText(R.string.start_tracking)
    }

    private fun initFence() {
        val intent = Intent(FENCE_RECEIVER_ACTION)
        val mPendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val selectedFenceParameters = getSelectedParameters()
        if (selectedFenceParameters.fenceList.isEmpty()) {
            toast("Please, select at least one fence")
            isTrackingNow = false
            return
        }
        val fence = AwarenessFence.or(selectedFenceParameters.fenceList)
        fenceSubtitle.text = getString(
            R.string.fence_subtitle,
            selectedFenceParameters.parametersNames.joinToString(separator = ", ")
        )
        fenceClient.updateFences(
            FenceUpdateRequest.Builder().addFence(
                FENCE_KEY,
                fence,
                mPendingIntent
            ).build()
        )
            .addOnSuccessListener {
                this@MainActivity.fenceLogTv.append("Start tracking")
            }
            .addOnFailureListener {
                this@MainActivity.fenceLogTv.append("Error: $it\n")
            }
        fenceLogTv.animate().alpha(1f).start()
        fenceSubtitle.animate().alpha(1f).start()
        fenceParametersScrollContainer.animate().alpha(0f)
            .withEndAction { fenceParametersScrollContainer.isVisible = false }
        fenceTrackingBt.setText(R.string.stop_tracking)
    }

    private fun getSelectedParameters(): SelectedFenceParameters {
        val listNames = mutableListOf<String>()
        val listFences = mutableListOf<AwarenessFence>()
        with(fenceHeadphonesConnect) {
            if (isChecked) {
                listNames.add(text.toString())
                listFences.add(HeadphoneFence.during(HeadphoneState.PLUGGED_IN))
            }
        }
        with(fenceHeadphonesDisconnect) {
            if (isChecked) {
                listNames.add(text.toString())
                listFences.add(HeadphoneFence.during(HeadphoneState.UNPLUGGED))
            }
        }
        with(fenceUserIdle) {
            if (isChecked) {
                listNames.add(text.toString())
                listFences.add(DetectedActivityFence.during(DetectedActivityFence.STILL))
            }
        }
        with(fenceUserStartWalking) {
            if (isChecked) {
                listNames.add(text.toString())
                listFences.add(DetectedActivityFence.starting(DetectedActivityFence.WALKING))
                listFences.add(DetectedActivityFence.starting(DetectedActivityFence.ON_FOOT))
                listFences.add(DetectedActivityFence.starting(DetectedActivityFence.RUNNING))
            }
        }
        with(fenceUserWalking) {
            if (isChecked) {
                listNames.add(text.toString())
                listFences.add(DetectedActivityFence.during(DetectedActivityFence.WALKING))
                listFences.add(DetectedActivityFence.during(DetectedActivityFence.ON_FOOT))
                listFences.add(DetectedActivityFence.during(DetectedActivityFence.RUNNING))
            }
        }
        with(fenceUserStopWalking) {
            if (isChecked) {
                listNames.add(text.toString())
                listFences.add(DetectedActivityFence.stopping(DetectedActivityFence.WALKING))
                listFences.add(DetectedActivityFence.stopping(DetectedActivityFence.ON_FOOT))
                listFences.add(DetectedActivityFence.stopping(DetectedActivityFence.RUNNING))
            }
        }
        with(fenceUserInCar) {
            if (isChecked) {
                listNames.add(text.toString())
                listFences.add(DetectedActivityFence.during(DetectedActivityFence.IN_VEHICLE))
                listFences.add(DetectedActivityFence.starting(DetectedActivityFence.IN_VEHICLE))
                listFences.add(DetectedActivityFence.stopping(DetectedActivityFence.IN_VEHICLE))
            }
        }
        return SelectedFenceParameters(
            parametersNames = listNames,
            fenceList = listFences
        )
    }

    private fun initListeners() {
        snapshotHeadphonesContainer.setOnClickListener { snapshotCheckHeadphones() }
        snapshotLocationContainer.setOnClickListener {
            checkPermissions(onEndAction = { snapshotGetLocation() })
        }
        snapshotActivityContainer.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                checkPermissions(onEndAction = { snapShotGetUserActivity() })
            } else {
                snapShotGetUserActivity()
            }
        }
        fenceTrackingBt.setOnClickListener {
            checkPermissions(doIfSuccess = { changeTrackingState() })
        }
    }

    private fun snapshotCheckHeadphones() {
        snapshotClient.headphoneState
            .addOnSuccessListener {
                snapshotHeadphonesContainer.text = it.headphoneState.toString(this)
                snapshotHeadphonesContainer.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
            .addOnFailureListener {
                handleSnapshotError(snapshotHeadphonesContainer, it)
            }
    }

    private fun snapshotGetLocation() {
        snapshotClient.location
            .addOnSuccessListener {
                with(it.location) {
                    snapshotLocationContainer.text = getString(
                        R.string.user_location,
                        latitude, longitude, altitude, accuracy
                    )
                }
                snapshotLocationContainer.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
            .addOnFailureListener {
                handleSnapshotError(snapshotLocationContainer, it)
            }
    }

    private fun snapShotGetUserActivity() {
        snapshotClient.detectedActivity
            .addOnSuccessListener {
                with(it.activityRecognitionResult) {
                    snapshotActivityContainer.text = getString(
                        R.string.user_activity,
                        mostProbableActivity.stateString(),
                        mostProbableActivity.confidence
                    )
                }
                snapshotActivityContainer.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
            .addOnFailureListener {
                handleSnapshotError(snapshotActivityContainer, it)
            }
    }

    private fun handleSnapshotError(view: TextView, error: Throwable) {
        with(view) {
            text = error.message
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
        }
    }

    inner class FenceReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val fenceState = FenceState.extract(intent)
            if (fenceState.fenceKey == FENCE_KEY) {
                when (fenceState.currentState) {
                    FenceState.TRUE -> {
                        this@MainActivity.fenceLogTv.append(
                            "<br><font color=red>what:</font> Success, when: ${Date(
                                fenceState.lastFenceUpdateTimeMillis
                            ).toDateTimeFormat()}".asHtml()
                        )
                    }
                    FenceState.FALSE -> {
                        this@MainActivity.fenceLogTv.append(
                            "<br><font color=red>what:</font> Failure, when: ${Date(
                                fenceState.lastFenceUpdateTimeMillis
                            ).toDateTimeFormat()}".asHtml()
                        )
                    }
                    FenceState.UNKNOWN -> {
                        this@MainActivity.fenceLogTv.append(
                            "<br><font color=red>what:</font> Unknown, when: ${Date(
                                fenceState.lastFenceUpdateTimeMillis
                            ).toDateTimeFormat()}".asHtml()
                        )
                    }
                    else -> {
                        this@MainActivity.fenceLogTv.append(
                            "<br><font color=red>what:</font> Something wrong, when: ${Date(
                                fenceState.lastFenceUpdateTimeMillis
                            ).toDateTimeFormat()}".asHtml()
                        )
                    }
                }
            }
        }
    }
}