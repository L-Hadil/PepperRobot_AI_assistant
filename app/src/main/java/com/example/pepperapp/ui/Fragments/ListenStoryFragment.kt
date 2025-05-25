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

class ListenStoryFragment : Fragment(), RobotLifecycleCallbacks {

    private lateinit var tvSection: TextView
    private var qiContext: QiContext? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    private val storyLines = listOf(
        "Mamamouth, et, Papapouth, étaient, très, ennuyés. Depuis, sa, naissance, leur, fils, Helmouth, étai adorable, mais il avait un grave défaut : il n’avait pas de touffe de poils sur sa tête. C’était bien le seul mammouth au crâne lisse comme une bille.",
        "Alors pendant des mois, Mamamouth et Papapouth eurent recours à un stratagème très malin. La nuit, dans les marais, ils déterraient des mottes de terre et de longues herbes folles, qu'ils mettaient sur la tête de leur fiston. De loin, les autres animaux pensaient que Helmouth était parfaitement poilu.",
        "Seul Picpic, un petit oiseau pique-bœuf à bec rouge, était au courant de ce secret. Il vivait sur le dos d'Helmouth et mangeait les insectes qui venaient le parasiter. Quel bonheur d’habiter sur un garde-manger vivant, avec nourriture à domicile, sans besoin d’aller chasser ! Et Picpic n’avait cure de la tonsure de son ami.",
        "Mais un jour, catastrophe ! Une tempête emporta la fausse touffe d’Helmouth.",
        "Aussitôt, tous les animaux entourèrent le pauvre petit et l’observèrent avec stupéfaction et mépris. Les mamoutheaux du troupeau se moquèrent de lui en pouffant bêtement : « Il est ridicule ! Il ne ressemble à rien ! »",
        "Alors Helmouth fut très triste d’être la risée des mammouths et la honte de ses parents. Avalant quelques petites mouches collées à la peau d’Helmouth, Picpic se redressa soudain, chagriné de sentir son ami si malheureux.",
        "En colère contre tous les animaux, il leur lança de sa petite voix aiguë : « Vous êtes des imbéciles ! Plutôt que de ricaner comme des sales gamins, vous feriez mieux de l’aider ! Il faut trouver une moumoute pour Helmouth. »",
        "Calmés par le petit mais autoritaire Picpic, tous les animaux prirent un air sérieux et responsable. Ils se mirent à réfléchir en adultes.",
        "Soudain, le cheval eut une idée : « Je vais lui prêter un peu de ma belle chevelure. » Mais, voyant un mamoutheau avec une longue crinière blanche qui lui cachait les yeux, tous les animaux éclatèrent de rire, et le cheval fut très vexé.",
        "Le mouton s'approcha : « Je vais lui prêter un peu de ma magnifique laine ! » Mais, voyant un mammoutheau tout frisé, tous les animaux explosèrent de rire, et le mouton fut très vexé.",
        "Le renard s’avança : « Je vais lui prêter un peu de ma somptueuse fourrure. » Mais, voyant un mammoutheau rouquin, tous les animaux furent pliés de rire, et le renard fut très vexé.",
        "« Je vais prêter un peu de mes redoutables piquants ! » dit le porc-épic. Mais, voyant un mammoutheau tout hérissé, tous les animaux s’étouffèrent de rire, et le porc-épic fut très vexé.",
        "« Je vais lui prêter quelques-unes de mes majestueuses plumes ! » s’exclama le paon. Mais, voyant un mamoutheau avec un grand plumeau coloré sur la tête, tous les animaux tombèrent par terre de rire, et le paon fut très vexé.",
        "« Il n'y a que moi qui puisse apporter la solution ! » rugit le lion. Mais, voyant un mamoutheau avec une crinière royale, tous les animaux se retinrent de rire (et même de glousser), car ils n’avaient pas envie de se faire dévorer.",
        "« J’en ai marre !!! » cria soudain Helmouth, à bout de nerfs. « C’est vrai !!! Ça tourne au grand n’importe quoi. » s’impatienta Picpic.",
        "Mamamouth et Papapouth accoururent auprès de leur fiston : « Nous, on t’aime comme tu es, et ce n’est pas grave si tu es différent des autres. »",
        "« Ils ont raison, tu es toi-même. Tu es unique, on t’aime comme ça et on va te le prouver ! » s’exclama Picpic.",
        "Tenant fermement un caillou très affûté dans son bec rouge, Picpic se mit à raser le dessus de la tête de Mamamouth, de Papapouth et de tous les animaux. (Enfin, tous sauf le lion, car Picpic n’avait pas envie de se faire déchiqueter.) Du coup, très jaloux, tous les mamoutheaux voulurent la même coiffure qu’Helmouth !",
        "Finalement, tout le monde imita Helmouth, qui au départ ne ressemblait soi-disant à rien, et qui, sans le vouloir, avait lancé le top de la mode capillaire ! Helmouth comprit alors que sa différence était devenue sa force."
    )

    private val animationsByLine = listOf(
        "animations/07-Reactions/SadReaction_01.qianim",
        "animations/BodyParts/Show_Hand_Both_01.qianim",
        "animations/Solitaries/Funny_01.qianim",
        "animations/Solitaries/Looking_around_01.qianim",
        "animations/07-Reactions/SadReaction_02.qianim",
        "animations/Solitaries/LookFarLeft_01.qianim",
        "animations/Orientation/PointFrontL_01.qianim",
        "animations/Solitaries/LookAtSidesRight_01.qianim",
        "animations/Solitaries/Funny_02.qianim",
        "animations/Solitaries/LookHandRight_01.qianim",
        "animations/Solitaries/LookRight_01.qianim",
        "animations/Solitaries/LookBumpersRight_01.qianim",
        "animations/Attract/Attract_R03.qianim",
        "animations/Back_to_stand/Back_to_stand_03.qianim",
        "animations/Make_space/Make_Space_02.qianim",
        "animations/Hello/Hello_05.qianim",
        "animations/Enumeration/Enumeration_01.qianim",
        "animations/07-Reactions/NiceReaction_01.qianim",
        "animations/07-Reactions/NiceReaction_02.qianim"
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
                // Met à jour le texte sur le thread UI
                withContext(Dispatchers.Main) {
                    tvSection.text = line
                }

                // Parole
                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText(line)
                        .build()
                        .run()
                }

                // Animation
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
                        } catch (_: Exception) {
                            // ignore missing animation
                        }
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

    }

    override fun onDestroyView() {
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }
}
