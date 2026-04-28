import React, { useCallback, useMemo, useEffect, useState } from "react";
import useWebSocket from "./hooks/useWebSocket";
import LaunchForm from "./components/LaunchForm";
import EventFeed from "./components/EventFeed";
import AccuracyChart from "./components/AccuracyChart";
import NetworkTraceGraph from "./components/NetworkTraceGraph";
import ParamHeatmap from "./components/ParamHeatmap";

function Sidebar({ onStart, onStop, onClear }) {
  return (
    <aside
      style={{
        width: 320,
        backgroundColor: "#1F2937",
        color: "#fff",
        height: "100vh",
        position: "fixed",
        top: 0,
        left: 0,
        zIndex: 100,
        display: "flex",
        flexDirection: "column",
      }}
    >
      <div style={{ padding: "20px 16px", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
        <h2 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 700 }}>PeerSim</h2>
        <div style={{ fontSize: "0.8rem", color: "rgba(255,255,255,0.6)", marginTop: 6 }}>
          Session Control
        </div>
      </div>
      <div style={{ padding: "16px", overflowY: "auto" }}>
        <div style={{ backgroundColor: "#111827", padding: 12, borderRadius: 10 }}>
          <LaunchForm onStart={onStart} onStop={onStop} onClear={onClear} />
        </div>
      </div>
      <div style={{ padding: "16px", marginTop: "auto", borderTop: "1px solid rgba(255,255,255,0.1)", fontSize: "0.75rem", color: "rgba(255,255,255,0.5)" }}>
        PeerSim DJL v1.0
      </div>
    </aside>
  );
}

function Header({ connected, onRefresh }) {
  return (
    <header
      style={{
        backgroundColor: "#111827",
        color: "#fff",
        padding: "12px 24px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        position: "sticky",
        top: 0,
        zIndex: 50,
        boxShadow: "0 2px 8px rgba(0,0,0,0.15)",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
        <div
          style={{
            width: 8,
            height: 8,
            borderRadius: "50%",
            backgroundColor: connected ? "#10B981" : "#EF4444",
            boxShadow: connected ? "0 0 8px rgba(16,185,129,0.6)" : "0 0 8px rgba(239,68,68,0.6)",
          }}
        />
        <h1 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 600 }}>PeerSim Dashboard</h1>
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <span style={{ fontSize: "0.85rem", color: "rgba(255,255,255,0.7)" }}>
          {connected ? "Connected" : "Disconnected"}
        </span>
        <button
          onClick={onRefresh}
          style={{
            backgroundColor: "#3B82F6",
            border: "none",
            color: "#fff",
            padding: "8px 16px",
            borderRadius: 6,
            cursor: "pointer",
            fontSize: "0.85rem",
            fontWeight: 500,
            transition: "background-color 0.2s",
          }}
          onMouseEnter={(e) => (e.target.style.backgroundColor = "#2563EB")}
          onMouseLeave={(e) => (e.target.style.backgroundColor = "#3B82F6")}
        >
          ↻ Refresh
        </button>
      </div>
    </header>
  );
}

function Card({ children, style, hoverable }) {
  return (
    <div
      style={{
        backgroundColor: "#fff",
        borderRadius: 12,
        boxShadow: "0 1px 3px rgba(0,0,0,0.08), 0 4px 12px rgba(0,0,0,0.04)",
        padding: 20,
        transition: "transform 0.2s, box-shadow 0.2s",
        ...style,
      }}
      onMouseEnter={(e) => {
        if (hoverable) {
          e.currentTarget.style.transform = "translateY(-2px)";
          e.currentTarget.style.boxShadow = "0 4px 12px rgba(0,0,0,0.12), 0 8px 24px rgba(0,0,0,0.06)";
        }
      }}
      onMouseLeave={(e) => {
        if (hoverable) {
          e.currentTarget.style.transform = "translateY(0)";
          e.currentTarget.style.boxShadow = "0 1px 3px rgba(0,0,0,0.08), 0 4px 12px rgba(0,0,0,0.04)";
        }
      }}
    >
      {children}
    </div>
  );
}

function StatCard({ label, value, icon, color }) {
  return (
    <Card style={{ display: "flex", alignItems: "center", gap: 16, padding: "16px 20px" }}>
      <div
        style={{
          width: 48,
          height: 48,
          borderRadius: 12,
          backgroundColor: color + "15",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: "1.4rem",
        }}
      >
        {icon}
      </div>
      <div>
        <div style={{ fontSize: "0.8rem", color: "#6B7280", marginBottom: 4 }}>{label}</div>
        <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "#111827" }}>{value}</div>
      </div>
    </Card>
  );
}

function StatusBadge({ status }) {
  const config = {
    RUNNING: { bg: "#D1FAE5", color: "#065F46", text: "Running" },
    INIT: { bg: "#FEF3C7", color: "#92400E", text: "Initializing" },
    DONE: { bg: "#DBEAFE", color: "#1E40AF", text: "Completed" },
    IDLE: { bg: "#F3F4F6", color: "#374151", text: "Idle" },
  };
  const c = config[status] || config.IDLE;
  return (
    <span
      style={{
        backgroundColor: c.bg,
        color: c.color,
        padding: "4px 10px",
        borderRadius: 20,
        fontSize: "0.75rem",
        fontWeight: 600,
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: "50%", backgroundColor: c.color, display: "inline-block" }} />
      {c.text}
    </span>
  );
}

