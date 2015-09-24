# thumb
Thumb - a command line thumbnail maker

(original email sent to a few friends using the software below, in French)

Voici le logiciel en Scala pour créer les miniatures et les photos avec masque.

Je l'ai préconfiguré pour qu'il fonctionne tel quel (script test.sh), la configuration est dans le fichier conf.json. Lionel sait comment le modifier (doc. sur le Shuttle).

Pour tester le logiciel, il suffit d'installer SBT (cf. http://www.scala-sbt.org/) et de lancer le script. Au premier démarrage, SBT va télécharger tous les JAR requis, y compris le compilateur Scala.

J'utilise quelques dépendances, des packages pour le JSON et une librairie pour un traitement simplifié des images.

Pour créer un fichier JAR qui contient toutes les dépendances (équivalent à un fichier .exe dans le monde Java), il y a une tâche SBT "assembly" qui crée un JAR dans le dossier target/scala-2.11.

C'est ce que j'ai mis sur le Shuttle (et non pas tout le chenis de SBT, vu que le Shuttle n'est pas connecté au Net).

Si tu veux jeter un coup d'oeil, Iaro, fais attention aux points suivants:

- les sources sont dans src/main/scala
- la configuration est chargée depuis des fichiers JSON, une forme de "grammaire" est créée automatiquement par Play/JSON depuis les classes du fichier "Config.scala"
- la classe CtrlCWatch implémente une vérif. du signal SIGINT
- la classe Log est passée implicitement entre les méthodes pour définir une "méthode" log() qui affiche le message à l'écran tout en l'appendant dans un fichier texte
- il y a des méthodes additionelles (comme les enrichments de C#) pour certaines classes, dans le fichier package.scala
- le reste est codé de manière assez explicite

Comme IDE, j'utilise emacs, mais te recommande IntelliJ avec le plugin Scala si tu veux pouvori naviguer facilement dans le code en cliquant.
