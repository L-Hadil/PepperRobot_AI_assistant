package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.example.pepperapp.R
import com.example.pepperapp.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StartChildFragment : Fragment(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        QiSDK.register(requireActivity(), this)
        return inflater.inflate(R.layout.fragment_start_child, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.buttonStart).setOnClickListener {
            findNavController().navigate(R.id.action_startChildFragment_to_chooseFragment)
        }
    }

    override fun onRobotFocusGained(context: QiContext) {
        qiContext = context

        lifecycleScope.launch(Dispatchers.IO) {
            try {

                val helloAnim: Animation = AnimationBuilder.with(context)
                    .withAssets("animations/01-Hello/Hello_03.qianim")
                    .build()
                val helloAction = AnimateBuilder.with(context)
                    .withAnimation(helloAnim)
                    .build()

                helloAction.async().run()


                val text = """
Bonjour les enfants! Je suis Pepper, votre robot préféré pour raconter des histoires. Aujourd'hui, je suis avec vous pour raconter une très belle histoire. Vous allez voir que vous allez adorer! Êtes-vous prêts? Je veux que vous m'écoutiez bien, car à la fin, je serai très ravi de faire votre connaissance.
""".trimIndent()
                val sayAction = SayBuilder.with(context)
                    .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                    .withText(text)
                    .build()
                sayAction.run()

                // 3) Animation "looking around"
                val lookAnim = AnimationBuilder.with(context)
                    .withAssets("animations/06-Solitaries/Looking_around_01.qianim")
                    .build()
                AnimateBuilder.with(context)
                    .withAnimation(lookAnim)
                    .build()
                    .run()


                listOf("LookRight_01.qianim", "LookLeft_01.qianim").forEach { file ->
                    val anim = AnimationBuilder.with(context)
                        .withAssets("animations/06-Solitaries/$file")
                        .build()
                    AnimateBuilder.with(context)
                        .withAnimation(anim)
                        .build()
                        .run()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onRobotFocusLost() {

        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {

    }

    override fun onDestroyView() {

        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }
}
