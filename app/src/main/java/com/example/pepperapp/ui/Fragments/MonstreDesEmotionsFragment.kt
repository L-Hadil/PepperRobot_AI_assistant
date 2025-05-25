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

class MonstreDesEmotionsFragment : Fragment(), RobotLifecycleCallbacks {

    private lateinit var tvSection: TextView
    private var qiContext: QiContext? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    private val storyLines = listOf(
        "Aujourd’hui, je vais vous raconter l’histoire d’un petit monstre…",
        "Un monstre très gentil… mais un peu tout mélangé !",
        "Il s’appelle le monstre des couleurs.",
        "Ce matin, il s’est réveillé… et rien ne va plus !",
        "Il se sent tout bizarre, comme s’il avait plein de choses dans son cœur… mais il ne sait pas quoi.",
        "Alors son amie lui dit :",
        "— “Tes émotions sont toutes mélangées ! Il faut les ranger. Tu veux que je t’aide ?”",
        "Le monstre dit oui avec sa grosse tête.",
        "Alors ils commencent…",

        "D’abord, la joie.",
        "Elle est jaune comme le soleil.",
        "C’est quand tu ris très fort, que tu joues avec tes amis, ou que tu manges ton goûter préféré.",
        "La joie fait battre ton cœur plus vite et te donne envie de sauter partout !",
        "Est-ce que vous avez déjà ressenti la joie ?",

        "Ensuite, la tristesse.",
        "Elle est bleue, comme la pluie.",
        "C’est quand ton cœur est tout mou, que tu veux être seul, ou que tu as perdu ton doudou…",
        "La tristesse fait couler les larmes… mais parfois, elle fait du bien aussi.",
        "Vous avez déjà été tristes ? Moi aussi… parfois.",

        "Puis vient la colère.",
        "Elle est rouge, comme un volcan !",
        "C’est quand quelque chose t’énerve très fort, que tu veux crier ou taper du pied.",
        "Mais attention : la colère, il ne faut pas la laisser exploser !",
        "Quand vous êtes en colère, vous faites quoi ?",

        "Après, il y a la peur.",
        "Elle est grise comme un orage.",
        "C’est quand tu crois qu’il y a un monstre sous ton lit… même si ce n’est pas vrai.",
        "La peur te fait tout petit, comme une souris.",
        "Moi, j’ai déjà eu peur d’un aspirateur ! Et vous ?",

        "Et ensuite… il y a la sérinité.",
        "Il est vert, comme les feuilles d’un arbre.",
        "C’est quand tu es bien, que tu respires doucement, que tu écoutes une histoire.",
        "Le calme, c’est doux comme un câlin.",
        "Fermez les yeux, respirez doucement… vous sentez le calme ?",

        "Mais il y a encore une émotion très spéciale : l’amour.",
        "L’amour est rose, comme un bisou ou un cœur.",
        "C’est quand ton cœur est tout chaud, que tu penses fort à quelqu’un que tu aimes.",
        "L’amour, c’est quand tu fais un câlin, ou que tu tiens la main de quelqu’un.",
        "Et vous ? À qui avez-vous envie de dire 'je t’aime' aujourd’hui ?",

        "Alors le monstre a tout rangé",
        "Une couleur pour chaque émotion.",
        "Et maintenant, il se sent bien. Il comprend ce qui se passe en lui.",
        "Et vous, les enfants ?",
        "Quelle est votre couleur aujourd’hui ?"
    )


    private val animationsByLine = listOf(
        "animations/01-Hello/Hello_02.qianim",                    // Intro du monstre
        "animations/06-Solitaries/Funny_01.qianim",              // Monstre gentil mélangé
        "animations/06-Solitaries/LookHandRight_01.qianim",      // Présentation du nom
        "animations/07-Reactions/SadReaction_01.qianim",         // Réveil bizarre
        "animations/06-Solitaries/Looking_around_01.qianim",     // Sentiment confus
        null,                                                    // Citation de l’amie
        "animations/06-Solitaries/LookHandLeft_01.qianim",       // “Tes émotions sont mélangées”
        "animations/02-Body_Parts/Show_Hand_Both_01.qianim",     // Grosse tête qui dit oui
        "animations/07-Reactions/NiceReaction_01.qianim",        // Ils commencent

        "animations/07-Reactions/NiceReaction_01.qianim",        // La joie
        "animations/01-Hello/Hello_04.qianim",                   // Jaune soleil
        "animations/06-Solitaries/Funny_02.qianim",              // Goûter, amis
        "animations/06-Solitaries/PlayWithHandBoth_01.qianim",   // Sauter partout
        null,                                                    // Question ouverte

        "animations/07-Reactions/SadReaction_01.qianim",         // Tristesse
        "animations/06-Solitaries/LookDown_01.qianim",           // Bleue pluie
        "animations/06-Solitaries/LookRight_01.qianim",          // Perdu doudou
        "animations/07-Reactions/SadReaction_02.qianim",         // Larmes
        null,                                                    // Moi aussi…

        "animations/04-Make_Space/Make_Space_02.qianim",         // Colère
        "animations/01-Hello/Hello_05.qianim",                   // Rouge volcan (choix symbolique)
        "animations/06-Solitaries/LookHandLeft_01.qianim",       // Crier
        "animations/06-Solitaries/LookBumpersLeft_01.qianim",    // Attention à la colère
        null,                                                    // Question ouverte

        "animations/06-Solitaries/LookFarLeft_01.qianim",        // Peur
        "animations/06-Solitaries/LookBumpersRight_01.qianim",   // Noire nuit
        "animations/06-Solitaries/LookAtSidesRight_01.qianim",   // Monstre sous le lit
        "animations/06-Solitaries/LookHandRight_01.qianim",      // Tout petit
        null,                                                    // Aspirateur

        "animations/07-Reactions/NiceReaction_01.qianim",        // Calme
        "animations/07-Reactions/NiceReaction_02.qianim",        // Vert arbre
        "animations/06-Solitaries/Looking_around_01.qianim",     // Respiration douce
        "animations/02-Body_Parts/Show_Body_03.qianim",          // Câlin
        null,                                                    // Fermez les yeux

        "animations/07-Reactions/NiceReaction_01.qianim",        // Amour (ajout)
        "animations/01-Hello/Hello_04.qianim",                   // Rose comme un bisou
        "animations/06-Solitaries/PlayWithHandBoth_01.qianim",   // Cœur chaud, câlin
        "animations/02-Body_Parts/Show_Hand_Both_01.qianim",     // Tenir la main
        null,                                                    // Je t’aime

        "animations/05-Enumeration/Enumeration_01.qianim",       // Rangement final
        null,                                                    // Une couleur pour chaque émotion
        "animations/07-Reactions/NiceReaction_02.qianim",        // Monstre soulagé
        null,                                                    // Et vous ?
        "animations/05-Enumeration/Enumeration_02.qianim"        // Quelle est ta couleur ?
    )




    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Enregistrer pour recevoir les callbacks QiSDK
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
                // Met à jour le texte sur le thread UI
                withContext(Dispatchers.Main) {
                    tvSection.text = line
                }

                // Lecture de la phrase
                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText(line)
                        .build()
                        .run()
                }

                // Animation éventuelle
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
                        } catch (_: Exception) { /* ignore */ }
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
        // Rien à faire
    }

    override fun onDestroyView() {
        // Se désinscrire des callbacks QiSDK
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }
}
