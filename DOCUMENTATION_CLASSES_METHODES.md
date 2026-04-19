# Documentation des classes et méthodes (`com.example.peersimdjl`)

Ce document explique **chaque classe** et **chaque méthode** du package:
`src/main/java/com/example/peersimdjl`.

> Note stockage batchs : les `DataBatch` sont persistés **physiquement** en CSV
> dans `src/main/resources/stockage/node<idString>/batch_<id>.csv`.

---

## 1) `App`

### Rôle
Point d’entrée de l’application. Lance PeerSim avec `peersim.cfg`.
En mode démo, demande le dataset et le nombre de nœuds, puis génère un config temporaire.
La démo peut aussi lancer **2 apprentissages successifs** pour visualiser le comportement.

### Méthodes

- `public static void main(String[] args)`
  - Démarre la simulation PeerSim.
  - Lit le dataset et le nombre de nœuds si aucun argument CLI n’est fourni.
  - Désactive le debug Chord pour n’afficher que les logs d’apprentissage.
  - Génère un fichier de configuration temporaire puis appelle `Simulator.main(...)`.

### Configuration de démo
- `control.learning.sessionCount = 2` pour enchaîner deux apprentissages.

- `private static String resolveConfigPath() throws URISyntaxException`
  - Cherche `peersim.cfg` dans les ressources (`ClassLoader`).
  - Retourne le chemin trouvé; sinon fallback: `src/main/resources/peersim.cfg`.

---

## 2) `DataBatch`

### Rôle
Représente un batch de données d’apprentissage distribué via le DHT,
avec persistance physique en CSV par nœud.

### Enum
- `BatchStatus`: `CREATED`, `ASSIGNED`, `STORED`, `COMPLETED`.

### Méthodes

- `public DataBatch(String batchId, double[][] data)`
  - Initialise un batch avec ID, données, état initial `CREATED`.

- `public synchronized void assignToNode(int chordId, String nodeIdString)`
  - Assigne le batch à un nœud cible.
  - Met l’état à `ASSIGNED`.

- `public synchronized void markStored()`
  - Marque le batch comme stocké (`STORED`).

- `public synchronized void markCompleted()`
  - Marque le batch comme traité (`COMPLETED`).

- `public int rowCount()`
  - Retourne le nombre de lignes du batch (`0` si `data == null`).

- `public String toString()`
  - Retourne une représentation texte utile au logging.

---

## 3) `LearningSession`

### Rôle
Encapsule l’état métier d’une session d’apprentissage distribuée.
Inclut participants, état, timestamps et versioning.

### Enum
- `SessionState`: `INIT`, `RUNNING`, `DONE`.

### Méthodes

- `public LearningSession(String sessionId, int ideNodeId, String ideNodeIdString, int totalParticipants, long createdAtTimestamp, int createdAtCycle)`
  - Construit la session (métadonnées + état initial).

- `public synchronized void addActiveNode(int chordId, String nodeIdString)`
  - Ajoute un participant actif s’il n’existe pas déjà.

- `public synchronized void removeActiveNode(int chordId)`
  - Retire un participant actif (ex: crash).

- `public synchronized void transitionToRunning()`
  - Transitionne `INIT -> RUNNING`.

- `public synchronized void transitionToDone()`
  - Transitionne vers `DONE`.

- `public synchronized void updateTimestamp(int cycle)`
  - Met à jour cycle et timestamp de dernière modification.

- `public boolean isNewerThan(LearningSession other)`
  - Compare les versions (`true` si cette session est plus récente).

- `public boolean isStale(int currentCycle, int staleCycleThreshold)`
  - Détecte l’obsolescence d’une session selon un seuil de cycles.

- `private void writeObject(ObjectOutputStream out)`
  - Hook de sérialisation personnalisée (logging + sérialisation standard).

- `private void readObject(ObjectInputStream in)`
  - Hook de désérialisation personnalisée (logging + désérialisation standard).

- `public String toString()`
  - Résumé texte de la session (état, version, participants...).

---

## 4) `DHTSessionManager`

