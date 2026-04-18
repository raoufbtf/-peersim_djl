package com.example.peersimdjl;

import java.util.Map;

/**
 * Agrège les dépôts complets présents localement et publie les paramètres globaux.
 */
public class DepotAggregator {

    private final ChordProtocol chord;

    public DepotAggregator(ChordProtocol chord) {
        this.chord = chord;
    }

    public void checkAndAggregate(int epoch) {
        Map<String, Object> localEntries = chord.snapshotLocalStorage();
        String expectedPrefix = "grad/epoch/" + epoch + "/param/";

        for (Map.Entry<String, Object> entry : localEntries.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(expectedPrefix)) {
                continue;
            }
            if (!(entry.getValue() instanceof ParamDepot)) {
                continue;
            }

            ParamDepot depot = (ParamDepot) entry.getValue();
            if (depot.isAggregated()) {
                continue;
            }
            if (depot.getContributions().isEmpty()) {
                continue;
            }

            float aggregatedValue = depot.aggregate();
            chord.putLocal(key, depot);

            String globalKey = FederatedDhtKeys.globalKey(epoch, depot.getParamIndex());
            chord.put(globalKey, aggregatedValue);

            System.out.println("[EPOCH " + epoch + "][Depot param[" + depot.getParamIndex() + "]][Node "
                    + chord.nodeIdString + "] contributions=" + depot.getContributions().keySet()
                    + " -> global=" + aggregatedValue + " key=" + globalKey);
        }
    }
}
