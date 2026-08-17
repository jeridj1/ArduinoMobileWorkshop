package com.arduinomobileworkshop.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivityMainBinding
import com.arduinomobileworkshop.toolchain.Board
import com.arduinomobileworkshop.toolchain.ToolchainManager
import com.arduinomobileworkshop.usb.UsbManager
import com.arduinomobileworkshop.workspace.SketchProject
import com.arduinomobileworkshop.workspace.WorkspaceManager

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "AMW_Main"
        private const val REQUEST_OPEN_DOCUMENT = 1001
    }
    
    private lateinit var binding: ActivityMainBinding
    
    private val workspaceManager: WorkspaceManager
        get() = ArduinoMobileWorkshopApp.instance.workspaceManager
    
    private val toolchainManager: ToolchainManager
        get() = ArduinoMobileWorkshopApp.instance.toolchainManager
    
    private val usbManager: UsbManager
        get() = ArduinoMobileWorkshopApp.instance.usbManager
    
    private var currentProject: SketchProject? = null
    private var isModified = false
    private var currentBoard: Board? = null
    
    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) initializeApp()
        else initializeApp()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        requestRequiredPermissions()
        initializeUi()
        setupEventListeners()
    }
    
    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            initializeApp()
        }
    }
    
    private fun initializeApp() {
        Log.d(TAG, "Initializing app")
        workspaceManager.initialize()
        toolchainManager.initialize()
        usbManager.initialize()
        
        usbManager.setDeviceListener(object : UsbManager.DeviceListener {
            override fun onDeviceAttached(device: android.hardware.usb.UsbDevice) {
                runOnUiThread { updateStatus("Device connected: ${device.deviceName}") }
            }
            override fun onDeviceDetached(device: android.hardware.usb.UsbDevice) {
                runOnUiThread { updateStatus("Device disconnected: ${device.deviceName}") }
            }
            override fun onPermissionGranted(device: android.hardware.usb.UsbDevice) {
                runOnUiThread { showToast("USB permission granted") }
            }
            override fun onPermissionDenied(device: android.hardware.usb.UsbDevice) {
                runOnUiThread { showToast("USB permission denied") }
            }
        })
        loadLastProject()
        setupMenu()
    }
    
    private fun initializeUi() {
        binding.editor.setTextSize(14f)
        binding.editor.typeface = android.graphics.Typeface.MONOSPACE
        updateBoardSelector()
        updatePortSelector()
        updateStatus("Ready")
        binding.compileOutputPanel.visibility = android.view.View.GONE
    }
    
    private fun setupEventListeners() {
        binding.buttonCompile.setOnClickListener { compileSketch() }
        binding.buttonUpload.setOnClickListener { uploadSketch() }
        binding.buttonSerialMonitor.setOnClickListener {
            startActivity(Intent(this, SerialMonitorActivity::class.java))
        }
        binding.boardSelector.setOnClickListener { showBoardSelectorDialog() }
        binding.portSelector.setOnClickListener { showPortSelectorDialog() }
    }
    
    private fun setupMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(com.arduinomobileworkshop.app.R.menu.menu_main, menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    com.arduinomobileworkshop.app.R.id.action_new -> { newSketch(); true }
                    com.arduinomobileworkshop.app.R.id.action_open -> { openSketch(); true }
                    com.arduinomobileworkshop.app.R.id.action_save -> { saveSketch(); true }
                    com.arduinomobileworkshop.app.R.id.action_save_as -> { saveSketchAs(); true }
                    com.arduinomobileworkshop.app.R.id.action_boards_manager -> {
                        startActivity(Intent(this@MainActivity, BoardsManagerActivity::class.java)); true
                    }
                    com.arduinomobileworkshop.app.R.id.action_library_manager -> {
                        startActivity(Intent(this@MainActivity, LibraryManagerActivity::class.java)); true
                    }
                    com.arduinomobileworkshop.app.R.id.action_settings -> {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java)); true
                    }
                    else -> false
                }
            }
        }, this)
    }
    
    private fun loadLastProject() {
        val recent = workspaceManager.getRecentProjects()
        if (recent.isNotEmpty()) {
            workspaceManager.openProject(recent[0])?.let { project ->
                currentProject = project
                loadSketch(project)
            }
        } else {
            newSketch()
        }
    }
    
    private fun newSketch() {
        val name = "Sketch_${System.currentTimeMillis()}"
        workspaceManager.createProject(name)?.let { project ->
            currentProject = project
            loadSketch(project)
            isModified = false
        }
    }
    
    private fun openSketch() {
        val intent = Intent(this, FilePickerActivity::class.java)
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT)
    }
    
    private fun saveSketch() {
        currentProject?.let { project ->
            val code = binding.editor.text.toString()
            if (workspaceManager.saveProject(code)) {
                isModified = false
                showToast("Sketch saved")
                updateStatus("Saved: ${project.name}")
            } else {
                showToast("Failed to save")
            }
        } ?: newSketch()
    }
    
    private fun saveSketchAs() {
        currentProject?.let { project ->
            val code = binding.editor.text.toString()
            if (workspaceManager.saveProject(code)) {
                isModified = false
                showToast("Sketch saved")
            }
        }
    }
    
    private fun loadSketch(project: SketchProject) {
        workspaceManager.readSketchCode()?.let { code ->
            binding.editor.setText(code)
            updateStatus("Loaded: ${project.name}")
            isModified = false
        } ?: run {
            binding.editor.setText("// Error loading sketch")
            showToast("Failed to load sketch")
        }
    }
    
    private fun compileSketch() {
        currentProject?.let { project ->
            currentBoard?.let { board ->
                updateStatus("Compiling...")
                binding.compileOutputPanel.visibility = android.view.View.VISIBLE
                binding.compileOutput.text = "Compiling...
"
                toolchainManager.compileSketch(project.path, board.id) { success, output ->
                    runOnUiThread {
                        binding.compileOutput.append(output)
                        if (success) {
                            updateStatus("Compilation successful")
                            showToast("Compilation successful!")
                        } else {
                            updateStatus("Compilation failed")
                            showToast("Compilation failed")
                        }
                    }
                }
            } ?: run { showToast("Please select a board first") }
        } ?: run { showToast("No sketch to compile") }
    }
    
    private fun uploadSketch() {
        currentProject?.let { project ->
            currentBoard?.let { board ->
                val devices = usbManager.getSerialDevices()
                val port = devices.firstOrNull()?.deviceName
                if (port == null) {
                    showToast("Please connect a device and select a port")
                    return
                }
                updateStatus("Uploading...")
                toolchainManager.compileAndUpload(project.path, board.id, port) { success, output ->
                    runOnUiThread {
                        binding.compileOutput.text = output
                        binding.compileOutputPanel.visibility = android.view.View.VISIBLE
                        if (success) {
                            updateStatus("Upload successful")
                            showToast("Upload successful!")
                        } else {
                            updateStatus("Upload failed")
                            showToast("Upload failed")
                        }
                    }
                }
            } ?: run { showToast("Please select a board first") }
        } ?: run { showToast("No sketch to upload") }
    }
    
    private fun showBoardSelectorDialog() {
        val boards = toolchainManager.getInstalledBoards()
        val boardNames = boards.map { it.name }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Board")
            .setItems(boardNames) { _, which ->
                currentBoard = boards[which]
                updateBoardSelector()
                showToast("Board selected: ${currentBoard?.name}")
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
    
    private fun showPortSelectorDialog() {
        val devices = usbManager.getSerialDevices()
        if (devices.isEmpty()) {
            showToast("No serial devices found")
            return
        }
        val portNames = devices.map { "${it.manufacturerName ?: "Unknown"} ${it.productName ?: "Device"} (${it.deviceName})" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Port")
            .setItems(portNames) { _, which ->
                showToast("Port selected: ${devices[which].deviceName}")
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .setPositiveButton("Refresh") { _, _ ->
                usbManager.refreshDevices()
                showPortSelectorDialog()
            }
            .show()
    }
    
    private fun updateBoardSelector() {
        binding.boardSelector.text = currentBoard?.name ?: "Select Board"
    }
    
    private fun updatePortSelector() {
        val devices = usbManager.getSerialDevices()
        binding.portSelector.text = if (devices.isNotEmpty()) {
            "${devices[0].manufacturerName ?: "Device"} (${devices[0].deviceName})"
        } else {
            "No Ports"
        }
    }
    
    private fun updateStatus(message: String) {
        binding.statusBar.text = message
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_DOCUMENT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                workspaceManager.importSketch(uri)?.let { project ->
                    currentProject = project
                    loadSketch(project)
                    isModified = false
                }
            }
        }
    }
    
    override fun onBackPressed() {
        if (isModified) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Save before exiting?")
                .setPositiveButton("Save") { _, _ -> saveSketch(); super.onBackPressed() }
                .setNegativeButton("Discard") { _, _ -> super.onBackPressed() }
                .setNeutralButton("Cancel") { _, _ -> }
                .show()
        } else {
            super.onBackPressed()
        }
    }
}