### Rôle
Façade de haut niveau pour stocker/récupérer `LearningSession` et `DataBatch`
via `ChordProtocol`.

### Méthodes

- `public DHTSessionManager(int protocolId)`
  - Initialise le gestionnaire avec le PID Chord.

- `public boolean storeSessionInDHT(LearningSession session)`
  - Stocke une session dans le DHT (`session_<id>`), met à jour version + timestamp.

- `public boolean storeBatchInDHT(DataBatch batch)`
  - Écrit les données du batch en CSV dans `src/main/resources/stockage/node<idString>/batch_<id>.csv`.
  - Stocke dans le DHT la référence (chemin CSV) sous la clé `batch_<id>` puis met l’état à `STORED`.

- `public int broadcastSessionToAllNodes(LearningSession session)`
  - Diffuse localement la session sur tous les nœuds (cache local).
  - Retourne le nombre de nœuds servis.

- `public LearningSession retrieveSessionFromDHT(String sessionId)`
  - Récupère une session depuis le DHT avec routage Chord.
  - Vérifie aussi l’obsolescence (`stale`).

- `public LearningSession retrieveSessionLocally(String sessionId)`
  - Lit la session depuis un cache local (sans routage).

- `public DataBatch retrieveBatchFromDHT(String batchId)`
  - Récupère un batch depuis le DHT.
  - Si le DHT contient un chemin CSV, recharge le batch depuis le stockage physique.

- `private Path resolveBatchStorageRoot()`
  - Retourne le répertoire racine de stockage physique des batchs (`src/main/resources/stockage`).

- `private Path resolveNodeStorageDirectory(String nodeIdString)`
  - Retourne/crée le dépôt physique d’un nœud: `node<idString>`.

- `private void saveBatchToCsv(DataBatch batch, Path csvPath)`
  - Sérialise les données du batch dans un fichier CSV.

- `private DataBatch loadBatchFromCsv(String batchId, Path csvPath)`
  - Reconstruit un `DataBatch` depuis un CSV physique.

- `public boolean transitionToRunning(String sessionId)`
  - Applique `INIT -> RUNNING` et persist la session.

- `public boolean transitionToDone(String sessionId)`
  - Applique `RUNNING -> DONE` et persist la session.

- `public boolean deleteBatchFromDHTAndDisk(String batchId)`
  - Supprime le CSV physique d’un batch puis retire sa référence du DHT.

- `public boolean addParticipant(String sessionId, int chordId, String nodeIdString)`
  - Ajoute un participant actif puis restocke la session.

- `public boolean removeParticipant(String sessionId, int chordId)`
  - Retire un participant actif puis restocke la session.

- `private Node findNodeById(int chordId)`
  - Cherche un nœud réseau par `chordId`.

- `private Node findAnyAliveNode()`
  - Retourne le premier nœud vivant disponible.

- `public LearningSession getCurrentSession()`
  - Retourne la session en cache côté manager.

- `public void clearCache()`
  - Vide le cache local du manager.

---

## 5) `LearningControl`

### Rôle
Orchestre la logique d’apprentissage distribué:
élection IDE, création session, partition dataset, distribution batchs,
transitions d’état de session.

Le pilotage est désormais **automatique par état** (sans cycles fixes codés en dur).
Le dataset est **requis** via `control.learning.datasetPath`.

### Enum
- `BatchAssignmentStrategy`: `ROUND_ROBIN`, `RANDOM`.

### Méthodes

- `public LearningControl(String prefix)`
  - Lit la configuration PeerSim (`pid`, dataset, stratégie, limites de charge...).

- `public boolean execute()`
  - Pilote le workflow séquentiel par état (sans cycles fixes):
    - élection,
    - vérification + initialisation,
    - distribution,
    - transitions `RUNNING` puis `DONE`.

- `private void performElection()`
  - Élit le nœud IDE (plus grand `chordId`) et crée `LearningSession`.

- `private void verifyAndInitializeSession()`
  - Vérifie que l’IDE est vivant.
  - Sélectionne participants actifs.
  - Stocke la session dans le DHT.
  - Charge/synthétise dataset et découpe en batchs.

