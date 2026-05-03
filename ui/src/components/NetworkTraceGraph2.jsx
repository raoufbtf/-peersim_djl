import React from "react";

const T = {
  surface: "#0d1526", card: "#111d35", border: "#1e2d4a",
  cyan: "#00c8ff", green: "#10d98a", amber: "#f5a623", red: "#f43f5e", purple: "#a78bfa",
  textPrimary: "#e2eaf8", textSecondary: "#6b82a8", textMuted: "#3d5070",
  fontMono: "'JetBrains Mono', 'Fira Code', monospace",
};

const COMM_COLORS = {
  GRADIENT:     T.cyan,
  GOSSIP_VOTE:  T.purple,
  DEPOT:        T.amber,
  GLOBAL_MODEL: T.green,
  STATE:        T.red,
};

export default function NetworkTraceGraph({ nodes, ideNode, sessionNodes, communications, speed = 1 }) {
  if (!nodes || nodes.length === 0) {
    return (
      <div style={{ height: 280, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 8 }}>
        <div style={{ color: T.border, fontSize: 48 }}>⬡</div>
        <div style={{ color: T.textMuted, fontFamily: T.fontMono, fontSize: 12 }}>No network data yet</div>
      </div>
    );
  }

  const W = 600, H = 290;
  const cx = W / 2, cy = H / 2;
  const r = Math.min(W, H) / 2 - 44;
  const sessionSet = new Set(sessionNodes || []);

  const positions = nodes.map((node, idx) => {
    const angle = (2 * Math.PI * idx) / nodes.length - Math.PI / 2;
    return { node, x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) };
  });

  const getPos = id => positions.find(p => p.node === id);

  const recentComms = (communications || []).slice(-40);
  const animated = recentComms.slice(-12);

  return (
    <div style={{ position: "relative" }}>
      <svg width="100%" viewBox={`0 0 ${W} ${H}`} style={{ display: "block" }}>
        <defs>
          <filter id="glow-red" x="-60%" y="-60%" width="220%" height="220%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
          <filter id="glow-green" x="-60%" y="-60%" width="220%" height="220%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
          {Object.entries(COMM_COLORS).map(([type, color]) => (
            <marker key={type} id={`arrow-${type}`} viewBox="0 0 8 8" refX="8" refY="4" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
              <path d="M 0 0 L 8 4 L 0 8 z" fill={color} opacity="0.8" />
            </marker>
          ))}
          <marker id="arrow-default" viewBox="0 0 8 8" refX="8" refY="4" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
            <path d="M 0 0 L 8 4 L 0 8 z" fill={T.textMuted} opacity="0.6" />
          </marker>

          <radialGradient id="bg-grad" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#0a1628" />
            <stop offset="100%" stopColor="#070c18" />
          </radialGradient>
        </defs>

        <rect width={W} height={H} fill="url(#bg-grad)" rx="10" />
        <circle cx={cx} cy={cy} r={r} fill="none" stroke={T.border} strokeWidth="1" strokeDasharray="4 6" opacity="0.4" />
        <circle cx={cx} cy={cy} r={r * 0.5} fill="none" stroke={T.border} strokeWidth="1" strokeDasharray="3 8" opacity="0.2" />

        {ideNode && positions.filter(p => p.node !== ideNode && sessionSet.has(p.node)).map(p => {
          const ide = getPos(ideNode);
          if (!ide) return null;
          return (
            <line key={`base-${p.node}`} x1={ide.x} y1={ide.y} x2={p.x} y2={p.y} stroke={T.border} strokeWidth={1} opacity={0.4} />
          );
        })}

        {recentComms.map((comm, i) => {
          const src = getPos(comm.source || comm.from);
          const dst = getPos(comm.dest || comm.to);
          if (!src || !dst || src === dst) return null;
          const color = COMM_COLORS[comm.commType || comm.type] || T.textSecondary;
          const markerId = `arrow-${comm.commType || comm.type}`;
          return (
            <line key={`link-${comm.id || i}`} x1={src.x} y1={src.y} x2={dst.x} y2={dst.y} stroke={color} strokeWidth={1.5} markerEnd={`url(#${markerId})`} opacity={0.55} />
          );
        })}

        {animated.map((comm, idx) => {
          const src = getPos(comm.source || comm.from);
          const dst = getPos(comm.dest || comm.to);
          if (!src || !dst || src === dst) return null;
          const path = `M ${src.x} ${src.y} L ${dst.x} ${dst.y}`;
          const color = COMM_COLORS[comm.commType || comm.type] || T.cyan;
          const baseDur = 1.8 + (idx % 4) * 0.3;
          const baseDel = (idx * 0.12) % 2;
          const dur = Math.max(0.05, baseDur / Math.max(0.01, speed));
          const del = baseDel / Math.max(0.01, speed);
          return (
            <circle key={`pkt-${comm.id || idx}`} r={4} fill={color} opacity={0.9}>
              <animateMotion dur={`${dur}s`} repeatCount="indefinite" begin={`${del}s`} path={path} />
              <animate attributeName="opacity" values="0;1;1;0" dur={`${dur}s`} repeatCount="indefinite" begin={`${del}s`} />
              <animate attributeName="r" values="3;5;3" dur={`${dur * 0.6}s`} repeatCount="indefinite" />
            </circle>
          );
        })}

        {positions.map(p => {
          const isIde = p.node === ideNode;
          const isSess = sessionSet.has(p.node);
          const fill   = isIde ? T.red     : isSess ? T.green   : "#1a2a45";
          const stroke = isIde ? "#ff6b6b" : isSess ? "#4aeea4" : T.border;
          const nr     = isIde ? 14        : isSess ? 11        : 8;
          const filterId = isIde ? "url(#glow-red)" : isSess ? "url(#glow-green)" : undefined;
          return (
            <circle key={`node-${p.node}`} cx={p.x} cy={p.y} r={nr} fill={fill} stroke={stroke} strokeWidth={isIde ? 2.5 : 1.5} filter={filterId} />
          );
        })}

        {positions.map(p => {
          const isIde = p.node === ideNode;
          const isSess = sessionSet.has(p.node);
          const color = isIde ? T.red : isSess ? T.green : T.textSecondary;
          const dx = p.x - cx, dy = p.y - cy;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          const lx = p.x + (dx / dist) * 20;
          const ly = p.y + (dy / dist) * 20;
          return (
            <text key={`lbl-${p.node}`} x={lx} y={ly} textAnchor="middle" dominantBaseline="middle" fontSize="9" fontFamily={T.fontMono} fontWeight={isIde ? 700 : 500} fill={color}>{p.node}</text>
          );
        })}

        <g transform="translate(12,12)">
          <rect width="105" height="68" rx="6" fill={T.card} stroke={T.border} />
          <circle cx={14} cy={16} r={6} fill={T.red} filter="url(#glow-red)" />
          <text x={26} y={20} fontSize="10" fontFamily={T.fontMono} fill={T.textSecondary}>IDE Node</text>
          <circle cx={14} cy={34} r={5} fill={T.green} filter="url(#glow-green)" />
          <text x={26} y={38} fontSize="10" fontFamily={T.fontMono} fill={T.textSecondary}>Learner</text>
          <circle cx={14} cy={52} r={4} fill="#1a2a45" stroke={T.border} />
          <text x={26} y={56} fontSize="10" fontFamily={T.fontMono} fill={T.textSecondary}>Other</text>
        </g>

        <g transform={`translate(${W - 120}, 12)`}>
          <rect width="112" height="82" rx="6" fill={T.card} stroke={T.border} />
          {Object.entries(COMM_COLORS).slice(0, 4).map(([type, color], i) => (
            <g key={type} transform={`translate(8, ${10 + i * 17})`}>
              <line x1={0} y1={4} x2={12} y2={4} stroke={color} strokeWidth={2} />
              <text x={18} y={8} fontSize="9" fontFamily={T.fontMono} fill={T.textSecondary}>{type}</text>
            </g>
          ))}
        </g>
      </svg>
    </div>
  );
}
