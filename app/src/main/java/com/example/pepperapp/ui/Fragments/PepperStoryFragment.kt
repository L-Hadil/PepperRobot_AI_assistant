package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PepperStoryFragment : Fragment(), RobotLifecycleCallbacks {

    private lateinit var tvSection: TextView
    private var qiContext: QiContext? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    private val storyLines = listOf(
        "Bonjour, je m'appelle Pepper.",
        "Je suis un petit robot tout blanc.",
        "Je vis dans une grande maison de robots.",
        "Là-bas, il y a Madalina.",
        "C'est la maman d'Elisa, votre camarade.",
        "Et il y a Hadil.",
        "Madalina dit : « Les enfants, c'est magique ! »",
        "Moi aussi, j'aime beaucoup les enfants !",
        "Dans ma maison, il y a une grande étagère pleine d'histoires.",
        "Chaque matin, Madalina me lit une petite histoire.",
        "Hadil la garde bien dans ma tête.",
        "Petit à petit, ma tête se remplit d'histoires rigolotes.",
        "Quand je connais bien une histoire, je la garde bien au chaud.",
        "Un jour, Madalina dit : « Pepper, va raconter tes histoires ! »",
        "Hadil dit : « Et fais bien attention aux enfants. »",
        "Alors on a mis mes roues, et je suis parti vers vous.",
        "Me voilà aujourd'hui dans votre école.",
        "Mes histoires sautent dans ma tête, prêtes à sortir.",
        "Je peux parler pour raconter.",
        "Je peux écouter pour apprendre.",
        "Quand vous riez, je suis très content.",
        "Quand vous chuchotez, je tends bien mes oreilles.",
        "Ce soir, je dirai à mes autres amis robots :",
        "« J'ai raconté plein d'histoires aux enfants aujourd'hui ! »",
        "Demain, je reviendrai avec encore plus d'histoires.",
        "Amusez-vous bien avec mes histoires,",
        "et faites-moi des gros câlins, car j'adore ça !"



    )

    private val animationsByLine = listOf(
        "animations/01-Hello/Hello_03.qianim",            // 1
        "animations/07-Reactions/NiceReaction_01.qianim", // 2
        "animations/05-Enumeration/Enumeration_01.qianim",// 3
        "animations/Solitaries/LookHandRight_01.qianim",  // 4
        "animations/Solitaries/LookHandLeft_01.qianim",   // 5
        "animations/08-Attract/Attract_R03.qianim",       // 6
        "animations/07-Reactions/Happy_01.qianim",        // 7 (if exists)
        null,                                             // 8
        null,                                             // 9
        "animations/07-Reactions/NiceReaction_02.qianim", // 10
        "animations/02-Body_Parts/Show_Hand_Both_01.qianim",//11
        "animations/04-Make_Space/Make_Space_02.qianim",  //12
        "animations/02-Body_Parts/Show_Body_01.qianim",   //13
        null,                                             //14
        null,                                             //15
        "animations/03-Tablet/Show_Tablet_01.qianim",     //16
        null,                                             //17
        "animations/06-Solitaries/Looking_around_01.qianim",//18
        "animations/07-Reactions/SadReaction_01.qianim",  //19
        "animations/07-Reactions/Funny_01.qianim",        //20
        null,                                             //21
        "animations/07-Reactions/Success_01.qianim",      //22
        "animations/05-Enumeration/Enumeration_02.qianim",//23
        "animations/05-Enumeration/Enumeration_01.qianim",//24
        "animations/07-Reactions/NiceReaction_01.qianim", //25
        null,                                             //26
        "animations/07-Reactions/Success_01.qianim",      //27
        "animations/06-Solitaries/PlayWithHandBoth_01.qianim"//28
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        QiSDK.register(requireActivity(), this)
        return inflater.inflate(R.layout.fragment_listen_story, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvSection = view.findViewById(R.id.tvStorySection)
    }

    override fun onRobotFocusGained(context: QiContext) {
        qiContext = context

        lifecycleScope.launch(Dispatchers.IO) {
            storyLines.forEachIndexed { index, line ->
                withContext(Dispatchers.Main) {
                    tvSection.text = line
                }

                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText(line)
                        .build()
                        .run()
                }

                val animJob = async {
                    animationsByLine.getOrNull(index)?.let { path ->
                        try {
                            val anim: Animation = AnimationBuilder.with(context)
                                .withAssets(path)
                                .build()
                            AnimateBuilder.with(context)
                                .withAnimation(anim)
                                .build()
                                .run()
                        } catch (_: Exception) { }
                    }
                }

                sayJob.await()
                animJob.await()
            }
        }
    }

    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        // rien
    }

    override fun onDestroyView() {
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }
}