- `private List<ChordProtocol> selectActiveParticipants()`
  - Sélectionne les participants actifs (IDE prioritaire).

- `private double[][] buildReceivedDataset()`
  - Charge le dataset CSV fourni par le chercheur.
  - Retourne `null` si aucun dataset n’est fourni ou si le fichier est invalide.

- `private double[][] loadDatasetFromCsv(String path)`
  - Parse un CSV numérique (`,` ou `;`) en matrice `double[][]`.

- `private List<DataBatch> splitDataset()`
  - Découpe le dataset dynamiquement selon le nombre de nœuds ciblés.
  - En pratique: nombre de batchs piloté par `activeNodeCount` (ou participants actifs).

- `private void assignBatchToNodes()`
  - Assigne chaque batch à un nœud selon la stratégie choisie et stocke via DHT.

- `private ChordProtocol selectTargetNode(List<ChordProtocol> availableProtocols)`
  - Choisit le nœud cible (round-robin ou aléatoire) en respectant la capacité max.

- `private void incrementLoad(String nodeIdString)`
  - Incrémente la charge de distribution pour un nœud.

- `private ChordProtocol findProtocolByChordId(int chordId)`
  - Retourne le protocole Chord d’un nœud actif identifié par `chordId`.

- `private void transitionSessionToRunning()`
  - Récupère session du DHT, passe en `RUNNING`, restocke.

- `private void transitionSessionToDone()`
  - Récupère session du DHT, passe en `DONE`, restocke.
  - Après succès, supprime les batchs physiques CSV et leurs références DHT.

---

## 6) `InitControl`

### Rôle
Initialise complètement l’anneau Chord au début de la simulation.

### Méthodes

- `public InitControl(String prefix)`
  - Constructeur de contrôle (préfixe non utilisé dans l’implémentation actuelle).

- `public boolean execute()`
  - Exécute la séquence d’initialisation:
    - assigne IDs,
    - trie les nœuds,
    - calcule `m`,
    - relie successeur/prédécesseur,
    - calcule finger tables,
    - fait un test de lookup initial.

---

## 7) `DynamicControl`

### Rôle
Gère la dynamique réseau en simulation:
ajout/retrait/crash de nœuds, stabilisation globale, tests DHT/réplication.

### Méthodes

- `public DynamicControl(String prefix)`
  - Lit le PID du protocole Chord dans la configuration.

- `private int computeM(int networkSize)`
  - Calcule la taille logique `m` de l’espace d’identifiants.

- `private int getCurrentM()`
  - Lit la valeur courante de `m` depuis le réseau.

- `private void updateMForAllNodes(int newM)`
  - Applique `newM` partout et redimensionne les finger tables.

- `private void verifyM()`
  - Vérifie la cohérence de `m` sur tous les nœuds.

- `private void purgeDeadReferences()`
  - Supprime les références vers des nœuds morts (succ/pred/fingers/listes).

- `private void stabilizeAllNodes()`
  - Lance plusieurs rounds de stabilisation/fixFingers.

- `private boolean networkContains(Node n)`
  - Vérifie la présence d’un nœud dans `Network`.

- `private void crashNodeById(int targetChordId)`
  - Simule un crash ciblé par `chordId` puis répare l’anneau.

- `private void simulateCrash()`
  - Simule un crash aléatoire puis réparation globale.

- `public boolean execute()`
  - Boucle dynamique par cycle (scénarios d’événements et de tests).

- `private void addNewNode()`
  - Ajoute un nœud, exécute `join()`, puis stabilise.

- `private void removeNode()`
  - Retire volontairement un nœud, répare puis stabilise.

- `private void testLookup()`
  - Test de lookup aléatoire pour valider le routage.

- `private void printNetworkState()`
  - Affiche une vue synthétique de l’état de l’anneau.

- `private void testSimpleDHT()`
  - Test minimal de stockage/récupération DHT.

- `private void testDHTBasic()`
  - Campagne PUT/GET de base sur plusieurs nœuds.