function QuickStats({ networkStats, sessionStats, eventCount, connected }) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
        gap: 16,
        marginBottom: 24,
      }}
    >
      <StatCard label="Active Nodes" value={networkStats.activeNodes || 0} icon="🖥️" color="#3B82F6" />
      <StatCard label="Total Nodes" value={(networkStats.nodes || []).length} icon="🌐" color="#8B5CF6" />
      <StatCard label="Session Status" value={<StatusBadge status={sessionStats.currentStatus} />} icon="⚡" color="#F59E0B" />
      <StatCard label="Events Logged" value={eventCount} icon="📋" color="#10B981" />
      <StatCard label="Connection" value={connected ? "Online" : "Offline"} icon={connected ? "✅" : "❌"} color={connected ? "#10B981" : "#EF4444"} />
    </div>
  );
}

function NetworkPanel({ networkStats, communications, sessionNodes }) {
  const effectiveIde = networkStats.ideNode || sessionNodes[0] || networkStats.nodes[0];
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
        <h3 style={{ margin: 0, fontSize: "1rem", fontWeight: 600, color: "#111827" }}>Network Topology</h3>
        <span style={{ fontSize: "0.8rem", color: "#6B7280" }}>Updated: {networkStats.lastUpdated || "—"}</span>
      </div>
      <NetworkTraceGraph
        nodes={networkStats.nodes}
        ideNode={effectiveIde}
        sessionNodes={sessionNodes}
        communications={communications}
      />
      {networkStats.nodes.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 12 }}>
          {networkStats.nodes.map((node) => (
            <span
              key={node}
              style={{
                backgroundColor: node === networkStats.ideNode ? "#FEE2E2" : "#E5E7EB",
                color: node === networkStats.ideNode ? "#991B1B" : "#374151",
                padding: "4px 10px",
                borderRadius: 6,
                fontSize: "0.8rem",
                fontWeight: node === networkStats.ideNode ? 600 : 400,
              }}
            >
              {node} {node === networkStats.ideNode ? "(IDE)" : ""}
            </span>
          ))}
        </div>
      )}
      <div style={{ marginTop: 16, borderTop: "1px solid #F3F4F6", paddingTop: 12 }}>
        <h4 style={{ margin: "0 0 8px", fontSize: "0.9rem", color: "#374151" }}>Recent Communications</h4>
        <div style={{ maxHeight: 180, overflowY: "auto", fontSize: "0.8rem" }}>
          {(communications || []).length === 0 && <div style={{ color: "#9CA3AF" }}>No communications yet.</div>}
          {(communications || []).slice(-20).reverse().map((comm) => (
            <div
              key={comm.id}
              style={{
                padding: "6px 0",
                borderBottom: "1px solid #F9FAFB",
                display: "flex",
                alignItems: "center",
                gap: 8,
                fontSize: "0.78rem",
              }}
            >
              <span style={{ color: "#9CA3AF", minWidth: 65 }}>{comm.time}</span>
              <span style={{ fontWeight: 600, color: "#111827" }}>{comm.source}</span>
              <span style={{ color: "#D1D5DB" }}>→</span>
              <span style={{ color: comm.dest === effectiveIde ? "#DC2626" : "#111827" }}>{comm.dest}</span>
              <span
                style={{
                  backgroundColor: "#F3F4F6",
                  color: "#374151",
                  borderRadius: 4,
                  padding: "2px 6px",
                  fontSize: "0.7rem",
                  marginLeft: "auto",
                }}
              >
                {comm.commType}
              </span>
              {comm.epoch && (
                <span style={{ color: "#6B7280", fontSize: "0.7rem" }}>E{comm.epoch}</span>
              )}
              {comm.samples && (
                <span style={{ color: "#6B7280", fontSize: "0.7rem" }}>rows={comm.samples}</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function SessionsPanel({ sessions, selectedSessionId, onSelect, sessionStats }) {
  const selected = sessions.find((s) => s.id === selectedSessionId) || null;
  return (
    <div>
      <h3 style={{ margin: "0 0 12px", fontSize: "1rem", fontWeight: 600, color: "#111827" }}>Sessions</h3>
      <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: 16 }}>
        <div>
          <div style={{ fontSize: "0.8rem", color: "#6B7280", marginBottom: 8 }}>
            Total sessions: {sessions.length}
          </div>
          <div style={{ maxHeight: 220, overflowY: "auto", border: "1px solid #E5E7EB", borderRadius: 10 }}>
            {sessions.length === 0 && (
              <div style={{ padding: 12, color: "#9CA3AF" }}>No sessions yet.</div>
            )}
            {sessions.map((s) => (
              <div
                key={s.id}
                onClick={() => onSelect(s.id)}
                style={{
                  padding: "10px 12px",
                  cursor: "pointer",
                  backgroundColor: s.id === selectedSessionId ? "#EEF2FF" : "#fff",
                  borderBottom: "1px solid #F3F4F6",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <span style={{ fontWeight: 600, color: "#111827", fontFamily: "monospace" }}>{s.id}</span>
                  <StatusBadge status={s.status} />
                </div>
                <div style={{ fontSize: "0.75rem", color: "#6B7280", marginTop: 4 }}>
                  {s.dataset || "Dataset: —"}
                </div>
              </div>
            ))}
          </div>
        </div>
        <div style={{ border: "1px solid #E5E7EB", borderRadius: 10, padding: 12 }}>
          <div style={{ fontSize: "0.85rem", color: "#6B7280", marginBottom: 8 }}>Selected session</div>
          {!selected && (
            <div style={{ color: "#9CA3AF" }}>Select a session to see details.</div>
          )}
          {selected && (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <div style={{ fontWeight: 700, color: "#111827" }}>{selected.id}</div>
              <div><StatusBadge status={selected.status} /></div>
              <div style={{ fontSize: "0.8rem", color: "#6B7280" }}>{selected.dataset || "Dataset: —"}</div>
              <div style={{ fontSize: "0.8rem", color: "#6B7280" }}>
                Last update: {selected.lastUpdated ? new Date(selected.lastUpdated).toLocaleTimeString("en-GB") : "—"}
              </div>
            </div>
          )}
          <div style={{ marginTop: 16, fontSize: "0.8rem", color: "#6B7280" }}>
            Current session: {sessionStats.currentSessionId || "—"}
          </div>
        </div>
      </div>
    </div>
  );
}

function SummaryTab({ sessionsSummary, learningHistory }) {
  return (
    <div>
      <h3 style={{ margin: "0 0 12px", fontSize: "1rem", fontWeight: 600, color: "#111827" }}>
        Learning History
      </h3>
      {learningHistory.length === 0 && (
        <div style={{ color: "#9CA3AF", marginBottom: 16 }}>No learning runs yet.</div>
      )}
      {learningHistory.length > 0 && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16, marginBottom: 24 }}>
          {learningHistory.map((run) => (
            <Card key={run.id} hoverable>
              <div style={{ fontWeight: 700, color: "#111827", fontFamily: "monospace", marginBottom: 6 }}>
                {run.sessionId || run.id}
              </div>
              <div style={{ fontSize: "0.8rem", color: "#374151", marginBottom: 4 }}>
                Start: <strong>{run.startedAtLabel || "—"}</strong>
              </div>
              <div style={{ fontSize: "0.8rem", color: "#374151", marginBottom: 4 }}>
                Dataset: <strong>{run.dataset || "—"}</strong>
              </div>
              <div style={{ fontSize: "0.8rem", color: "#374151" }}>
                Summary: <strong>{run.summary || "—"}</strong>
              </div>
            </Card>
          ))}
        </div>
      )}
      <h3 style={{ margin: "0 0 12px", fontSize: "1rem", fontWeight: 600, color: "#111827" }}>
        Sessions Summary
      </h3>
      {sessionsSummary.length === 0 && (
        <div style={{ color: "#9CA3AF" }}>No session summaries yet.</div>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 16 }}>
        {sessionsSummary.map((session) => (
          <Card key={session.id} hoverable>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
              <div style={{ fontWeight: 700, color: "#111827", fontFamily: "monospace" }}>{session.id}</div>
              <StatusBadge status={session.status} />
            </div>
            <div style={{ fontSize: "0.8rem", color: "#6B7280", marginBottom: 8 }}>
              {session.dataset || "Dataset: —"}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 8, marginBottom: 10 }}>
              <div style={{ fontSize: "0.8rem", color: "#374151" }}>
                Duration: <strong>{session.duration || "—"}</strong>
              </div>
              <div style={{ fontSize: "0.8rem", color: "#374151" }}>
                Samples: <strong>{session.samples ?? "—"}</strong>
              </div>
              <div style={{ fontSize: "0.8rem", color: "#374151" }}>
                Nodes used: <strong>{session.nodesUsed ?? "—"}</strong>
              </div>
              <div style={{ fontSize: "0.8rem", color: "#374151" }}>
                Last update: <strong>{session.lastUpdatedLabel || "—"}</strong>
              </div>
            </div>
            {session.points.length === 0 && (
              <div style={{ color: "#9CA3AF", fontSize: "0.8rem" }}>No accuracy data.</div>
            )}
            {session.points.length > 0 && (
              <AccuracyChart accuracyPoints={session.points} height={180} showLegend={false} />
            )}
          </Card>
        ))}
      </div>
    </div>
  );
}

