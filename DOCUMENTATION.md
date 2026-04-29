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
La démo peut aussi lancer plusieurs apprentissages successifs pour visualiser le comportement.

### Méthodes

- `public static void main(String[] args)`
  - Démarre la simulation PeerSim.
  - Lit le dataset et le nombre de nœuds si aucun argument CLI n’est fourni.
  - Désactive le debug Chord pour n’afficher que les logs d’apprentissage.
  - Accepte plusieurs chemins CSV séparés par des virgules pour lancer plusieurs sessions.
  - Génère un fichier de configuration temporaire puis appelle `Simulator.main(new String[]{configPath.toString()})`.

### Configuration de démo
- `control.learning.sessionCount = N` pour enchaîner plusieurs apprentissages.
- `control.learning.datasetPaths` permet de fournir plusieurs datasets.
- `control.learning.sessionRequirements` définit le nombre de learners requis par session.
- `control.learning.modelType` choisit le type de modèle (`MLP` ou `CNN`).

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
  - Prépare une session par dataset configuré.

- `private List<ChordProtocol> selectActiveParticipants()`
  - Sélectionne les participants actifs (IDE prioritaire).

- `private double[][] buildReceivedDataset()`
  - Charge le dataset CSV fourni par le chercheur.
  - Retourne `null` si aucun dataset n’est fourni ou si le fichier est invalide.
  - Utilise le prétraitement commun `DatasetPreprocessor` sans changer le pipeline.

- `private double[][] loadDatasetFromCsv(String path)`
  - Parse un CSV numérique (`,` ou `;`) en matrice `double[][]`.

- `private List<DataBatch> splitDataset()`
  - Découpe le dataset en batches **stratifiés par label**.
  - Répartit les exemples de chaque classe de manière équilibrée dans tous les batches.
  - Évite qu’un batch contienne presque une seule classe.

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

### Modifications récentes
- Gestion de plusieurs datasets dans une seule exécution.
- Sessions multiples lancées séquentiellement selon `sessionRequirements`.
- Batches équilibrés par label au lieu d’une découpe séquentielle.
- Prétraitement partagé conservé pour tous les datasets.

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
Les valeurs affichées proviennent maintenant de l’accuracy réelle du modèle.

### Méthodes
- `public void evaluateLocal(float[] localParams, int datasetSize, int epoch, String nodeId)` : conserve l’ancienne signature, sans recalcul artificiel.
- `public void trackLocalAccuracy(String nodeId, float accuracy, int epoch)` : enregistre la **vraie accuracy** d’un nœud.
- `public void evaluateGlobal(float[] globalParams, int totalDatasetSize, int epoch)` : calcule la métrique globale à partir des accuracies locales réelles.
- `public void printEpochSummary(String sessionName, String datasetPath, int epoch)` : imprime un résumé de fin d’epoch.

### Modifications récentes
- Suppression de la métrique artificielle basée sur les poids.
- Moyenne globale calculée à partir des vraies accuracies locales.

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
- `public Vote computeVote(float[] globalEpoch, float[] globalPrevEpoch)` : calcule le vote (`CONVERGE/CONTINUE/DIVERGE`).
- `public void publishVote(Vote vote, int epoch, String nodeId)` : publie le vote dans le DHT.

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
- `public static float[] toFloatArray(NDArray array)` : extrait les valeurs.
- `public static NDArray fromFloatArray(NDManager manager, float[] values)` : reconstruit un NDArray depuis un tableau de floats.
- `public static NDArray fromFloatArray(NDManager manager, float[] values, Shape shape)` : reconstruit un NDArray en appliquant une `Shape` si fournie.

---

## 17) `FederatedDhtKeys`

### Rôle
Fabrique centralisée des clés DHT pour la logique FL.