- `private void testDHTAfterCrash()`
  - Vérifie les accès DHT après changements topologiques.

- `private void testReplication()`
  - Teste la réplication de clés/récupération.

- `private void testDataMigration()`
  - Vérifie l’accessibilité des données après migration de clés.

---

## 8) `ChordProtocol`

### Rôle
Implémentation du protocole Chord + fonctionnalités DHT + réplication +
maintenance de l’anneau (stabilization/fingers/successor list).

### Méthodes

- `public static synchronized String generateUniqueStringId()`
  - Génère un identifiant textuel permanent (`N0`, `N1`, ...).

- `public ChordProtocol()`
  - Constructeur par défaut.

- `public ChordProtocol(String prefix)`
  - Constructeur config, initialise `successorList`.

- `public boolean isAlive(Node n)`
  - Vérifie qu’un nœud existe encore dans le réseau et possède un protocole Chord.

- `public boolean networkContains(Node n)`
  - Vérifie la présence d’un nœud dans `Network`.

- `public void cleanupDeadReferences()`
  - Nettoie toutes les références mortes locales (`successor`, `predecessor`, `successorList`, `finger`).

- `private int chordIdOf(Node n)`
  - Retourne le `chordId` d’un nœud pour logs/diagnostic.

- `public void nextCycle(Node node, int protocolID)`
  - Tick périodique CDProtocol: cleanup, stabilize, fixFingers, check predecessor, réparation réplication.

- `public Object clone()`
  - Clone PeerSim du protocole en réinitialisant les pointeurs topologiques.

- `public Node findSuccessor(Node currentNode, int id)`
  - Lookup principal: trouve le successeur de `id` depuis un nœud de départ.

- `private Node findFirstAliveSuccessor(ChordProtocol c)`
  - Retourne le premier successeur vivant dans `successorList`.

- `private Node findMinAliveNode(Node startNode, ChordProtocol startChord)`
  - Fallback: retourne le nœud vivant avec plus petit `chordId`.

- `public Node findSuccessor(int id)`
  - Surcharge de lookup depuis `selfNode`.

- `public Node closestPrecedingNode(int id)`
  - Choisit le meilleur finger précédent pour accélérer le routage.

- `private boolean isBetween(int id, int start, int end)`
  - Test d’appartenance sur intervalle Chord `(start, end]` avec wrap-around.

- `private static boolean inIntervalOpen(int id, int start, int end)`
  - Test sur intervalle ouvert `(start, end)` avec wrap-around.

- `public static boolean inInterval(int id, int start, int end)`
  - Variante statique `(start, end]` (utilisée aussi hors classe).

- `public void stabilize()`
  - Algorithme Chord de stabilisation du successeur et notification.

- `public void notifyPredecessor(Node n)`
  - Mise à jour conditionnelle du prédécesseur après notification d’un voisin.

- `public void fixFingers()`
  - Met à jour une entrée de finger table à chaque appel (rotation incrémentale).

- `public void rebuildFingerTable()`
  - Recalcule toute la finger table.

- `public void checkPredecessor()`
  - Supprime le prédécesseur s’il est mort.

- `public void join(Node n0)`
  - Fait rejoindre un nœud à l’anneau via un nœud existant.
  - Met à jour successeurs et déclenche migration des clés nécessaires.

- `private void migrateDataFromSuccessor(ChordProtocol successorChord)`
  - Récupère du successeur les clés devenues de la responsabilité du nouveau nœud.

- `private boolean isKeyMine(int keyId)`
  - Détermine si `keyId` appartient à ce nœud dans l’anneau courant.

- `private int getLastNodeId()`
  - Renvoie le `chordId` maximal du réseau.

- `public void leave()`
  - Quitte proprement l’anneau (migration sortante + recâblage voisins).

- `private void migrateDataToSuccessor(ChordProtocol successorChord)`
  - Transfère toutes les clés locales vers le successeur au départ du nœud.

- `public void traitementparnode()`
  - Méthode utilitaire de test/log par nœud.

