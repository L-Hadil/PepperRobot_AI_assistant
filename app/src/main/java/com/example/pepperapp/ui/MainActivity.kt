package com.example.pepperapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.example.pepperapp.ui.Fragments.ProfileFragment
import com.example.pepperapp.R
import com.example.pepperapp.ui.Fragments.StartChildFragment

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        QiSDK.register(this, this)
    }

    override fun onDestroy() {
        QiSDK.unregister(this)
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext

        val fragment = supportFragmentManager.findFragmentById(R.id.container)
        if (fragment is ProfileFragment) {
            fragment.setQiContext(qiContext)
        }
    }

    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Toast.makeText(this, "Focus refusé : $reason", Toast.LENGTH_LONG).show()
    }

    fun getQiContext(): QiContext? = qiContext

}