function Footer() {
  return (
    <footer
      style={{
        textAlign: "center",
        padding: "16px",
        color: "#9CA3AF",
        fontSize: "0.75rem",
        borderTop: "1px solid #E5E7EB",
        marginTop: 32,
      }}
    >
      PeerSim DJL Dashboard • Built with React & Spring Boot
    </footer>
  );
}

function parseAccuracy(events) {
  const accuracyPoints = [];
  const sums = new Map();
  const globalByEpoch = new Map();
  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;
    const match = message.match(/\[EPOCH\s+(\d+)\]\[COMPARE\].*localAcc=([0-9.]+)\s+globalAcc=([0-9.]+)/);
    if (!match) continue;
    const epoch = Number(match[1]);
    const localAccuracy = Number(match[2]);
    const globalAccuracy = Number(match[3]);
    if (Number.isNaN(epoch) || Number.isNaN(localAccuracy) || Number.isNaN(globalAccuracy)) continue;
    const existing = sums.get(epoch) || { localSum: 0, globalSum: 0, count: 0 };
    existing.localSum += localAccuracy;
    existing.globalSum += globalAccuracy;
    existing.count += 1;
    sums.set(epoch, existing);
  }

  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;

    const globalMatch = message.match(/\[EPOCH\s+(\d+)\]\[GLOBAL\]\s+real accuracy=([0-9.]+)/);
    if (globalMatch) {
      const epoch = Number(globalMatch[1]);
      const globalAccuracy = Number(globalMatch[2]);
      if (!Number.isNaN(epoch) && !Number.isNaN(globalAccuracy)) {
        globalByEpoch.set(epoch, globalAccuracy);
      }
    }

    const localMatch = message.match(/\[EPOCH\s+(\d+)\]\[Node\s+(\S+)\]\s+real accuracy=([0-9.]+)/);
    if (localMatch) {
      const epoch = Number(localMatch[1]);
      const localAccuracy = Number(localMatch[3]);
      if (!Number.isNaN(epoch) && !Number.isNaN(localAccuracy)) {
        const existing = sums.get(epoch) || { localSum: 0, globalSum: 0, count: 0 };
        existing.localSum += localAccuracy;
        existing.count += 1;
        sums.set(epoch, existing);
      }
    }
  }

  for (const [epoch, value] of sums.entries()) {
    const globalAccuracy = globalByEpoch.has(epoch)
      ? globalByEpoch.get(epoch)
      : value.count > 0
        ? value.globalSum / value.count
        : 0;
    accuracyPoints.push({
      epoch,
      localAccuracy: value.count > 0 ? value.localSum / value.count : 0,
      globalAccuracy,
    });
  }
  return accuracyPoints.sort((a, b) => a.epoch - b.epoch);
}