- `private String hashKey(String key)`
  - Hash (MD5 puis modulo `2^m`) pour mapper une clé dans l’espace Chord.

- `public void putLocal(String key, Object value)`
  - Écriture locale dans le stockage du nœud.

- `public Object getLocal(String key)`
  - Lecture locale.

- `public Object removeLocal(String key)`
  - Suppression locale.

- `public boolean containsLocal(String key)`
  - Test d’existence locale.

- `public void put(String key, Object value)`
  - `PUT` distribué vers le nœud responsable.

- `public Object get(String key)`
  - `GET` distribué depuis le nœud responsable.

- `public Object remove(String key)`
  - `REMOVE` distribué sur le nœud responsable.

- `public boolean contains(String key)`
  - `CONTAINS` distribué sur le nœud responsable.

- `public void printLocalStorage()`
  - Affiche le stockage local (debug).

- `public void putReplicated(String key, Object value)`
  - Écrit la clé sur le primaire + successeurs (facteur de réplication).

- `public Object getReplicated(String key)`
  - Lit la clé depuis la première réplique disponible.

- `private List<Node> getReplicaNodes(Node primaryNode, int k)`
  - Construit la liste des nœuds de réplication (primaire + `k` successeurs).

- `private void registerReplica(String logicalKey, Node replicaNode)`
  - Enregistre qu’un nœud détient une copie d’une clé.

- `public void checkAndRepairReplication()`
  - Vérifie les répliques attendues et restaure les copies manquantes.

- `private void updateSuccessorList()`
  - Met à jour la liste des successeurs de secours.

- `public static void updateAllNodes()`
  - Recalcule globalement les liens anneau + fingers après changement topologique.

- `public static void printNetworkState()`
  - Affiche la topologie globale (succ/pred).

- `public static void printFingerTables()`
  - Affiche les finger tables globales.

---

## 9) `AbstractLocalModel`

### Rôle
Base commune pour les modèles locaux (préparation entrée/sortie + utilitaires numériques).

### Méthodes
- `protected AbstractLocalModel(double learningRate)` : initialise le taux d’apprentissage.
- `protected void ensureInputDim(List<double[]> batch)` : valide/inférer la dimension d’entrée.
- `protected void rememberBatch(List<double[]> batch)` : conserve le dernier batch reçu.
- `protected double[] extractFeatures(double[] row)` : extrait les features d’une ligne.
- `protected double extractTarget(double[] row)` : extrait la cible d’une ligne.
- `protected double clip(double value, double min, double max)` : borne une valeur.
- `protected double sigmoid(double value)` : applique la sigmoïde.

---

## 10) `AccuracyTracker`

### Rôle
Calcule et journalise des métriques locales/globales (accuracy, loss) par epoch.

### Méthodes
- `public void evaluateLocal(...)` : calcule les métriques du modèle local.
- `public void trackLocalAccuracy(...)` : enregistre l’accuracy locale d’un nœud.
- `public void evaluateGlobal(...)` : évalue le modèle global.
- `public void printEpochSummary(...)` : imprime un résumé de fin d’epoch.
- `private Metrics computeMetrics(...)` : routine de calcul interne.

---

## 11) `ActiveSession`

### Rôle
Objet runtime représentant une session active planifiée par `SessionQueueManager`.

### Méthodes
- Getters d’accès : `getRequest`, `getSessionId`, `getIdeNodeId`, `getIdeChordId`, `getIdeNodeIdString`, `getLearnerNodeIds`, `getLearnerChordIds`, `getLearningSession`.
- `isCompleted()` : indique si la session est terminée.
- `markCompleted()` : marque la session comme terminée.

---

## 12) `CnnModel`

### Rôle
Implémentation `FederatedLocalModel` orientée CNN via DJL.

### Méthodes
- Cycle principal : `trainBatch`, `evaluate`, `predict`, `getWeights`, `setWeights`, `close`.
- Initialisation/infra : `initializeModel`, `ensureTrainer`, `getModelParameters`, `closeSilently`.
- Prétraitement interne : `toCnnInput`, `normalizeInput`, `trainInternal`.

