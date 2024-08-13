/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kynetics.uf.clientexample.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.kynetics.uf.android.api.Communication
import com.kynetics.uf.android.api.UFServiceConfigurationV2
import com.kynetics.uf.android.api.UFServiceInfo
import com.kynetics.uf.android.api.toOutV1Message
import com.kynetics.uf.android.api.v1.UFServiceMessageV1
import com.kynetics.uf.clientexample.BuildConfig
import com.kynetics.uf.clientexample.R
import com.kynetics.uf.clientexample.data.MessageHistory
import com.kynetics.uf.clientexample.fragment.ListStateFragment
import com.kynetics.uf.clientexample.fragment.UFServiceInteractionFragment
import java.lang.ref.WeakReference
import java.util.*
import kotlin.concurrent.timer
import kotlin.properties.Delegates

/**
 * @author Daniele Sergio
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var twoPane: Boolean = false

    private var timer: Timer? = null

    /** Messenger for communicating with service.  */
    internal var mService: Messenger? = null

    /** Flag indicating whether we have called bind on the service.  */
    internal var mIsBound: Boolean by Delegates.observable(false) { _, old, new ->
        when {
            new == old -> {}

            new -> {
                serviceDisconnectSnackBar?.dismiss()
                timer?.purge()
                timer?.cancel()
            }

            !new -> {
                serviceDisconnectSnackBar?.show()
                timer = timer(
                    name = "Service Reconnection",
                    initialDelay = 5_000,
                    period = 30_000.toLong()
                ) {
                    Log.i(TAG, "Try reconnection")
                    doBindService()
                }
                mService = null
            }
        }
    }

    private var serviceDisconnectSnackBar: Snackbar? = null
    private var authSnackBar: Snackbar? = null
    private var authWarningSnackBar: Snackbar? = null

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    internal val mMessenger: Messenger by lazy {
        Messenger(IncomingHandler(this))
    }
    private var mServiceExist = false

    /**
     * Class for interacting with the main interface of the service.
     */
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            mService = Messenger(service)

            Toast.makeText(this@MainActivity, R.string.ui_connected,
                Toast.LENGTH_SHORT).show()

            handleRemoteException {
                mService?.send(Communication.V1.In.RegisterClient(mMessenger).toMessage())
                mService?.send(Communication.V1.In.Sync(mMessenger).toMessage())
            }
            mIsBound = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.i(TAG, "Service is disconnected")
            doUnbindService()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.i(TAG, "Service binding is died")
            mIsBound = false
        }
    }

    private var mNavigationView: NavigationView? = null

    private fun handleRemoteException(body: () -> Unit) {
        try {
            body.invoke()
        } catch (e: RemoteException) {
            Toast.makeText(this@MainActivity, "service communication error",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun NavigationView.configure(
        listener: NavigationView.OnNavigationItemSelectedListener) {
        mNavigationView!!.setNavigationItemSelectedListener(listener)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val textViewUiVersion = findViewById<TextView>(R.id.ui_version)
        val textViewServiceVersion = findViewById<TextView>(R.id.service_version)
        textViewUiVersion.text =
            String.format(getString(R.string.ui_version), BuildConfig.VERSION_NAME)
        try {
            val info = packageManager.getPackageInfo(UFServiceInfo.SERVICE_PACKAGE_NAME, 0)
            textViewServiceVersion.text =
                String.format(getString(R.string.service_version), info.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            textViewServiceVersion.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val mToolbar = findViewById<Toolbar>(R.id.my_toolbar)
        setSupportActionBar(mToolbar)
        supportActionBar?.title = getString(R.string.app_title)

        val toggle = ActionBarDrawerToggle(
            this, drawer, mToolbar, R.string.navigation_drawer_open,
            R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        val navigationViewWrapper: NavigationView = findViewById(R.id.nav_view_wrapper)
        mNavigationView = navigationViewWrapper.findViewById(R.id.nav_view)

        serviceDisconnectSnackBar = Snackbar.make(findViewById(R.id.coordinatorLayout),
            R.string.service_disconnected, Snackbar.LENGTH_INDEFINITE)

        serviceDisconnectSnackBar?.show()

        navigationViewWrapper.configure(this)
        initAccordingScreenSize()
    }

    override fun onStart() {
        super.onStart()
        doBindService()
    }

    override fun onStop() {
        super.onStop()
        doUnbindService()
    }

    override fun onBackPressed() {
        val drawer = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        when {
            drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
            !twoPane -> onBackPressedWithOnePane()
            else -> super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.menu_settings -> {
                val settingsIntent = Intent(UFServiceInfo.ACTION_SETTINGS)
                settingActivityResultLauncher.launch(settingsIntent)
            }

            R.id.force_ping -> {
                Log.d(TAG, "Force Ping Request")
                handleRemoteException {
                    mService?.send(Communication.V1.In.ForcePing.toMessage())
                }
            }

            R.id.menu_back -> onBackPressedWithOnePane()
        }

        val drawer = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    private fun sendPermissionResponse() {
        handleRemoteException {
            mService?.send(Communication.V1.In.AuthorizationResponse(true).toMessage())
        }
    }

    private var settingActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { _ ->
        syncWithUfService()
    }

    private fun syncWithUfService() {
        handleRemoteException {
            mService?.send(Communication.V1.In.Sync(mMessenger).toMessage())
        }
    }

    /**
     * Handler of incoming messages from service.
     */
    internal class IncomingHandler(mainActivity: MainActivity) :
        Handler(Looper.getMainLooper()) {
        private val activityRef = WeakReference(mainActivity)
        private var currentConfiguration: UFServiceConfigurationV2? = null

        private fun <T> WeakReference<T>.execute(action: T.() -> Unit) {
            get()?.let { ref ->
                ref.action()
            }
        }

        override fun handleMessage(msg: Message) {
            runCatching {
                @Suppress("DEPRECATION")
                when (val v1Msg = msg.toOutV1Message()) {

                    is Communication.V1.Out.CurrentServiceConfiguration ->
                        handleServiceConfigurationMsg(v1Msg)

                    is Communication.V1.Out.AuthorizationRequest ->
                        handleAuthorizationRequestMsg(v1Msg)

                    is Communication.V1.Out.ServiceNotification ->
                        handleServiceNotificationMsg(v1Msg)

                    is Communication.V1.Out.CurrentServiceConfigurationV2 ->
                        handleServiceConfigurationV2Msg(v1Msg)
                }
            }.onFailure {
                Log.w(TAG, "Cannot handle the $msg message", it)
            }
        }

        private fun handleServiceConfigurationMsg(
            @Suppress("DEPRECATION")
            currentServiceConfiguration: Communication.V1.Out.CurrentServiceConfiguration
        ) {
            Log.i(TAG, currentServiceConfiguration.conf.toString())
        }

        private fun handleServiceConfigurationV2Msg(
            currentServiceConfiguration: Communication.V1.Out.CurrentServiceConfigurationV2
        ) {
            currentConfiguration = currentServiceConfiguration.conf
            Log.i(TAG, currentServiceConfiguration.conf.toString())
        }

        private fun handleAuthorizationRequestMsg(
            authRequest: Communication.V1.Out.AuthorizationRequest) =
            activityRef.execute {
                showAuthorizationDialog(authRequest.authName)
            }

        private fun handleServiceNotificationMsg(
            serviceNotification: Communication.V1.Out.ServiceNotification) =
            activityRef.execute {
                val content = serviceNotification.content
                when (content) {
                    is UFServiceMessageV1.Event -> {
                        addEventToMessageHistory(content)
                    }

                    is UFServiceMessageV1.State -> {
                        MessageHistory.addState(MessageHistory.StateEntry(state = content))
                    }
                }

                supportFragmentManager.fragments
                    .filterIsInstance<UFServiceInteractionFragment>()
                    .forEach { fragment -> fragment.onMessageReceived(content) }

                when (content) {
                    is UFServiceMessageV1.State.WaitingDownloadAuthorization,
                    UFServiceMessageV1.State.WaitingUpdateAuthorization -> {
                        handleAuthorizationsState(content)
                    }

                    is UFServiceMessageV1.State -> {
                        authSnackBar?.dismiss()
                        authWarningSnackBar?.dismiss()
                    }

                    else -> {}
                }
            }

        private fun MainActivity.handleAuthorizationsState(event: UFServiceMessageV1) {
            val authName =
                if (event is UFServiceMessageV1.State.WaitingDownloadAuthorization) {
                    "download"
                } else {
                    "update"
                }
            if (!isApiModeOn() && !isUfServiceNew()) {
                showAuthWarningSnackBar()
            } else {
                showAuthSnackBar(authName)
            }
        }

        private fun Context.addEventToMessageHistory(event: UFServiceMessageV1.Event) {
            post {
                if (!MessageHistory.appendEvent(event)) {
                    Toast.makeText(
                        applicationContext,
                        event.name.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        private fun isApiModeOn(): Boolean {
            return currentConfiguration?.isApiMode ?: false
        }

        private fun MainActivity.showAuthorizationDialog(authType: String) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            val titleResource = resources.getIdentifier(String.format("%s_%s",
                authType.lowercase(), "title"),
                "string", packageName)
            val contentResource = resources.getIdentifier(String.format("%s_%s",
                authType.lowercase(), "content"),
                "string", packageName)
            builder.apply {
                setTitle(titleResource)
                setMessage(contentResource)
                setCancelable(false)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    sendPermissionResponse()
                }
                setNegativeButton(R.string.close) { _, _ ->
                }
            }

            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }

    private fun showAuthSnackBar(authType: String) {
        val content = getString(R.string.auth_request_toast_message, authType)
        val toolbarView = findViewById<View>(R.id.my_toolbar)
        authSnackBar = Snackbar.make(toolbarView, content, Snackbar.LENGTH_INDEFINITE)
            .setBehavior(NoSwipeBehavior())
            .setAction(R.string.action_grant) {
                sendPermissionResponse()
            }
        authSnackBar?.show()
    }

    private fun showAuthWarningSnackBar() {
        val toolbarView = findViewById<View>(R.id.my_toolbar)
        authWarningSnackBar = Snackbar.make(toolbarView, R.string.auth_request_warning,
            Snackbar.LENGTH_INDEFINITE)
            .setBehavior(NoSwipeBehavior())
        authWarningSnackBar?.show()
    }

    @Suppress("DEPRECATION")
    private fun isUfServiceNew(): Boolean {
        val ufServicePackage =
            packageManager.getPackageInfo(UFServiceInfo.SERVICE_PACKAGE_NAME, 0)
        val ufServiceVersionCode = ufServicePackage.versionCode
        return ufServiceVersionCode >= 10600
    }

    fun changePage(fragment: Fragment, addToBackStack: Boolean = true) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(R.id.main_content, fragment)

        if (addToBackStack) {
            tx.addToBackStack(null)
        }

        tx.commit()
    }

    private fun initAccordingScreenSize() {
        val stateDetailContainer = findViewById<View>(R.id.state_detail_container)
        twoPane = stateDetailContainer != null
        val listStateFragment = ListStateFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ListStateFragment.ARG_TWO_PANE, this@MainActivity.twoPane)
            }
        }
        if (twoPane) {
            this.supportFragmentManager
                .beginTransaction()
                .replace(R.id.state_list_container, listStateFragment)
                .commit()
        } else {
            changePage(listStateFragment, false)
        }
    }

    private fun onBackPressedWithOnePane() {
        val count = supportFragmentManager.backStackEntryCount
        if (count == 0) {
            super.onBackPressed()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    private fun doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        val intent = Intent(UFServiceInfo.SERVICE_ACTION)
        intent.setPackage(UFServiceInfo.SERVICE_PACKAGE_NAME)
        intent.flags = FLAG_INCLUDE_STOPPED_PACKAGES
        val serviceExist = bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        if (!serviceExist && !mServiceExist) {
            Toast.makeText(applicationContext, "UpdateFactoryService not found",
                Toast.LENGTH_LONG).show()
            unbindService(mConnection)
            this.finish()
        } else {
            mServiceExist = true
        }
    }

    private fun doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            try {
                mService?.send(Communication.V1.In.UnregisterClient(mMessenger).toMessage())
            } catch (e: RemoteException) {
                // There is nothing special we need to do if the service
                // has crashed.
            }

            // Detach our existing connection.
            unbindService(mConnection)
            mIsBound = false
        }
    }

    internal class NoSwipeBehavior : BaseTransientBottomBar.Behavior() {
        override fun canSwipeDismissView(child: View): Boolean {
            return false
        }
    }


    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