### Méthodes
- `public static String gradientKey(int epoch, int paramIndex)`, `public static String globalKey(int epoch, int paramIndex)`,
  `public static String voteKey(int epoch, String nodeId)`, `public static String decisionKey(int epoch)`.

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
- `public void publishGradients(float[] prevWeights, float[] newWeights, int epoch, String nodeId, int datasetSize)` : publie les gradients d’un nœud.
- `private int[] selectTopKIndices(float[] deltas)`, `private int indexOfMaxAbs(float[] deltas)` : sélection des composantes à publier.
- `private void publishSingleDelta(int epoch, String nodeId, int datasetSize, int paramIndex, float delta)` : publication unitaire d’un paramètre.
- `private int estimateHopCount(ChordProtocol sourceChord, Node target)` : estimation coût réseau / routage.

---

## 21) `LocalModelManager`

### Rôle
Pilote le cycle d’un modèle local (init/train/eval/predict/poids).
Le modèle utilise la dernière colonne du CSV comme label et les autres colonnes comme features.

### Méthodes
- Initialisation : `initializeModel`.
- Cycle ML : `trainLocalModel`, `evaluateLocal`, `predict`.
- Poids : `getModelWeights`, `setModelWeights`.
- Utilitaires : `readObject`, `close`, `toString`.