---

## 13) `ConvergenceVoter`

### Rôle
Produit et publie un vote de convergence à partir de 2 états globaux successifs.

### Méthodes
- `computeVote(...)` : calcule le vote (`CONVERGE/CONTINUE/DIVERGE`).
- `publishVote(...)` : publie le vote dans le DHT.

---

## 14) `DatasetPreprocessor`

### Rôle
Prétraitement tabulaire : nettoyage, encodage catégoriel, normalisation min-max.

### Méthodes
- Entrée principale : `preprocess(List<String[]> rawRows, boolean enabled)`.
- Parsing/alignement : `parseNumericStrict`, `maxColumns`, `alignRows`, `sanitize`.
- Détection : `looksLikeHeader`, `isCategoricalColumn`, `safeToken`, `isNumeric`.
- Conversion/scale : `parseNumber`, `normalizeMinMax`.

---

## 15) `DepotAggregator`

### Rôle
Agrège les dépôts de gradients d’un paramètre donné pour un epoch.

### Méthodes
- `checkAndAggregate(int epoch)` : déclenche l’agrégation si quorum atteint.

---

## 16) `DjlParameterCodec`

### Rôle
Codec NDArray DJL ↔ tableau `float[]` sérialisable.

### Méthodes
- `toFloatArray(NDArray array)` : extrait les valeurs.
- `fromFloatArray(...)` : reconstruit un NDArray (avec ou sans `Shape`).

---

## 17) `FederatedDhtKeys`

### Rôle
Fabrique centralisée des clés DHT pour la logique FL.

### Méthodes
- `gradientKey(...)`, `globalKey(...)`, `voteKey(...)`, `decisionKey(...)`.

---

## 18) `FederatedLocalModel`

### Rôle
Interface commune des modèles fédérés locaux.

### Méthodes
- `trainBatch`, `evaluate`, `predict`, `getWeights`, `setWeights`, `close`.

---

## 19) `GlobalModelCollector`

### Rôle
Collecte le modèle global agrégé depuis les clés DHT d’un epoch.

### Méthodes
- `collectGlobalModel(int epoch, int numParams, float[] fallbackModel)`.

---

## 20) `GradientPublisher`

### Rôle
Publie les deltas de gradients locaux dans le DHT (sparse top-k possible).

### Méthodes
- `publishGradients(...)` : publie les gradients d’un nœud.
- `selectTopKIndices(...)`, `indexOfMaxAbs(...)` : sélection des composantes à publier.
- `publishSingleDelta(...)` : publication unitaire d’un paramètre.
- `estimateHopCount(...)` : estimation coût réseau / routage.

---

## 21) `LocalModelManager`

### Rôle
Pilote le cycle d’un modèle local (init/train/eval/predict/poids).

### Méthodes
- Initialisation : `initializeModel`.
- Cycle ML : `trainLocalModel`, `evaluateLocal`, `predict`.
- Poids : `getModelWeights`, `setModelWeights`.
- Utilitaires : `generateSimpleLabels`, `readObject`, `close`, `toString`.

---

## 22) `NeuralNetworkModel`

### Rôle
Implémentation MLP/NN de `FederatedLocalModel` via DJL.

### Méthodes
- Constructeurs : variantes `learningRate`, `inputDim`, architecture complète.
- API modèle : `trainBatch`, `evaluate`, `predict`, `train`, `getWeights`, `setWeights`, `close`.
- Paramètres : `getParameters`, `setParameters`, `computeLoss`, `getModelType`, `toString`.
- Internes : `initializeModel`, `trainInternal`, `ensureTrainer`, `normalizeInput`, `clampTarget`, `getModelParameters`, `closeSilently`.

---

## 23) `NodeState`

### Rôle
État de disponibilité d’un nœud (occupé comme learner et/ou IDE).

### Méthodes
- Lecture état : `isBusyAsLearner`, `isBusyAsIDE`.
- Marquage : `markAsLearner`, `markAsIDE`.
- Libération : `releaseLearner`, `releaseIDE`.

