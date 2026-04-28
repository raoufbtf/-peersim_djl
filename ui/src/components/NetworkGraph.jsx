import React from "react";

import NetworkTraceGraph from "./NetworkTraceGraph";

export default function NetworkGraph({ nodes, ideNode, communications }) {
  if (!nodes || nodes.length === 0) {
    return (
      <div style={{ color: "#9ca3af", textAlign: "center", padding: "16px" }}>
        No network data yet.
      </div>
    );
  }

  return (
    <NetworkTraceGraph
      nodes={nodes}
      ideNode={ideNode}
      sessionNodes={nodes}
      communications={communications}
    />
  );
}
