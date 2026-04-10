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
```

Bonne exploration ! Ajustez les sources et la configuration pour vos usages.

