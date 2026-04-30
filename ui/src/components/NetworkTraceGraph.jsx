import React from "react";

export default function NetworkTraceGraph({ nodes, ideNode, sessionNodes, communications }) {
  if (!nodes || nodes.length === 0) {
    return (
      <div style={{ color: "#9ca3af", textAlign: "center", padding: "16px" }}>
        No network data yet.
      </div>
    );
  }

  const width = 520;
  const height = 260;
  const centerX = width / 2;
  const centerY = height / 2;
  const radius = Math.min(width, height) / 2 - 40;

  const positions = nodes.map((node, idx) => {
    const angle = (2 * Math.PI * idx) / nodes.length - Math.PI / 2;
    return {
      node,
      x: centerX + radius * Math.cos(angle),
      y: centerY + radius * Math.sin(angle),
    };
  });

  const getPosition = (nodeId) => positions.find((p) => p.node === nodeId);
  const sessionSet = new Set(sessionNodes || []);

  const comms = (communications || []).slice(-60);
  const hasComms = comms.length > 0;
  const fallbackComms = !hasComms && ideNode
    ? (sessionNodes || nodes)
        .filter((node) => node !== ideNode)
        .map((node, idx) => ({
          id: `fallback-${node}-${idx}`,
          source: node,
          dest: ideNode,
          summary: "idle",
        }))
    : [];

  const animated = (hasComms ? communications : fallbackComms).slice(-16);

  return (
    <svg width={width} height={height} style={{ display: "block", margin: "0 auto" }}>
      <rect x="8" y="8" width={width - 16} height={height - 16} rx="12" fill="#ffffff" stroke="#e5e7eb" />

      {/* Ring representation of the DHT overlay */}
      <circle
        cx={centerX}
        cy={centerY}
        r={radius}
        fill="none"
        stroke="#e5e7eb"
        strokeWidth={1}
        strokeDasharray="4 4"
      />
      {/* Optional: draw connections between consecutive nodes to emphasize the ring */}
      {positions.length > 1 && positions.map((p, idx) => {
        const next = positions[(idx + 1) % positions.length];
        return (
          <line
            key={`ring-${p.node}`}
            x1={p.x}
            y1={p.y}
            x2={next.x}
            y2={next.y}
            stroke="#e5e7eb"
            strokeWidth={1}
          />
        );
      })}
      <defs>
        <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="3" result="coloredBlur" />
          <feMerge>
            <feMergeNode in="coloredBlur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <marker
          id="arrow"
          viewBox="0 0 10 10"
          refX="10"
          refY="5"
          markerWidth="6"
          markerHeight="6"
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill="#9ca3af" />
        </marker>
      </defs>

      {comms.map((comm, idx) => {
        const src = getPosition(comm.source);
        const dst = getPosition(comm.dest);
        if (!src || !dst) return null;
        let stroke = "#16a34a"; // default
        if (comm.commType && comm.commType.startsWith("DHT_")) {
          stroke = "#3b82f6"; // blue for DHT put/get/lookup
        } else if (comm.commType === "AGGREGATION") {
          stroke = "#f59e0b"; // orange for aggregation
        } else if (comm.dest === ideNode) {
          stroke = "#10b981"; // green for direct IDE communications
        }
        return (
          <line
            key={`link-${comm.id || idx}`}
            x1={src.x}
            y1={src.y}
            x2={dst.x}
            y2={dst.y}
            stroke={stroke}
            strokeWidth={2}
            markerEnd="url(#arrow)"
            opacity={0.7}
          />
        );
      })}

      {animated.map((comm, idx) => {
        const src = getPosition(comm.source);
        const dst = getPosition(comm.dest);
        if (!src || !dst) return null;
        const path = `M ${src.x} ${src.y} L ${dst.x} ${dst.y}`;
        return (
          <circle key={`anim-${comm.id || idx}`} r={7} fill="#fde047" stroke="#f59e0b" strokeWidth={2}>
            <animateMotion
              dur="2.6s"
              repeatCount="indefinite"
              begin={`${idx * 0.08}s`}
              path={path}
            />
            <animate attributeName="r" values="5;8;5" dur="1.4s" repeatCount="indefinite" />
            <animate attributeName="opacity" values="0.4;1;0.4" dur="1.4s" repeatCount="indefinite" />
          </circle>
        );
      })}

      {positions.map((p) => {
        let fill = "#f3f4f6";
        let stroke = "#6b7280";
        let radius = 12;
        let strokeWidth = 2;
        let filter = undefined;
        if (p.node === ideNode) {
          fill = "#b91c1c";
          stroke = "#7f1d1d";
          strokeWidth = 4; // thicker border to highlight IDE without larger radius
          filter = "url(#glow)";
        } else if (sessionSet.has(p.node)) {
          fill = "#22c55e";
          stroke = "#15803d";
        }
        return (
          <circle
            key={`node-${p.node}`}
            cx={p.x}
            cy={p.y}
            r={radius}
            fill={fill}
            stroke={stroke}
            strokeWidth={strokeWidth}
            filter={filter}
          />
        );
      })}

      {positions.map((p) => (
        <text
          key={`label-${p.node}`}
          x={p.x}
          y={p.y - 18}
          textAnchor="middle"
          fontSize="11"
          fill="#111827"
        >
          {p.node}
        </text>
      ))}

      <g transform="translate(16, 18)">
        <rect width="150" height="96" rx="8" fill="#f9fafb" stroke="#e5e7eb" />
        <circle cx="12" cy="16" r="7" fill="#b91c1c" />
        <text x="24" y="20" fontSize="11" fill="#111827">IDE (node)</text>
        <circle cx="12" cy="34" r="6" fill="#22c55e" />
        <text x="24" y="38" fontSize="11" fill="#111827">Learner</text>
        <circle cx="12" cy="50" r="6" fill="#3b82f6" />
        <text x="24" y="54" fontSize="11" fill="#111827">DHT (put/get)</text>
        <circle cx="12" cy="66" r="6" fill="#f59e0b" />
        <text x="24" y="70" fontSize="11" fill="#111827">Aggregation</text>
        <circle cx="12" cy="82" r="6" fill="#10b981" />
        <text x="24" y="86" fontSize="11" fill="#111827">IDE Direct</text>
      </g>
    </svg>
  );
}