function parseParamEvolution(events) {
  const values = new Map();
  const epochsSet = new Set();
  const paramsSet = new Set();

  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;
    const match = message.match(/\[EPOCH\s+(\d+)\]\[Depot param\[(\d+)\]\].*value=([-0-9.eE]+)/);
    if (!match) continue;
    const epoch = Number(match[1]);
    const param = Number(match[2]);
    const value = Number(match[3]);
    if (!Number.isFinite(epoch) || !Number.isFinite(param) || !Number.isFinite(value)) continue;
    epochsSet.add(epoch);
    paramsSet.add(param);
    if (!values.has(epoch)) values.set(epoch, new Map());
    values.get(epoch).set(param, value);
  }

  const epochs = Array.from(epochsSet).sort((a, b) => a - b);
  const params = Array.from(paramsSet).sort((a, b) => a - b);
  return { epochs, params, values };
}

function isLearningLog(message) {
  if (!message || typeof message !== "string") return false;
  return /epoch|accuracy|loss|dataset|batch|gradient|weights|param|model|learning|fedavg|global|local/i.test(message)
    && !/chord|finger|stabilize|notify|successor|predecessor|route|dht/i.test(message);
}

function isLearningCommunication(message) {
  if (!message || typeof message !== "string") return false;
  if (/chord|finger|stabilize|notify|successor|predecessor|route|dht|join|leave/i.test(message)) return false;
  return /epoch|accuracy|loss|dataset|batch|gradient|weights|param|model|learning|fedavg|global|local|ide node|élu|election|élection|leader|coordinator|aggregat/i.test(message);
}

function parseNetworkStats(events) {
  let activeNodes = 0;
  let nodes = [];
  let ideNode = null;
  let lastUpdated = null;
  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;
    const countMatch = message.match(/Nœuds actifs ciblés\s*:\s*(\d+)/);
    if (countMatch) {
      activeNodes = Number(countMatch[1]);
      lastUpdated = evt?.timestamp ? new Date(evt.timestamp).toLocaleTimeString("en-GB") : null;
    }
    const ideMatch = message.match(/IDE Node (?:élu|réservé)\s*:\s*(\S+)/);
    if (ideMatch) {
      ideNode = ideMatch[1];
      lastUpdated = evt?.timestamp ? new Date(evt.timestamp).toLocaleTimeString("en-GB") : null;
    }
    const listMatch = message.match(/Active nodes:\s*\[(.*)\]/);
    if (listMatch) {
      nodes = listMatch[1].split(",").map((n) => n.trim()).filter(Boolean);
      activeNodes = nodes.length;
      lastUpdated = evt?.timestamp ? new Date(evt.timestamp).toLocaleTimeString("en-GB") : null;
    }
  }
  return { activeNodes, nodes, ideNode, lastUpdated };
}

