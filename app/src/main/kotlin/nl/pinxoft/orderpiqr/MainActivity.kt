package nl.pinxoft.orderpiqr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import nl.pinxoft.orderpiqr.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var cameraSource: CameraSource? = null
    private lateinit var barcodeScannerProcessor: BarcodeScannerProcessor

    private val tag = "PiQR"

    private var pickList: PickList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeScannerProcessor = BarcodeScannerProcessor(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<Button>(R.id.scanButton).setOnClickListener {
            instruction = ""
            startCameraSource()
        }

        if (allPermissionsGranted()) {
            createCameraSource()
        } else {
            getRuntimePermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        with(this.getPreferences(Context.MODE_PRIVATE).edit()) {
            clear()
            putString("scanResult", instruction)
            putString("pickList", pickList?.PickListString)
            putInt("lastPickedIndex", pickList?.LastPickedIndex ?: -1)
            commit()
        }
    }

    override fun onResume() {
        super.onResume()
        with(this.getPreferences(Context.MODE_PRIVATE)) {
            instruction = getString("scanResult", "") ?: ""
            val pickListString = getString("pickList", "") ?: ""
            if (pickListString.isNotEmpty())
                setPickList(pickListString)
            pickList?.LastPickedIndex = getInt("lastPickedIndex", -1)
        }
    }

    fun handleScanResult(text: String) {
        instruction = text
        if (instruction.length > 12)
            setPickList(text)
        else
            pickItem(text)
        if (!continuousScanning)
            cameraSource?.stop()
    }

    private fun setPickList(text: String) {
        pickList = PickList(text)
        showState(ScanState.NewPickList)
    }

    private fun pickItem(text: String) {
        if (pickList == null) {
            instruction = "Eerst een pickbon scannen!"
            return
        }
        val scanSuccessful = pickList!!.Pick(instruction)
        instruction = pickList!!.ItemToScan().joinToString ("\n")
        if (scanSuccessful) {
            pickList!!.LastPickedIndex += 1
            instruction = pickList!!.ItemToScan().joinToString("\n")
            showState(ScanState.Success)
        } else
            showState(ScanState.Failure)
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(this)
        }
        try {
            cameraSource!!.setMachineLearningFrameProcessor(barcodeScannerProcessor)
        } catch (e: RuntimeException) {
            Log.e(tag, "Can not create barcode image processor", e)
            Toast.makeText(
                applicationContext,
                "Can not create image processor: " + e.message,
                Toast.LENGTH_LONG
            )
                .show()
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private fun startCameraSource() {
        if (cameraSource != null) {
            try {
                // Inserted by Jetbrains:
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                cameraSource!!.start()
            } catch (e: IOException) {
                Log.e(tag, "Unable to start camera source.", e)
                cameraSource!!.release()
                cameraSource = null
            }
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return try {
            val info = this.packageManager
                .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
            val ps = info.requestedPermissions
            if (ps != null && ps.size > 0) {
                ps
            } else {
                emptyArray()
            }
        } catch (e: Exception) {
            emptyArray()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false
            }
        }
        return true
    }

    private fun getRuntimePermissions() {
        val allNeededPermissions: MutableList<String> = ArrayList()
        for (permission in getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission)
            }
        }
        if (allNeededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                allNeededPermissions.toTypedArray(),
                1
            )
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(tag, "Permission granted: $permission")
            return true
        }
        Log.i(tag, "Permission NOT granted: $permission")
        return false
    }

    private var instruction: String
        get() = findViewById<TextView>(R.id.scanResult).text.toString()
        set(value) {
            findViewById<TextView>(R.id.scanResult).text = value
        }

    private val continuousScanning: Boolean
        get() = findViewById<Switch>(R.id.continuousScan).isChecked

    private fun showState(state: ScanState) {
        when (state) {
            ScanState.Success ->
                window.decorView.setBackgroundColor(Color.GREEN)
            ScanState.Failure ->
                window.decorView.setBackgroundColor(Color.RED)
            else ->
                window.decorView.setBackgroundColor(Color.WHITE)
        }
    }
}