# peersim-djl

Projet Maven minimal pour démarrer avec PeerSim et DJL (Deep Java Library).

## Quick start

1. Compiler et télécharger les dépendances:

```powershell
mvn -DskipTests=true compile
```

2. Exécuter l'exemple principal (`App`) :

```powershell
mvn exec:java
```

3. Pour lancer le starter PeerSim (nécessite que PeerSim et ses dépendances soient
	 disponibles dans le repo Maven local or sur le classpath) :

```powershell
mvn exec:java "-Dexec.mainClass=com.example.peersimdjl.PeerSimStarter"
```

## Détails et notes

- **PeerSim** :
	- Si Maven ne trouve pas PeerSim, téléchargez le JAR depuis le site officiel
		(http://peersim.sourceforge.net/) puis installez-le localement :

		```powershell
		mvn install:install-file -Dfile=C:\\chemin\\vers\\peersim.jar \
				-DgroupId=peersim -DartifactId=peersim -Dversion=1.0 -Dpackaging=jar
		```
	- PeerSim dépend également de quelques bibliothèques tierces (par exemple
		JEP). Si l'exécution échoue avec une erreur du type
		`NoClassDefFoundError: org.nfunk.jep.SymbolTable`, installez aussi le jar
		correspondant :

		```powershell
		mvn install:install-file -Dfile=C:\\chemin\\vers\\jep.jar \
				-DgroupId=org.nfunk -DartifactId=jep -Dversion=2.24 -Dpackaging=jar
		```

	- Le template `src/main/resources/peersim.cfg` est fourni. Adaptez-le à vos
		propres protocoles (`protocol.*`) et contrôles (`control.*`).

- **DJL** :
	- La gestion des dépendances DJL se fait via le BOM. La version par défaut
		(`0.24.0`) est définie dans le `pom.xml`. Modifiez la propriété
		`djl.version` si nécessaire.
	- Pour changer de backend (TensorFlow, MXNet, etc.), remplacez
		`ai.djl.pytorch:pytorch-engine` par l'engine souhaité.

## Exemples de commande complètes

```powershell
mvn -DskipTests=true compile                               # compilation
mvn exec:java                                             # exécute App
mvn exec:java "-Dexec.mainClass=com.example.peersimdjl.PeerSimStarter"  # démarre PeerSim
mvn test                                                  # lance les tests unitaires/intégration logique
```

## Extension Federated Learning (Chord DHT)

L'extension FL décentralisée utilise les clés DHT suivantes :

- `grad/epoch/{E}/param/{i}` : dépôt des contributions de gradient (`ParamDepot`)
- `global/epoch/{E}/param/{i}` : paramètre agrégé global
- `vote/epoch/{E}/node/{nodeId}` : vote de convergence d'un nœud
- `decision/epoch/{E}` : décision de quorum

Paramètres de config disponibles dans `peersim.cfg` (préfixe `control.learning.*`) :

- `federatedEpochs` (défaut: `3`)
- `learningRate` (défaut: `0.05`)
- `numParams` (défaut: `4`)
- `preprocessOnUpload` (défaut: `true`) : active le prétraitement automatique du CSV importé

Prétraitement appliqué quand `preprocessOnUpload=true` :

- Détection/suppression de ligne d'en-tête
- Remplacement des valeurs manquantes (`""`, `?`, `NA`, `null`) par `0`
- Encodage des colonnes catégorielles (mapping entier par colonne)
- Normalisation min-max colonne par colonne dans `[0,1]`

Composants ajoutés :

- `ParamEntry`, `ParamDepot`
- `GradientPublisher`, `DepotAggregator`
- `GlobalModelCollector`
- `ConvergenceVoter`, `VoteCollector`
- `AccuracyTracker`
- `DjlParameterCodec` (NDArray ↔ `float[]`)

Bonne exploration ! Ajustez les sources et la configuration pour vos usages.