function parseCommunications(events, ideNode) {
  const communications = [];
  const typePatterns = [
    { type: "GRADIENT", re: /gradient|poids|weights|param/i },
    { type: "MODEL", re: /model|aggr[ée]gat|fusion/i },
    { type: "BATCH", re: /batch|dataset|distribu/i },
    { type: "VOTE", re: /vote|convergence|accuracy/i },
    { type: "EPOCH", re: /epoch|f[ée]d[ée]r[ée]|global/i },
    { type: "ELECTION", re: /ide node|élu|election|élection|leader|coordinator/i },
  ];

  const detectType = (message) => {
    for (const entry of typePatterns) {
      if (entry.re.test(message)) return entry.type;
    }
    return "LOG";
  };

  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;
    if (evt?.type && evt.type !== "SIM_LOG") continue;
    if (!isLearningCommunication(message)) continue;

    // Extraire le nœud source
    const nodeMatch = message.match(/\[Node\s+(\S+)\]/)
      || message.match(/\[([N][\w]*)\]/)
      || message.match(/node[=:\s]+([A-Za-z0-9_-]+)/i);
    const source = nodeMatch ? nodeMatch[1] : null;
    if (!source) continue;

    // Extraire la destination de manière plus précise
    let dest = null;

    // Pattern: "... to Node X" ou "... to N1"
    const toMatch = message.match(/to\s+(?:Node\s+)?([A-Za-z0-9_-]+)/i);
    if (toMatch) dest = toMatch[1];

    // Pattern: "... → N1" ou "...-> N2"
    if (!dest) {
      const arrowMatch = message.match(/[→\-]>[\s]*([A-Za-z0-9_-]+)/);
      if (arrowMatch) dest = arrowMatch[1];
    }

    // Pattern: "IDE Node", "peer N1"
    if (!dest) {
      const peerMatch = message.match(/peer\s+([A-Za-z0-9_-]+)/i)
        || message.match(/IDE\s+Node\s+([A-Za-z0-9_-]+)/i);
      if (peerMatch) dest = peerMatch[1];
    }

    // Si pas de destination claire, fallback vers IDE
    if (!dest) dest = ideNode || "IDE";

    const epochMatch = message.match(/\[EPOCH\s+(\d+)\]/);
    const epoch = epochMatch ? epochMatch[1] : null;
    const samplesMatch = message.match(/rows=([0-9]+)/i) || message.match(/samples=([0-9]+)/i);
    const samples = samplesMatch ? samplesMatch[1] : null;

    const time = evt?.timestamp
      ? new Date(evt.timestamp).toLocaleTimeString("en-GB")
      : "--:--:--";

    communications.push({
      id: `${evt.timestamp || "no-ts"}-${source}-${dest}-${message.slice(0, 40)}`,
      time,
      source,
      dest,
      commType: detectType(message),
      epoch,
      samples,
      summary: message.length > 80 ? message.slice(0, 80) + "…" : message,
    });
  }
  return communications;
}

function parseSessionStats(events) {
  let sessionsCreated = 0;
  let currentSessionId = null;
  let currentStatus = null;
  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;
    const createdMatch = message.match(/Session créée\s*:\s*(\S+)/);
    if (createdMatch) {
      sessionsCreated += 1;
      currentSessionId = createdMatch[1];
      currentStatus = "INIT";
    }
    const initMatch = message.match(/Session\s+(\S+)\s*:\s*initialisation/);
    if (initMatch) {
      currentSessionId = initMatch[1];
      currentStatus = "INIT";
    }
    if (message.includes("en état RUNNING")) {
      currentStatus = "RUNNING";
    }
    if (message.includes("maintenant DONE")) {
      currentStatus = "DONE";
    }
  }
  return { sessionsCreated, currentSessionId, currentStatus };
}

function parseSessions(events) {
  const sessions = new Map();
  let lastSessionId = null;

  const ensureSession = (sessionId) => {
    if (!sessions.has(sessionId)) {
      sessions.set(sessionId, {
        id: sessionId,
        status: "INIT",
        createdAt: null,
        dataset: null,
        lastUpdated: null,
        samples: null,
        nodesUsed: null,
      });
    }
    return sessions.get(sessionId);
  };

  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;

    const createdMatch = message.match(/Session créée\s*:\s*(\S+)/);
    if (createdMatch) {
      const sessionId = createdMatch[1];
      lastSessionId = sessionId;
      const s = ensureSession(sessionId);
      s.status = "INIT";
      s.createdAt = evt?.timestamp || s.createdAt;
      s.lastUpdated = evt?.timestamp || s.lastUpdated;
      continue;
    }

    const initMatch = message.match(/Session\s+(\S+)\s*:\s*initialisation/);
    if (initMatch) {
      const sessionId = initMatch[1];
      lastSessionId = sessionId;
      const s = ensureSession(sessionId);
      s.status = "INIT";
      s.lastUpdated = evt?.timestamp || s.lastUpdated;
      continue;
    }

    const summarySessionMatch = message.match(/Session\s*:\s*(\S+)/);
    if (summarySessionMatch) {
      const sessionId = summarySessionMatch[1];
      lastSessionId = sessionId;
      ensureSession(sessionId).lastUpdated = evt?.timestamp || ensureSession(sessionId).lastUpdated;
    }

    const datasetMatch = message.match(/Dataset\s*:\s*(.+)$/);
    if (datasetMatch && lastSessionId) {
      const s = ensureSession(lastSessionId);
      s.dataset = datasetMatch[1].trim();
      s.lastUpdated = evt?.timestamp || s.lastUpdated;
    }

    if (message.includes("en état RUNNING") && lastSessionId) {
      const s = ensureSession(lastSessionId);
      s.status = "RUNNING";
      s.lastUpdated = evt?.timestamp || s.lastUpdated;
    }

    if (message.includes("maintenant DONE") && lastSessionId) {
      const s = ensureSession(lastSessionId);
      s.status = "DONE";
      s.lastUpdated = evt?.timestamp || s.lastUpdated;
    }

    const samplesMatch = message.match(/rows=([0-9]+)/i) || message.match(/samples=([0-9]+)/i);
    if (samplesMatch && lastSessionId) {
      const s = ensureSession(lastSessionId);
      s.samples = Number(samplesMatch[1]);
      s.lastUpdated = evt?.timestamp || s.lastUpdated;
    }

    const nodesMatch = message.match(/Active nodes:\s*\[(.*)\]/);
    if (nodesMatch && lastSessionId) {
      const nodes = nodesMatch[1].split(",").map((n) => n.trim()).filter(Boolean);
      const s = ensureSession(lastSessionId);
      s.nodesUsed = nodes.length;
      s.lastUpdated = evt?.timestamp || s.lastUpdated;
    }
  }

  return Array.from(sessions.values()).sort((a, b) => {
    const aTime = a.createdAt ? new Date(a.createdAt).getTime() : 0;
    const bTime = b.createdAt ? new Date(b.createdAt).getTime() : 0;
    return bTime - aTime;
  });
}

