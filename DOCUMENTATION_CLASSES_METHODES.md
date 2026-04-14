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

## Remarque
Cette documentation décrit les signatures et le comportement observé dans le code actuel.
Si tu veux, je peux aussi te générer une **version UML (PlantUML)** à partir de ce même inventaire.