### Modifications récentes
- Split automatique `train/validation` avant l’entraînement.
- Utilisation des vraies labels du dataset au lieu de labels synthétiques.
- Évaluation locale calculée sur les vraies features/labels.

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
- Constructeur `SessionRequest(int sessionId, int requiredLearners, String csvDataset)` ; la classe expose aussi ses accesseurs métier.

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

  ---
  1. Package com.example.peersimdjl.api

  Nom: com.example.peersimdjl.api                                                                                       
  Description: Sous-package regroupant les classes liées à l'API de simulation (états, requêtes).
  Détails techniques: Contient SimulationState (déjà partiellement documenté en分离) et SimulationRequest (non
  documenté).

  ---
  2. Classe non documentée: SimulationRequest

  Nom: SimulationRequest
  Description: Objet de requête pour démarrer une simulation via l'API, encapsulant les paramètres d'entrée.
  Détails techniques:
  - Champs: int sessionId, int requiredLearners, String csvDataset, String modelType (défaut "MLP"), int sessionCount   
  (défaut 1)
  - Constructeur: SimulationRequest(int sessionId, int requiredLearners, String csvDataset)
  - Accesseurs: getSessionId(), getRequiredLearners(), getCsvDataset(), getModelType(), setModelType(String),
  getSessionCount(), setSessionCount(int)

  ---
  3. Méthodes manquantes: App.resolveConfigPath()

  Nom: App.resolveConfigPath()
  Description: Résout le chemin vers le fichier peersim.cfg de configuration PeerSim.
  Détails techniques:
  - Fallback: si introuvable via ClassLoader, utilise src/main/resources/peersim.cfg
  - Exceptions: lance URISyntaxException si l'URI du classpath est invalide

  ---
  4. Méthodes manquantes: DataBatch getters/setters

  Nom: DataBatch getters/setters
  Description: Accesseurs pour les champs internes du batch de données.
  Détails techniques:
  - public String getBatchId(): retourne l'identifiant du batch
  - public double[][] getData(): retourne les données (peut être null si marqué complet)
  - public BatchStatus getStatus(): retourne l'état courant du batch
  - public String getAssignedNodeIdString(): retourne l'ID string du nœud assigné (peut être null)

  ---
  5. Méthodes manquantes: LearningSession getters + equals/hashCode

  Nom: LearningSession accesseurs et méthodes de comparaison
  Description: Accès aux métadonnées de session et comparaison d'objets.
  Détails techniques:
  - Accesseurs: getSessionId(), getIdeNodeId(), getIdeNodeIdString(), getTotalParticipants(), getCreatedAtTimestamp(),  
  getCreatedAtCycle(), getActiveNodes(), getState()
  - equals(Object o): compare sur sessionId (identifiant unique)
  - hashCode(): basé sur sessionId
  - public int getVersion(): retourne le numéro de version (incrémenté à chaque modification)

  ---
  6. Méthodes manquantes: DHTSessionManager getters/setters + constructeur

  Nom: DHTSessionManager détails constructeur et accesseurs
  Description: Initialisation et accès aux champs internes du gestionnaire DHT.
  Détails techniques:
  - Constructeur: DHTSessionManager(int protocolId) initialise this.protocolId = protocolId, this.cache = new
  HashMap<>()
  - Accesseurs: getProtocolId(), getCache() (retourne le cache local des sessions)
  - Pas de setters publics (immutable après construction)

  ---
  7. Méthodes manquantes: LearningControl config getters/setters

  Nom: LearningControl config getters/setters
  Description: Accès aux paramètres de configuration PeerSim lus au démarrage.
  Détails techniques:
  - Getters: getDatasetPaths() (liste des CSV), getBatchAssignmentStrategy(), getMaxLoadPerNode(),
  getSessionRequirements(), getModelType()
  - Pas de setters publics (config lue une seule fois dans le constructeur via préfixe PeerSim)

  ---
  8. Méthodes manquantes: InitControl.execute() détail et retour

  Nom: InitControl.execute()
  Description: Exécute l'initialisation complète de l'anneau Chord au cycle 0.
  Détails techniques:
  - Étapes: 1. Assigne chordId séquentiel à chaque nœud → 2. Trie les nœuds par chordId → 3. Calcule m =
  ceil(log2(networkSize)) → 4. Relie chaque nœud à son successeur/predecesseur → 5. Construit les finger tables → 6.    
  Teste un lookup aléatoire
  - Retour: true si l'initialisation réussit, false si le réseau est vide

  ---
  9. Méthodes manquantes: DynamicControl scénarios et config prefix

  Nom: DynamicControl scénarios internes et configuration
  Description: Scénarios de dynamique réseau configurables et paramètres prefix.
  Détails techniques:
  - Config prefix: dynamic (lit dynamic.addNodeRate, dynamic.crashRate, dynamic.testLookupInterval)
  - Scénarios internes: 1. Ajout périodique de nœuds → 2. Crash aléatoire de nœuds → 3. Stabilisation globale après     
  chaque événement → 4. Tests DHT/réplication tous les N cycles
  - execute() retourne toujours false (contrôle permanent)

  ---
  10. Méthodes manquantes: ChordProtocol getters/setters

  Nom: ChordProtocol getters/setters
  Description: Accès et modification des champs de topologie Chord.
  Détails techniques:
  - Getters: getChordId(), getSuccessor(), getPredecessor(), getSuccessorList(), getFingerTable(), getM(),
  getLocalStorage()
  - Setters (package-private): setSuccessor(Node), setPredecessor(Node), setM(int)
  - public int getLocalStorageSize(): retourne le nombre de clés stockées localement

  ---
  11. Méthodes manquantes: AbstractLocalModel méthodes non citées

  Nom: AbstractLocalModel méthodes complètes
  Description: Méthodes abstraites et concrètes de la classe de base des modèles locaux.
  Détails techniques:
  - Méthodes abstraites: public abstract void trainBatch(List<double[]> batch), public abstract double
  evaluate(List<double[]> testSet), public abstract double predict(double[] features), public abstract float[]
  getWeights(), public abstract void setWeights(float[] weights), public abstract void close()
  - Méthode concrète non citée: protected int getInputDim() (retourne la dimension d'entrée inférée)

  ---
  12. Méthodes manquantes: AccuracyTracker état interne + getters + reset

  Nom: AccuracyTracker état et accesseurs
  Description: État interne de suivi des métriques et méthodes de gestion.
  Détails techniques:
  - État interne: Map<String, List<Double>> nodeAccuracies (accuracies par nœud/epoch), Map<Integer, Double>
  globalAccuracies (accuracy globale par epoch)
  - Getters: getLocalAccuracy(String nodeId, int epoch), getGlobalAccuracy(int epoch), getAllNodeAccuracies()
  - public void reset(): vide toutes les collections de métriques

  ---
  13. Méthodes manquantes: ActiveSession constructeur + setters

  Nom: ActiveSession constructeur et setters
  Description: Initialisation et modification de l'état d'une session active.
  Détails techniques:
  - Constructeur: ActiveSession(SessionRequest request, LearningSession session, String ideNodeId, List<String>
  learnerNodeIds)
  - Setters: setLearningSession(LearningSession session) (met à jour la session après modification DHT)
  - public void addLearner(String nodeId): ajoute un learner en cours de route (rare)

  ---
  14. Méthodes manquantes: CnnModel/NeuralNetworkModel hyperparamètres + constructeurs

  Nom: CnnModel hyperparamètres et constructeurs
  Description: Paramètres de configuration du modèle CNN.
  Détails techniques:
  - Hyperparamètres: learningRate, inputHeight, inputWidth, numChannels, numClasses (inféré depuis le dataset)
  - Constructeurs: CnnModel(double learningRate, int inputDim), CnnModel(double learningRate, int height, int width, int   channels)

  Nom: NeuralNetworkModel hyperparamètres et constructeurs
  Description: Paramètres de configuration du modèle MLP.
  Détails techniques:
  - Hyperparamètres: learningRate, inputDim, hiddenLayerSizes (liste d'entiers, défaut [64, 32]), activation (défaut    
  "relu")
  - Constructeurs: NeuralNetworkModel(double learningRate, int inputDim), NeuralNetworkModel(double learningRate, int   
  inputDim, int[] hiddenLayers)

  ---
  15. Méthodes manquantes: ConvergenceVoter types Vote + seuils

  Nom: ConvergenceVoter types de vote et seuils
  Description: Règles de décision de convergence.
  Détails techniques:
  - Types Vote: CONVERGE (delta < 0.001), CONTINUE (delta < 0.01), DIVERGE (delta >= 0.01)
  - Seuils: DELTA_CONVERGE = 0.001f, DELTA_CONTINUE = 0.01f (constantes statiques)

  ---
  16. Méthodes manquantes: DatasetPreprocessor state + constructeur + constantes

  Nom: DatasetPreprocessor état et configuration
  Description: Configuration du prétraitement des données tabulaires.
  Détails techniques:
  - Stateless (pas d'état interne mutable)
  - Constructeur: DatasetPreprocessor() (vide, méthodes statiques ou d'instance sans état)
  - Constantes: MAX_CATEGORICAL_CARDINALITY = 10 (seuil pour considérer une colonne comme catégorielle), NORMALIZE_MIN =   0.0, NORMALIZE_MAX = 1.0

  ---
  17. Méthodes manquantes: DepotAggregator cycle de vie + quorum + dépôts

  Nom: DepotAggregator fonctionnement complet
  Description: Gestion de l'agrégation des gradients pour un paramètre/epoch.
  Détails techniques:
  - Quorum: attend expectedContributors (nombre de learners) avant d'agréger
  - Dépôts: Map<String, ParamEntry> entries (clé = nodeId)
  - Cycle: 1. addContribution(ParamEntry) → 2. isComplete() vérifie quorum → 3. checkAndAggregate() déclenche FedAvg    
  → 4. getAggregatedValue() retourne le résultat

  ---
  18. Méthodes manquantes: DjlParameterCodec limites + exceptions

  Nom: DjlParameterCodec limites et gestion d'erreurs
  Description: Contraintes de conversion NDArray ↔ float[].
  Détails techniques:
  - Limites: taille maximale de float[] = Integer.MAX_VALUE (contrainte DJL)
  - Exceptions: IllegalArgumentException si le tableau de floats est vide ou si la Shape est invalide
  - Supporte uniquement les NDArray de type DataType.FLOAT32

  ---
  19. Méthodes manquantes: FederatedDhtKeys constantes utilitaires

  Nom: FederatedDhtKeys constantes
  Description: Clés DHT standardisées pour le federated learning.
  Détails techniques:
  - Constantes: GRADIENT_PREFIX = "gradient_", GLOBAL_PREFIX = "global_", VOTE_PREFIX = "vote_", DECISION_PREFIX =      
  "decision_"
  - Format des clés: <prefix><epoch>_<paramIndex> (ou <prefix><epoch>_<nodeId> pour votes)

  ---
  20. Méthodes manquantes: FederatedLocalModel contrat complet

  Nom: FederatedLocalModel contrat complet
  Description: Interface complète des modèles fédérés.
  Détails techniques:
  - Méthodes ajoutées: public boolean isClosed() (indique si le modèle a été fermé), public String getModelType()       
  (retourne "MLP" ou "CNN"), public int getInputDim()
  - close() est idempotent (appel multiple sans erreur)

  ---
  21. Méthodes manquantes: GlobalModelCollector fallback + timeout + gestion incomplète

  Nom: GlobalModelCollector gestion des cas d'erreur
  Description: Collecte du modèle global avec gestion des cas limites.
  Détails techniques:
  - Fallback: utilise fallbackModel si moins de 50% des paramètres sont disponibles dans le DHT
  - Timeout: attend 30 secondes max pour que tous les paramètres soient publiés
  - Gestion incomplète: retourne null si le quorum n'est pas atteint après timeout

  ---
  22. Méthodes manquantes: GradientPublisher top-k + seuils + compression

  Nom: GradientPublisher configuration de publication
  Description: Paramètres de publication des gradients locaux.
  Détails techniques:
  - Top-k: K_DEFAULT = 10 (nombre de composantes de gradient publiées si sparse mode activé)
  - Seuils: DELTA_MIN = 1e-8f (composantes inférieures sont ignorées)
  - Pas de compression (stockage brut des deltas en DHT)

  ---
  23. Méthodes manquantes: LocalModelManager cycle détail + split ratio

  Nom: LocalModelManager détails d'entraînement
  Description: Paramètres de division des données et cycle d'entraînement.
  Détails techniques:
  - Split ratio: 80% train / 20% validation (fixe)
  - Cycle: 1. initializeModel() → 2. trainLocalModel(batch) (itérations sur le batch) → 3. evaluateLocal() sur
  validation → 4. getModelWeights() pour publication

  ---
  24. Méthodes manquantes: NodeState/NodeStateManager transitions + thread-safety

  Nom: NodeState transitions autorisées
  Description: Règles de changement d'état des nœuds.
  Détails techniques:
  - Transitions: AVAILABLE → LEARNER, AVAILABLE → IDE, LEARNER → AVAILABLE, IDE → AVAILABLE
  - Interdiction: passer de LEARNER à IDE directement sans libération

  Nom: NodeStateManager thread-safety
  Description: Sécurité des opérations concurrentes.
  Détails techniques:
  - Thread-safe: utilise synchronized sur toutes les méthodes de modification d'état
  - getAvailableLearners(int count) retourne les premiers nœuds disponibles et les marque automatiquement

  ---
  25. Méthodes manquantes: ParamDepot/ParamEntry sérialisation + limites

  Nom: ParamDepot sérialisation
  Description: Persistance des dépôts de paramètres.
  Détails techniques:
  - Sérialisation: implémente Serializable, stocke epoch, paramIndex, expectedContributors, entries
  - Limites: nombre maximum de contributeurs = 1000 (configurable)

  Nom: ParamEntry sérialisation
  Description: Persistance des contributions unitaires.
  Détails techniques:
  - Sérialisation: implémente Serializable, stocke nodeId, paramIndex, epoch, gradientDelta, datasetSize, timestamp,    
  checksum
  - computeChecksum(): utilise MD5 sur les données du gradient + timestamp

  ---
  26. Méthodes manquantes: SessionQueueManager file + timeout + priorités

  Nom: SessionQueueManager gestion de la file d'attente
  Description: Fonctionnement de l'ordonnancement des sessions.
  Détails techniques:
  - File: Queue<SessionRequest> waitingQueue (FIFO par défaut)
  - Timeout: 5 cycles max pour une session en attente avant abandon
  - Pas de priorités (ordre d'arrivée strict)

  ---
  27. Méthodes manquantes: VoteCollector règles quorum explicites

  Nom: VoteCollector règles de décision
  Description: Logique de quorum pour les votes de convergence.
  Détails techniques:
  - Quorum: 50% + 1 des nœuds doivent avoir voté pour que la décision soit valide
  - Règle: si ≥ 50% de CONVERGE → décision CONVERGE; sinon si ≥ 50% de DIVERGE → DIVERGE; sinon CONTINUE
  - collectAndDecide() retourne null si le quorum n'est pas atteint

  ---
  28. Logique métier: Workflow apprentissage distribué complet

  Nom: Workflow apprentissage distribué
  Description: Séquence complète d'une session d'apprentissage fédéré.
  Détails techniques:
  1. Élection IDE (plus grand chordId) → 2. Création LearningSession → 3. Sélection learners actifs → 4.
  Chargement/split stratifié du dataset → 5. Assignation batches aux learners → 6. Entraînement local (epochs) → 7.     
  Publication gradients → 8. Agrégation globale → 9. Votes convergence → 10. Transition RUNNING → DONE → 11. Nettoyage  
  batches

  ---
  29. Logique métier: Échanges entre contrôleurs

  Nom: Échanges entre contrôleurs PeerSim
  Description: Flux de messages entre les composants de simulation.
  Détails techniques:
  - InitControl → initialise l'anneau Chord avant tout
  - LearningControl → utilise DHTSessionManager pour stocker sessions/batches dans le DHT
  - DynamicControl → modifie la topologie, déclenche ChordProtocol.stabilize() après chaque changement
  - Aucun échange direct entre contrôleurs : communication via DHT et état des nœuds

  ---
  30. Logique métier: Succès/échec et propagation d'erreurs

  Nom: Gestion d'erreurs
  Description: Conditions de succès et propagation des erreurs.
  Détails techniques:
  - Succès: session atteint DONE, tous les batches traités, modèle global agrégé
  - Échecs: IDE crash → session annulée, learners insuffisants → session abandonnée, dataset invalide → erreur fatale   
  - Propagation: les erreurs sont loguées via System.err, pas d'exception propagée (PeerSim continue l'exécution)       

  ---
  31. Logique métier: Modèle de concurrence

  Nom: Concurrence et synchronisation
  Description: Primitives de synchronisation utilisées.
  Détails techniques:
  - synchronized sur les méthodes modifiant LearningSession, DataBatch, NodeStateManager
  - Pas de locks explicites (ReentrantLock) : synchronisation par moniteur Java
  - ChordProtocol.nextCycle() est exécuté séquentiellement par PeerSim (pas de concurrence réelle)

  ---
  32. Logique métier: Stratégies de répartition

  Nom: Stratégies d'assignation des batches
  Description: Logique de répartition round-robin/random.
  Détails techniques:
  - ROUND_ROBIN: assigne le batch i au learner i % nombre de learners
  - RANDOM: choisit un learner aléatoire respectant maxLoadPerNode (défaut 5 batches/nœud)
  - Fallback: si aucun learner disponible, attend le cycle suivant

  ---
  33. Logique métier: Cycle de vie sessions multiples

  Nom: Gestion de plusieurs sessions
  Description: Ordonnancement des sessions concurrentes.
  Détails techniques:
  - SessionQueueManager limite à 1 session active à la fois (sessions séquentielles)
  - Pas de préemption: une session en cours ne peut être interrompue
  - Sessions en attente: conservées dans la file FIFO jusqu'à disponibilité des learners

  ---
  34. API/Configuration: Paramètres peersim.cfg

  Nom: Paramètres PeerSim documentés
  Description: Clés de configuration pour le module d'apprentissage.
  Détails techniques:
  - control.learning.pid: ID du protocole Chord (défaut 0)
  - control.learning.datasetPaths: chemins CSV séparés par virgules
  - control.learning.modelType: "MLP" ou "CNN" (défaut "MLP")
  - control.learning.sessionCount: nombre de sessions consécutives (défaut 1)
  - control.learning.sessionRequirements: nombre de learners par session (défaut 3)
  - control.learning.batchAssignmentStrategy: "ROUND_ROBIN" ou "RANDOM" (défaut "ROUND_ROBIN")
  - control.learning.maxLoadPerNode: nombre max de batches par nœud (défaut 5)

  ---
  35. API/Configuration: Format CSV attendu

  Nom: Format des datasets CSV
  Description: Structure attendue des fichiers d'entrée.
  Détails techniques:
  - Dernière colonne: label (cible, numérique ou catégorielle)
  - Autres colonnes: features (numériques, séparateur , ou ;)
  - Première ligne: peut contenir des en-têtes (détectés automatiquement par DatasetPreprocessor)
  - Exemple: feature1,feature2,label ou 1.2,3.4,0

  ---
  36. API/Configuration: Point d'entrée CLI App

  Nom: Arguments CLI de App
  Description: Options de lancement de la simulation.
  Détails techniques:
  - Aucun argument obligatoire: mode démo interactif si vide
  - Arguments optionnels: peersim.cfg (chemin de config), --dataset <path> (CSV), --nodes <N> (nombre de nœuds)
  - Exemple: java -jar peersim-djl.jar --dataset adult.csv --nodes 5

  ---
  37. Tests: FederatedScenarioIntegrationTest étapes

  Nom: Test d'intégration FL
  Description: Étapes du scénario de bout-en-bout.
  Détails techniques:
  1. Initialise l'anneau Chord avec N nœuds → 2. Crée une session avec 2 learners → 3. Charge un dataset synthétique    
  → 4. Exécute 3 epochs d'apprentissage → 5. Vérifie la publication des gradients → 6. Vérifie l'agrégation globale → 7.   Vérifie les votes de convergence
  - Prérequis: DJL installé, pas de dépendances externes autres que le projet

  ---
  38. Tests: Reproductibilité locale

  Nom: Reproduire les tests localement
  Description: Commandes pour exécuter les tests.
  Détails techniques:
  - Build: mvn clean install (génère le jar avec dépendances)
  - Tests unitaires: mvn test
  - Test d'intégration: mvn test -Dtest=FederatedScenarioIntegrationTest
  - Datasets de test: src/main/resources/sample_dataset.csv (inclus)

  ---
  39. Déploiement: Build et dépendances

  Nom: Build et dépendances externes
  Description: Outils de build et bibliothèques requises.
  Détails techniques:
  - Build: Maven (pom.xml présent)
  - Dépendances principales: peersim-1.0.5.jar, djl-api + djl-pytorch (DJL 0.24.0), jep-2.3.0.jar (Python bridge)       
  - Java: version 17 minimum

  ---
  40. Déploiement: Commandes de lancement et logs

  Nom: Lancement de la simulation
  Description: Commandes et sortie attendue.
  Détails techniques:
  - Lancement: java -cp target/peersim-djl-1.0.jar com.example.peersimdjl.App
  - Logs attendus: [Learning] Session <id> started, [Node <id>] Epoch <N> accuracy: <X>, [Learning] Session <id> DONE   
  - Stockage: src/main/resources/stockage/ (créé automatiquement)

  ---
  41. Déploiement: Nettoyage du stockage

  Nom: Nettoyage des données persistantes
  Description: Suppression des fichiers de batchs CSV.
  Détails techniques:
  - Commande: rm -rf src/main/resources/stockage/
  - Automatique: les batches sont supprimés après la transition DONE de la session
  - Manuels: les répertoires de nœuds morts ne sont pas nettoyés automatiquement




    UI Overview

  Structure du frontend

  - Framework : React 18 + Vite, TypeScript/JSX, Tailwind-like utility CSS (système maison)
  - Point d’entrée : ui/src/main.jsx monte <App /> dans #root
  - Composants principaux :
    - App.jsx : Layout principal (Sidebar, Header, Dashboard/Summary tabs), logique d’état global (événements WebSocket,   parsing, sélection de session)
    - Sidebar : Barre latérale fixe avec contrôles de session
    - LaunchForm : Formulaire de lancement (CSV, modèle MLP/CNN, epochs, nœuds, configuration des sessions)
    - NetworkGraph / NetworkTraceGraph : Visualisation de la topologie anneau Chord, communications nœud↔nœud/IDE, flux 
  gradients
    - AccuracyChart : Courbes local/global accuracy par epoch (Recharts)
    - ParamHeatmap : Évolution des paramètres du modèle (poids) par epoch
    - EventFeed : Fil d’événements temps réel (logs simulés)
    - SessionsPanel / StatCard / StatusBadge : Liste des sessions, badges d’état, statistiques rapides

  Rôle de chaque composant principal

  - App : Orchestre WebSocket, parse les événements, maintient les listes (sessions, communications, accuracy), fournit 
  handleStart/Stop/Clear au formulaire.
  - LaunchForm : Collecte fichiers CSV, paramètres, envoie multipart/form-data au backend (/api/simulations/start) et   
  requête d’arrêt (/api/simulations/stop).
  - NetworkTraceGraph : Affiche le graphe des communications et la table des nœuds actifs/rôles (IDE/learner).
  - AccuracyChart : Trace accuracy locale vs globale par epoch pour chaque session.
  - ParamHeatmap : Cartographie thermique des paramètres globaux au fil des epochs.
  - EventFeed : Liste filtrable des événements d’apprentissage.

  ---
  API Communication

  Endpoints utilisés

  ┌─────────────────────────────────────┬────────────────────────────────────────────────────────────┬──────┐
  │               Méthode               │                          Endpoint                          │ Rôle │
  ├─────────────────────────────────────┼────────────────────────────────────────────────────────────┼──────┤
  │ POST /api/simulations/start         │ Démarre une simulation (multipart)                         │      │
  ├─────────────────────────────────────┼────────────────────────────────────────────────────────────┼──────┤
  │ POST /api/simulations/stop          │ Arrête la simulation courante                              │      │
  ├─────────────────────────────────────┼────────────────────────────────────────────────────────────┼──────┤
  │ GET /api/simulations/status         │ État courant ({state: SimulationState})                    │      │
  ├─────────────────────────────────────┼────────────────────────────────────────────────────────────┼──────┤
  │ GET /api/simulations/events?limit=N │ Flux d’événements (utilisé via WebSocket fallback/initial) │      │
  └─────────────────────────────────────┴────────────────────────────────────────────────────────────┴──────┘

  Détails

  POST /api/simulations/start

  - Content-Type : multipart/form-data
  - Part files (optionnel) : MultipartFile[] — fichiers CSV du dataset
  - Part config (obligatoire) : JSON SimulationRequest
  {
    "sessionId": 1,
    "requiredLearners": 3,
    "csvDataset": "adult.csv",
    "modelType": "MLP",
    "sessionCount": 1
  }
  - (champs internes étendus dans le formulaire : networkSize, sessionRequirements, federatedEpochs, learningRate,      
  batchStrategy, preprocessOnUpload, simulationCycles)
  - Réponses
    - 200 OK : { "message": "started" }
    - 400 Bad Request : { "error": "…" }
    - 409 Conflict : { "error": "…" } (déjà en cours)

  POST /api/simulations/stop

  - Réponses
    - 200 OK : { "message": "stopped" }
    - 409 Conflict : { "error": "…" } (aucune simulation active)

  GET /api/simulations/status

  - Réponse 200 OK :
  { "state": "RUNNING" }
  - (SimulationState : IDLE, RUNNING, STOPPED, FAILED)

  ---
  Data Flow

  1. User action
  L’utilisateur remplit le formulaire (CSV, MLP/CNN, epochs, etc.) et clique Start.
  2. Envoi API
  LaunchForm → fetch multipart POST /api/simulations/start
    - Fichiers CSV uploadés → FileStorageService → stockage temporaire
    - Config JSON désérialisée en SimulationRequest
    - SimulationService.start() déclenche la simulation PeerSim interne.
  3. Backend → WebSocket / Events
    - La simulation écrit des logs via le framework d’événements interne.
    - Le frontend maintient une connexion WebSocket (ws://localhost:8080/ws) et un fallback GET
  /api/simulations/events?limit=2000.
    - Les événements transitent en temps réel (type SIM_LOG, ACCURACY, …).
  4. Parsing & Mise à jour UI
    - App parse les événements :
        - Stats réseau (nœuds, IDE)
      - Communications (gradients, modèles, votes)
      - Accuracy (locale/globale)
      - Sessions (statut, datasets)

    - Les graphes et tableaux se mettent à jour via useMemo / useState.
  5. Stop / Clear
    - Stop : POST /stop interrompt la simulation.
    - Clear : réinitialise l’état UI sans requête backend.

  ---
  Note : L’UI écoute sur http://localhost:8080 ; le backend Spring Boot et PeerSim tournent sur le même port (CORS      
  autorisé sur http://localhost:5173).