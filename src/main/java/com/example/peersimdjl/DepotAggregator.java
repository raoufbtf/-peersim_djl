package com.example.peersimdjl;

import java.util.Map;

/**
 * Agrège les dépôts complets présents localement et publie les paramètres globaux.
 */
public class DepotAggregator {

    private final ChordProtocol chord;
    private final String ideNodeId;

    public DepotAggregator(ChordProtocol chord, String ideNodeId) {
        this.chord = chord;
        this.ideNodeId = ideNodeId;
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
            String globalKey = FederatedDhtKeys.globalKey(epoch, depot.getParamIndex());

            ChordProtocol ownerChord = ParameterShardRouter.ownerForParam(chord, depot.getParamIndex());
            if (ownerChord == null) {
                continue;
            }

            ownerChord.putLocal(key, depot);
            ownerChord.putLocal(globalKey, aggregatedValue);

                SimulationCommEventLogger.emit(
                    "DEPOT",
                    ownerChord.nodeIdString,
                    ideNodeId,
                    epoch,
                    (int) peersim.core.CommonState.getTime(),
                    "param[" + depot.getParamIndex() + "]",
                    (double) aggregatedValue,
                    null,
                    null,
                    "aggregated from " + depot.getContributions().keySet());

            System.out.println("[EPOCH " + epoch + "][Depot param[" + depot.getParamIndex() + "]][Node "
                    + ownerChord.nodeIdString + "] contributions=" + depot.getContributions().keySet()
                    + " -> global=" + aggregatedValue + " key=" + globalKey);
        }
    }
}