function parseSessionAccuracy(events) {
  const bySession = new Map();
  let currentEpoch = null;
  let currentSession = null;

  const ensure = (sessionId) => {
    if (!bySession.has(sessionId)) {
      bySession.set(sessionId, { points: [], dataset: null });
    }
    return bySession.get(sessionId);
  };

  for (const evt of events || []) {
    const message = evt?.message;
    if (!message || typeof message !== "string") continue;

    const epochMatch = message.match(/\[EPOCH\s+(\d+)\]\s+SUMMARY/);
    if (epochMatch) {
      currentEpoch = Number(epochMatch[1]);
      currentSession = null;
      continue;
    }

    const sessionMatch = message.match(/Session\s*:\s*(\S+)/);
    if (sessionMatch) {
      currentSession = sessionMatch[1];
      ensure(currentSession);
      continue;
    }

    const datasetMatch = message.match(/Dataset\s*:\s*(.+)$/);
    if (datasetMatch && currentSession) {
      ensure(currentSession).dataset = datasetMatch[1].trim();
      continue;
    }

    const globalMatch = message.match(/GLOBAL\s+\|\s+acc=([0-9.]+)/);
    if (globalMatch && currentSession != null && currentEpoch != null) {
      const acc = Number(globalMatch[1]);
      if (Number.isFinite(acc)) {
        ensure(currentSession).points.push({
          epoch: currentEpoch,
          localAccuracy: acc,
          globalAccuracy: acc,
        });
      }
    }
  }

  return bySession;
}