---

## 24) `NodeStateManager`

### Rôle
Singleton de gestion globale des disponibilités des nœuds.

### Méthodes
- Cycle de vie : `getInstance`, `init`, `isInitialized`.
- Sélection : `getAvailableLearners`, `getAvailableIDEs`.
- Verrous métier : `markAsLearner`, `markAsIDE`, `releaseLearner`, `releaseIDE`.

---

## 25) `ParamDepot`

### Rôle
Accumule les contributions d’un paramètre pour un epoch puis agrège (FedAvg).

### Méthodes
- Ingestion/état : `addContribution`, `isComplete`, `missingContributors`, `debugState`.
- Agrégation : `aggregate`, `isAggregated`, `getAggregatedValue`.
- Getters : `getParamIndex`, `getEpoch`, `getExpectedContributors`, `getContributions`.

---

## 26) `ParamEntry`

### Rôle
Contribution unitaire d’un nœud (delta de gradient + checksum d’intégrité).

### Méthodes
- Getters : `getNodeId`, `getParamIndex`, `getEpoch`, `getGradientDelta`, `getDatasetSize`, `getTimestamp`, `getChecksum`.
- Intégrité/sérialisation : `computeChecksum`, `toHex`, `writeObject`, `readObject`.
- `toString()` : affichage diagnostic.

---

## 27) `SessionQueueManager`

### Rôle
Orchestre la file d’attente et l’allocation des sessions concurrentes.

### Méthodes
- API publique : `getInstance`, `tryStartSession`, `onSessionComplete`, `getActiveSessions`, `getWaitingQueueSnapshot`.
- Planification interne : `tryStartSessionInternal`, `drainWaitingQueue`, `containsRequest`, `chooseLearners`, `joinNodes`, `chordProtocolForNodeIndex`, `logWaiting`.

---

## 28) `SessionRequest`

### Rôle
Objet de demande de session (ID, nombre de learners, dataset).

### Méthodes
- Constructeur `SessionRequest(...)` ; la classe expose aussi ses accesseurs métier.

---

## 29) `VoteCollector`

### Rôle
Collecte les votes de convergence et produit une décision de quorum.

### Méthodes
- `collectAndDecide(int epoch, List<String> allNodeIds)`.

---

## 30) Classes de test (`src/test/java/com/example/peersimdjl`)

### `DatasetPreprocessorTest`
- `shouldSkipHeaderEncodeCategoricalAndNormalize()` : valide le pipeline complet de prétraitement.
- `shouldKeepNumericParsingWhenPreprocessDisabled()` : valide le mode sans prétraitement.

### `DjlParameterCodecTest`
- `shouldConvertArrayRoundTrip()` : valide la conversion aller/retour NDArray ↔ float[].

### `FederatedLogicTest`
- `convergenceVoterShouldReturnConvergeContinueDiverge()` : règles de vote.
- `voteCollectorDecisionRulesShouldMatchSpec()` : règles de décision quorum.
- `globalModelCollectorShouldReturnNullWhenIncomplete()` : collecte incomplète.
- `globalModelCollectorShouldReturnArrayWhenComplete()` : collecte complète.
- `paramEntryChecksumShouldBeStableForSameInputs()` : stabilité du checksum.

### `FederatedScenarioIntegrationTest`
- `scenarioShouldPublishAggregateAndDecideConsistently(int nodeCount)` : scénario FL intégré bout-en-bout.

### `ParamDepotTest`
- `aggregateShouldUseWeightedFedAvg()` : agrégation pondérée.
- `addContributionShouldBeIdempotentPerNodeId()` : idempotence par nœud.
- `completeAndMissingContributorsShouldBeConsistent()` : cohérence complétude/manquants.
- `aggregateShouldFallbackToSimpleAverageWhenWeightsAreZero()` : fallback moyenne simple.

---

## Remarque
Cette documentation décrit les signatures et le comportement observé dans le code actuel.
Si tu veux, je peux aussi te générer une **version UML (PlantUML)** à partir de ce même inventaire.