export default function App() {
  const { events, connected } = useWebSocket(
    "ws://localhost:8080/ws",
    "http://localhost:8080/api/simulations/events?limit=2000"
  );

  const [clearTick, setClearTick] = useState(0);
  const [networkSize, setNetworkSize] = useState(0);
  const [selectedSessionId, setSelectedSessionId] = useState(null);
  const [activeTab, setActiveTab] = useState("dashboard");
  const [summaryHistory, setSummaryHistory] = useState([]);
  const [learningHistory, setLearningHistory] = useState([]);

  const handleStart = useCallback(async (payload) => {
    try {
      const resetTime = Date.now();
      setClearTick(resetTime);
      setSelectedSessionId(null);
      setLastNetworkStats({ activeNodes: 0, nodes: [], ideNode: null, lastUpdated: null });
      setLastSessionStats({ sessionsCreated: 0, currentSessionId: null, currentStatus: null });
      setLastAccuracyPoints([]);
      setLastCommunications([]);
      try {
        if (payload?.formData && payload?.sessionConfigs) {
          const first = payload.sessionConfigs[0];
          if (first && Number.isFinite(first.networkSize)) {
            setNetworkSize(first.networkSize);
          }
        }
      } catch {}
      if (!payload?.formData || !payload?.sessionConfigs) {
        return;
      }

      for (const config of payload.sessionConfigs) {
        const fd = new FormData();
        for (const [key, value] of payload.formData.entries()) {
          if (key !== "config") {
            fd.append(key, value);
          }
        }
        fd.append("config", JSON.stringify(config));

        const resp = await fetch("http://localhost:8080/api/simulations/start", {
          method: "POST",
          body: fd,
        });

        if (!resp.ok) {
          const data = await resp.json();
          alert(data.error || "Failed to start simulation");
          break;
        }
      }
    } catch (e) {
      alert(e.message);
    }
  }, []);

  const handleStop = useCallback(async () => {
    try {
      const resp = await fetch("http://localhost:8080/api/simulations/stop", { method: "POST" });
      if (!resp.ok) {
        const data = await resp.json();
        alert(data.error || "Failed to stop simulation");
      }
    } catch (e) {
      alert(e.message);
    }
  }, []);

  const handleClear = useCallback(() => {
    const now = Date.now();
    setClearTick(now);
    setNetworkSize(0);
    setLastNetworkStats({ activeNodes: 0, nodes: [], ideNode: null, lastUpdated: null });
    setLastSessionStats({ sessionsCreated: 0, currentSessionId: null, currentStatus: null });
    setLastAccuracyPoints([]);
    setLastCommunications([]);
  }, []);

  const handleRefresh = () => setClearTick(0);

  const filteredEvents = useMemo(() => {
    if (!clearTick) return events;
    return (events || []).filter((evt) => {
      if (!evt?.timestamp) return false;
      return new Date(evt.timestamp).getTime() > clearTick;
    });
  }, [events, clearTick]);

  const accuracyPoints = useMemo(() => parseAccuracy(filteredEvents), [filteredEvents]);
  const paramEvolution = useMemo(() => parseParamEvolution(filteredEvents), [filteredEvents]);
  const learningEvents = useMemo(
    () => (filteredEvents || []).filter((evt) => isLearningLog(evt?.message)),
    [filteredEvents]
  );
  const networkStats = useMemo(() => parseNetworkStats(filteredEvents), [filteredEvents]);
  const sessionStats = useMemo(() => parseSessionStats(filteredEvents), [filteredEvents]);
  const sessions = useMemo(() => parseSessions(filteredEvents), [filteredEvents]);
  const sessionAccuracy = useMemo(() => parseSessionAccuracy(filteredEvents), [filteredEvents]);
  const communications = useMemo(
    () => parseCommunications(filteredEvents, networkStats.ideNode || networkStats.nodes[0]),
    [filteredEvents, networkStats.ideNode, networkStats.nodes]
  );
  const sessionNodes = useMemo(() => networkStats.nodes, [networkStats.nodes]);
  const allNodes = useMemo(() => {
    if (!networkSize || networkSize < 1) return networkStats.nodes;
    return Array.from({ length: networkSize }, (_, idx) => `N${idx}`);
  }, [networkSize, networkStats.nodes]);

  const sessionsSummary = useMemo(() => {
    return sessions.map((s) => {
      const accuracy = sessionAccuracy.get(s.id);
      const start = s.createdAt ? new Date(s.createdAt).getTime() : null;
      const end = s.lastUpdated ? new Date(s.lastUpdated).getTime() : null;
      const durationMs = start && end ? Math.max(0, end - start) : null;
      const duration = durationMs != null
        ? `${Math.floor(durationMs / 60000)}m ${Math.floor((durationMs % 60000) / 1000)}s`
        : null;
      return {
        id: s.id,
        status: s.status,
        dataset: s.dataset || accuracy?.dataset || null,
        points: accuracy?.points || [],
        duration,
        samples: s.samples,
        nodesUsed: s.nodesUsed,
        lastUpdatedLabel: s.lastUpdated ? new Date(s.lastUpdated).toLocaleTimeString("en-GB") : null,
      };
    });
  }, [sessions, sessionAccuracy]);

  useEffect(() => {
    if (sessionsSummary.length === 0) return;
    setSummaryHistory((prev) => {
      const merged = new Map();
      for (const s of prev) merged.set(s.id, s);
      for (const s of sessionsSummary) merged.set(s.id, s);
      return Array.from(merged.values());
    });
  }, [sessionsSummary]);

  useEffect(() => {
    if (!events || events.length === 0) return;
    setLearningHistory((prev) => {
      const merged = new Map();
      for (const item of prev) merged.set(item.id, item);

      for (const evt of events) {
        const message = evt?.message;
        if (!message || typeof message !== "string") continue;
        if (evt?.type && evt.type !== "SIM_LOG") continue;

        const createdMatch = message.match(/Session créée\s*:\s*(\S+)/)
          || message.match(/Session\s+(\S+)\s*:\s*initialisation/)
          || message.match(/Session\s*:\s*(\S+)/);
        const sessionId = createdMatch ? createdMatch[1] : null;
        if (!sessionId) continue;

        const startedAtMs = evt?.timestamp ? new Date(evt.timestamp).getTime() : Date.now();
        const existing = merged.get(sessionId) || {
          id: sessionId,
          sessionId,
          startedAtMs,
          startedAtLabel: evt?.timestamp ? new Date(evt.timestamp).toLocaleTimeString("en-GB") : null,
          dataset: null,
          summary: null,
        };

        const datasetMatch = message.match(/Dataset\s*:\s*(.+)$/);
        if (datasetMatch) existing.dataset = datasetMatch[1].trim();

        const summaryMatch = message.match(/\[EPOCH\s+\d+\]\s+SUMMARY\s*[:\-]?\s*(.*)$/i)
          || message.match(/SUMMARY\s*[:\-]?\s*(.*)$/i);
        if (summaryMatch) {
          const text = summaryMatch[1]?.trim();
          if (text) existing.summary = text;
        }

        merged.set(sessionId, existing);
      }

      return Array.from(merged.values()).sort((a, b) => (b.startedAtMs || 0) - (a.startedAtMs || 0));
    });
  }, [events]);

  const [lastNetworkStats, setLastNetworkStats] = useState(networkStats);
  const [lastSessionStats, setLastSessionStats] = useState(sessionStats);
  const [lastAccuracyPoints, setLastAccuracyPoints] = useState(accuracyPoints);
  const [lastCommunications, setLastCommunications] = useState([]);
  const [lastLearningEvents, setLastLearningEvents] = useState([]);
  const [lastParamEvolution, setLastParamEvolution] = useState({ epochs: [], params: [], values: new Map() });

  useEffect(() => {
    if (networkStats.nodes.length > 0 || networkStats.activeNodes > 0) {
      setLastNetworkStats(networkStats);
    }
  }, [networkStats]);

  useEffect(() => {
    if (sessionStats.currentSessionId || sessionStats.sessionsCreated > 0) {
      setLastSessionStats(sessionStats);
    }
  }, [sessionStats]);

  useEffect(() => {
    if (!selectedSessionId && sessions.length > 0) {
      setSelectedSessionId(sessions[0].id);
    }
  }, [sessions, selectedSessionId]);

  useEffect(() => {
    if (accuracyPoints.length > 0) {
      setLastAccuracyPoints(accuracyPoints);
    }
  }, [accuracyPoints]);

  useEffect(() => {
    if (communications.length === 0) return;
    setLastCommunications((prev) => {
      const merged = new Map();
      for (const comm of prev) merged.set(comm.id, comm);
      for (const comm of communications) merged.set(comm.id, comm);
      const result = Array.from(merged.values());
      return result.length > 500 ? result.slice(result.length - 500) : result;
    });
  }, [communications]);

  useEffect(() => {
    if (!learningEvents || learningEvents.length === 0) return;
    setLastLearningEvents(learningEvents);
  }, [learningEvents]);

  useEffect(() => {
    if (!paramEvolution || paramEvolution.epochs.length === 0) return;
    setLastParamEvolution(paramEvolution);
  }, [paramEvolution]);

  return (
    <div style={{ display: "flex", minHeight: "100vh", fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif", backgroundColor: "#F3F4F6" }}>
      <Sidebar onStart={handleStart} onStop={handleStop} onClear={handleClear} />
      <div style={{ flex: 1, marginLeft: 320, minWidth: 0 }}>
        <Header connected={connected} onRefresh={handleRefresh} />
        <main style={{ padding: "24px", maxWidth: 1400, margin: "0 auto", width: "100%" }}>
          <div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
            <button
              onClick={() => setActiveTab("dashboard")}
              style={{
                backgroundColor: activeTab === "dashboard" ? "#2563EB" : "#E5E7EB",
                color: activeTab === "dashboard" ? "#fff" : "#374151",
                border: "none",
                padding: "8px 16px",
                borderRadius: 8,
                cursor: "pointer",
                fontWeight: 600,
              }}
            >
              Dashboard
            </button>
            <button
              onClick={() => setActiveTab("summary")}
              style={{
                backgroundColor: activeTab === "summary" ? "#2563EB" : "#E5E7EB",
                color: activeTab === "summary" ? "#fff" : "#374151",
                border: "none",
                padding: "8px 16px",
                borderRadius: 8,
                cursor: "pointer",
                fontWeight: 600,
              }}
            >
              Summary
            </button>
          </div>

          {activeTab === "dashboard" && (
            <>
              <QuickStats
                networkStats={{ ...lastNetworkStats, nodes: allNodes }}
                sessionStats={lastSessionStats}
                eventCount={(filteredEvents || []).length}
                connected={connected}
              />

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fit, minmax(380px, 1fr))",
                  gap: 20,
                  marginBottom: 24,
                }}
              >
                <Card style={{ gridColumn: "span 2", minWidth: 380 }}>
                  <NetworkPanel
                    networkStats={{ ...lastNetworkStats, nodes: allNodes }}
                    communications={lastCommunications}
                    sessionNodes={sessionNodes}
                  />
                </Card>
                <Card>
                  <AccuracyChart events={lastLearningEvents} accuracyPoints={lastAccuracyPoints} />
                </Card>
                <Card>
                  <h3 style={{ margin: "0 0 12px", fontSize: "1rem", fontWeight: 600, color: "#111827" }}>
                    Model Parameters Evolution
                  </h3>
                  <ParamHeatmap data={lastParamEvolution} />
                </Card>
                <Card>
                  <SessionsPanel
                    sessionStats={lastSessionStats}
                    sessions={sessions}
                    selectedSessionId={selectedSessionId}
                    onSelect={setSelectedSessionId}
                  />
                </Card>
                <Card style={{ gridColumn: "span 2", minWidth: 380 }}>
                  <EventFeed events={lastLearningEvents} />
                </Card>
              </div>
            </>
          )}

          {activeTab === "summary" && (
            <SummaryTab sessionsSummary={summaryHistory} learningHistory={learningHistory} />
          )}

          <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
            <button
              onClick={handleClear}
              style={{
                backgroundColor: "#374151",
                color: "#fff",
                border: "none",
                padding: "10px 20px",
                borderRadius: 8,
                cursor: "pointer",
                fontSize: "0.85rem",
                fontWeight: 500,
                transition: "background-color 0.2s",
                height: 40,
              }}
              onMouseEnter={(e) => (e.target.style.backgroundColor = "#1F2937")}
              onMouseLeave={(e) => (e.target.style.backgroundColor = "#374151")}
            >
              Clear All Panels
            </button>
          </div>

          <Footer />
        </main>
      </div>
    </div>
  );
}
