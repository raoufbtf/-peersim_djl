import React, { useCallback, useMemo, useEffect, useState, useRef } from "react";
import useWebSocket from "./hooks/useWebSocket";
import LaunchForm from "./components/LaunchForm";
import AccuracyChart from "./components/AccuracyChart";
import EventFeed from "./components/EventFeed";
import NetworkTraceGraph from "./components/NetworkTraceGraph";
import ParamHeatmap from "./components/ParamHeatmap";

/* ─── THEME ────────────────────────────────────────────── */
const T = {
  bg:       "#070c18",
  surface:  "#0d1526",
  card:     "#111d35",
  border:   "#1e2d4a",
  cyan:     "#00c8ff",
  green:    "#10d98a",
  amber:    "#f5a623",
  red:      "#f43f5e",
  purple:   "#a78bfa",
  textPrimary:   "#e2eaf8",
  textSecondary: "#6b82a8",
  textMuted:     "#3d5070",
  fontMono: "'JetBrains Mono', 'Fira Code', 'Courier New', monospace",
  fontUI:   "'Inter', system-ui, sans-serif",
};

/* inject Google Font once */
function useFonts() {
  useEffect(() => {
    const id = "peersim-fonts";
    if (document.getElementById(id)) return;
    const link = document.createElement("link");
    link.id = id;
    link.rel = "stylesheet";
    link.href = "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;700&display=swap";
    document.head.appendChild(link);
    const style = document.createElement("style");
    style.textContent = `
      * { box-sizing: border-box; }
      ::-webkit-scrollbar { width: 6px; height: 6px; }
      ::-webkit-scrollbar-track { background: ${T.surface}; }
      ::-webkit-scrollbar-thumb { background: ${T.border}; border-radius: 3px; }
      ::-webkit-scrollbar-thumb:hover { background: #2e4470; }
      @keyframes pulse-dot { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.5;transform:scale(.7)} }
      @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }
      @keyframes fadeIn { from{opacity:0;transform:translateY(6px)} to{opacity:1;transform:translateY(0)} }
      @keyframes gradient-flow {
        0%{background-position:0% 50%} 50%{background-position:100% 50%} 100%{background-position:0% 50%}
      }
    `;
    document.head.appendChild(style);
  }, []);
}

/* ─── PARSERS ───────────────────────────────────────────── */
function parseAccuracy(events, communications) {
  const sums = new Map();
  const globalByEpoch = new Map();
  const localOnGlobalByEpoch = new Map();
  for (const evt of events || []) {
    /* structured ACCURACY events */
    if (evt?.type === "ACCURACY" && evt.payload) {
      let { epoch, localAccuracy, globalAccuracy } = evt.payload;
      // Normalise les valeurs envoyées en pourcentage (ex: 76.07) ou en fraction (0.7607)
      const norm = v => (v == null || Number.isNaN(v) ? null : (Number(v) > 1 ? Number(v) / 100 : Number(v)));
      localAccuracy = norm(localAccuracy);
      globalAccuracy = norm(globalAccuracy);
      if (epoch != null) {
        const e = sums.get(epoch) || { localSum: 0, localCount: 0, globalSum: 0, globalCount: 0 };
        if (localAccuracy != null) {
          e.localSum += localAccuracy;
          e.localCount += 1;
        }
        if (globalAccuracy != null) {
          e.globalSum += globalAccuracy;
          e.globalCount += 1;
          globalByEpoch.set(epoch, globalAccuracy);
        }
        sums.set(epoch, e);
      }
      continue;
    }
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    const globalMatch = msg.match(/\[EPOCH\s+(\d+)\]\[GLOBAL\]\s+real accuracy=([0-9.]+)/);
    if (globalMatch) {
      let val = +globalMatch[2]; if (val > 1) val = val / 100;
      globalByEpoch.set(+globalMatch[1], val);
    }
    const localMatch = msg.match(/\[EPOCH\s+(\d+)\]\[Node\s+\S+\]\s+real accuracy=([0-9.]+)/);
    if (localMatch) {
      const ep = +localMatch[1]; let la = +localMatch[2]; if (la > 1) la = la / 100;
      const e = sums.get(ep) || { localSum: 0, localCount: 0, globalSum: 0, globalCount: 0 };
      e.localSum += la;
      e.localCount += 1;
      sums.set(ep, e);
    }
  }
  // Also parse structured communications for local-on-global accuracy emissions
  for (const comm of communications || []) {
    // comm object shape from useWebSocket -> { commType, epoch, value, param, ... }
    try {
      const type = String(comm?.commType || comm?.type || "").toUpperCase();
      const param = String(comm?.param || "").toLowerCase();
      if (type === "LOCAL_ACCURACY" || param === "local_accuracy") {
        const ep = Number.isFinite(Number(comm.epoch)) ? Number(comm.epoch) : null;
        let v = comm.value;
        if (v == null) continue;
        v = Number(v);
        if (Number.isNaN(v)) continue;
        if (v > 1) v = v / 100;
        if (ep != null) {
          const e = sums.get(ep) || { localSum: 0, localCount: 0, globalSum: 0, globalCount: 0 };
          e.localSum += v;
          e.localCount += 1;
          sums.set(ep, e);
        }
        continue;
      }

      if (type === "GLOBAL_ACCURACY" || param === "global_accuracy") {
        const ep = Number.isFinite(Number(comm.epoch)) ? Number(comm.epoch) : null;
        let v = comm.value;
        if (v == null) continue;
        v = Number(v);
        if (Number.isNaN(v)) continue;
        if (v > 1) v = v / 100;
        if (ep != null) {
          globalByEpoch.set(ep, v);
        }
        continue;
      }

      if (type === "LOCAL_ON_GLOBAL" || param === "local_on_global") {
        const ep = Number.isFinite(Number(comm.epoch)) ? Number(comm.epoch) : null;
        let v = comm.value;
        if (v == null) continue;
        v = Number(v);
        if (Number.isNaN(v)) continue;
        if (v > 1) v = v / 100; // normalize percent to fraction
        if (ep != null) {
          const s = localOnGlobalByEpoch.get(ep) || { sum: 0, count: 0 };
          s.sum += v; s.count += 1;
          localOnGlobalByEpoch.set(ep, s);
        }
      }
    } catch (e) {
      // ignore malformed comms
    }
  }

  const epochs = new Set([...sums.keys(), ...globalByEpoch.keys(), ...localOnGlobalByEpoch.keys()]);
  return Array.from(epochs).map((epoch) => {
    const v = sums.get(epoch) || { localSum: 0, localCount: 0, globalSum: 0, globalCount: 0 };
    const lg = localOnGlobalByEpoch.get(epoch) || { sum: 0, count: 0 };
    return {
      epoch,
      localAccuracy: v.localCount ? v.localSum / v.localCount : null,
      globalAccuracy: globalByEpoch.has(epoch)
        ? globalByEpoch.get(epoch)
        : (v.globalCount ? v.globalSum / v.globalCount : null),
      localOnGlobal: lg.count ? (lg.sum / lg.count) : null,
    };
  }).sort((a, b) => a.epoch - b.epoch);
}

function mergeAccuracySeries(previousPoints, nextPoints) {
  const mergedByEpoch = new Map();
  for (const point of previousPoints || []) {
    if (point?.epoch == null) continue;
    mergedByEpoch.set(point.epoch, point);
  }
  for (const point of nextPoints || []) {
    if (point?.epoch == null) continue;
    mergedByEpoch.set(point.epoch, point);
  }
  return [...mergedByEpoch.values()].sort((a, b) => a.epoch - b.epoch);
}

function parseNodeStats(events) {
  /* returns Map<nodeId, { epoch, accuracy, loss }> — latest per node */
  const latest = new Map();
  for (const evt of events || []) {
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    const m = msg.match(/\[EPOCH\s+(\d+)\]\[Node\s+(\S+)\]\s+real accuracy=([0-9.]+)\s+real loss=([0-9.]+)/);
    if (m) {
      const epoch = +m[1], node = m[2];
      let acc = +m[3]; if (acc > 1) acc = acc / 100;
      const loss = +m[4];
      const prev = latest.get(node);
      if (!prev || epoch >= prev.epoch) latest.set(node, { epoch, accuracy: acc, loss });
    }
  }
  return latest;
}

function parseGlobalMetrics(events) {
  let epoch = 0, accuracy = null, loss = null, dataset = 0;
  for (const evt of events || []) {
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    const m = msg.match(/\[EPOCH\s+(\d+)\]\[GLOBAL\]\s+real accuracy=([0-9.]+)\s+real loss=([0-9.]+)\s+\(dataset=(\d+)\)/);
    if (m && +m[1] >= epoch) {
      epoch = +m[1];
      let acc = +m[2]; if (acc > 1) acc = acc / 100;
      accuracy = acc; loss = +m[3]; dataset = +m[4];
    }
  }
  return { epoch, accuracy, loss, dataset };
}

function parseEpochProgress(events) {
  let currentEpoch = 0, maxEpoch = 10;
  for (const evt of events || []) {
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    const startM = msg.match(/\[EPOCH\s+(\d+)\]\s+=====\s+Federated Epoch START/);
    if (startM) currentEpoch = Math.max(currentEpoch, +startM[1]);
    const fedM = msg.match(/federatedEpochs[=:\s]+(\d+)/i);
    if (fedM) maxEpoch = +fedM[1];
  }
  return { currentEpoch, maxEpoch };
}

function parseSessionStats(events) {
  let sessionsCreated = 0, currentSessionId = null, currentStatus = null;
  for (const evt of events || []) {
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    if (msg.match(/Session créée\s*:\s*(\S+)/)?.[1]) { sessionsCreated++; currentSessionId = msg.match(/Session créée\s*:\s*(\S+)/)[1]; currentStatus = "INIT"; }
    if (msg.includes("en état RUNNING")) currentStatus = "RUNNING";
    if (msg.includes("maintenant DONE") || msg.includes("Transition RUNNING → DONE")) currentStatus = "DONE";
  }
  return { sessionsCreated, currentSessionId, currentStatus: currentStatus || "IDLE" };
}

function parseNetworkStats(events) {
  let activeNodes = 0, nodes = [], ideNode = null, lastUpdated = null;
  for (const evt of events || []) {
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    const countM = msg.match(/Nœuds actifs ciblés\s*:\s*(\d+)/);
    if (countM) { activeNodes = +countM[1]; lastUpdated = evt.timestamp ? new Date(evt.timestamp).toLocaleTimeString("en-GB") : null; }
    const ideM = msg.match(/IDE Node (?:élu|réservé)\s*:\s*(\S+)/);
    if (ideM) { ideNode = ideM[1]; lastUpdated = evt.timestamp ? new Date(evt.timestamp).toLocaleTimeString("en-GB") : null; }
    const listM = msg.match(/Active nodes:\s*\[(.*)\]/);
    if (listM) { nodes = listM[1].split(",").map(n => n.trim()).filter(Boolean); activeNodes = nodes.length; lastUpdated = evt.timestamp ? new Date(evt.timestamp).toLocaleTimeString("en-GB") : null; }
  }
  return { activeNodes, nodes, ideNode, lastUpdated };
}

function parseCommunications(events) {
  /* parse BOTH structured COMM_EVENT_JSON (type !== SIM_LOG) and text logs */
  const comms = [];
  for (const evt of events || []) {
    /* structured event (GRADIENT, GOSSIP_VOTE, DEPOT, GLOBAL_MODEL, STATE) */
    if (evt?.type && evt.type !== "SIM_LOG" && evt.from && evt.to) {
      comms.push({
        id: `${evt.ts || evt.timestamp}-${evt.seq || comms.length}`,
        time: evt.timestamp || "",
        source: evt.from,
        dest: evt.to,
        commType: evt.type,
        epoch: evt.epoch,
        cycle: evt.cycle,
        detail: evt.detail || "",
        value: evt.value,
        param: evt.param,
        voteCount: evt.voteCount,
      });
    }
  }
  return comms;
}

function parseCommTypeCounts(communications) {
  const counts = {};
  for (const c of communications) {
    counts[c.commType] = (counts[c.commType] || 0) + 1;
  }
  return counts;
}

function parseParamEvolution(events) {
  const values = new Map();
  const epochsSet = new Set(), paramsSet = new Set();
  for (const evt of events || []) {
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    const m = msg.match(/\[EPOCH\s+(\d+)\]\[Depot param\[(\d+)\]\].*value=([-0-9.eE]+)/);
    if (!m) continue;
    const ep = +m[1], param = +m[2], val = +m[3];
    if (!isFinite(ep) || !isFinite(param) || !isFinite(val)) continue;
    epochsSet.add(ep); paramsSet.add(param);
    if (!values.has(ep)) values.set(ep, new Map());
    values.get(ep).set(param, val);
  }
  return { epochs: [...epochsSet].sort((a,b)=>a-b), params: [...paramsSet].sort((a,b)=>a-b), values };
}

function parseSessions(events) {
  const sessions = new Map();
  let lastId = null;
  const ensure = id => { if (!sessions.has(id)) sessions.set(id, { id, status: "INIT", createdAt: null, dataset: null, lastUpdated: null, samples: null, nodesUsed: null }); return sessions.get(id); };
  for (const evt of events || []) {
    const msg = evt?.message;
    if (!msg || typeof msg !== "string") continue;
    const cm = msg.match(/Session créée\s*:\s*(\S+)/);
    if (cm) { lastId = cm[1]; const s = ensure(lastId); s.status = "INIT"; s.createdAt = evt.timestamp; s.lastUpdated = evt.timestamp; continue; }
    const dm = msg.match(/Dataset\s*:\s*(.+)$/);
    if (dm && lastId) { ensure(lastId).dataset = dm[1].trim(); }
    if (msg.includes("en état RUNNING") && lastId) ensure(lastId).status = "RUNNING";
    if ((msg.includes("maintenant DONE") || msg.includes("Transition RUNNING → DONE")) && lastId) { ensure(lastId).status = "DONE"; ensure(lastId).lastUpdated = evt.timestamp; }
    const sm = msg.match(/rows=([0-9]+)/i) || msg.match(/samples=([0-9]+)/i);
    if (sm && lastId) ensure(lastId).samples = +sm[1];
    const nm = msg.match(/Active nodes:\s*\[(.*)\]/);
    if (nm && lastId) ensure(lastId).nodesUsed = nm[1].split(",").filter(Boolean).length;
  }
  return [...sessions.values()].sort((a,b) => (new Date(b.createdAt||0)) - (new Date(a.createdAt||0)));
}

function isLearningLog(msg) {
  if (!msg || typeof msg !== "string") return false;
  return /epoch|accuracy|loss|dataset|batch|gradient|weights|param|model|learning|fedavg|global|local/i.test(msg)
    && !/chord|finger|stabilize|notify|successor|predecessor|route|dht/i.test(msg);
}

/* ─── UI ATOMS ──────────────────────────────────────────── */
function Card({ children, style, title, subtitle, action }) {
  return (
    <div style={{
      background: T.card,
      border: `1px solid ${T.border}`,
      borderRadius: 12,
      overflow: "hidden",
      ...style,
    }}>
      {(title || subtitle || action) && (
        <div style={{ padding: "14px 18px", borderBottom: `1px solid ${T.border}`, display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
          <div>
            {title && <div style={{ fontFamily: T.fontUI, fontSize: 13, fontWeight: 600, color: T.textPrimary, letterSpacing: "0.03em", textTransform: "uppercase" }}>{title}</div>}
            {subtitle && <div style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary, marginTop: 2 }}>{subtitle}</div>}
          </div>
          {action}
        </div>
      )}
      <div style={{ padding: "16px 18px" }}>{children}</div>
    </div>
  );
}

function Pill({ label, color = T.cyan }) {
  return (
    <span style={{
      background: color + "22",
      color,
      border: `1px solid ${color}44`,
      borderRadius: 6,
      padding: "2px 8px",
      fontSize: 11,
      fontFamily: T.fontMono,
      fontWeight: 600,
      letterSpacing: "0.05em",
      textTransform: "uppercase",
    }}>{label}</span>
  );
}

function StatusDot({ status }) {
  const map = { RUNNING: T.green, INIT: T.amber, DONE: T.cyan, IDLE: T.textMuted };
  const color = map[status] || T.textMuted;
  const label = { RUNNING: "Running", INIT: "Init", DONE: "Done", IDLE: "Idle" }[status] || "Idle";
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 6, fontFamily: T.fontMono, fontSize: 11, color }}>
      <span style={{
        width: 7, height: 7, borderRadius: "50%", background: color,
        animation: status === "RUNNING" ? "pulse-dot 1.5s ease-in-out infinite" : "none",
        boxShadow: status === "RUNNING" ? `0 0 8px ${color}` : "none",
        display: "inline-block",
      }} />
      {label}
    </span>
  );
}

function MetricCard({ label, value, unit, color = T.cyan, icon, trend }) {
  return (
    <div style={{
      background: T.card,
      border: `1px solid ${T.border}`,
      borderRadius: 10,
      padding: "14px 16px",
      position: "relative",
      overflow: "hidden",
    }}>
      <div style={{ position: "absolute", top: 0, left: 0, width: 3, height: "100%", background: color, borderRadius: "10px 0 0 10px" }} />
      <div style={{ paddingLeft: 8 }}>
        <div style={{ fontFamily: T.fontUI, fontSize: 11, color: T.textSecondary, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }}>
          {icon && <span style={{ marginRight: 6 }}>{icon}</span>}{label}
        </div>
        <div style={{ fontFamily: T.fontMono, fontSize: 22, fontWeight: 700, color, lineHeight: 1 }}>
          {value}
          {unit && <span style={{ fontSize: 12, color: T.textSecondary, marginLeft: 4 }}>{unit}</span>}
        </div>
        {trend != null && <div style={{ fontFamily: T.fontMono, fontSize: 11, color: trend >= 0 ? T.green : T.red, marginTop: 4 }}>{trend >= 0 ? "▲" : "▼"} {Math.abs(trend).toFixed(4)}</div>}
      </div>
    </div>
  );
}

/* ─── EPOCH PROGRESS BAR ───────────────────────────────── */
function EpochProgressBar({ current, max }) {
  const pct = max > 0 ? Math.min(1, current / max) : 0;
  return (
    <div style={{ padding: "10px 18px", borderBottom: `1px solid ${T.border}`, display: "flex", alignItems: "center", gap: 12 }}>
      <span style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary, whiteSpace: "nowrap" }}>EPOCH</span>
      <div style={{ flex: 1, background: T.surface, borderRadius: 4, height: 6, overflow: "hidden" }}>
        <div style={{ height: "100%", width: `${pct * 100}%`, background: `linear-gradient(90deg, ${T.cyan}, ${T.green})`, borderRadius: 4, transition: "width 0.6s ease" }} />
      </div>
      <span style={{ fontFamily: T.fontMono, fontSize: 12, color: T.textPrimary, fontWeight: 700, whiteSpace: "nowrap" }}>
        {current} / {max}
      </span>
    </div>
  );
}

/* ─── NODE STATUS GRID ──────────────────────────────────── */
function NodeStatusGrid({ nodes, nodeStats, ideNode }) {
  if (!nodes || nodes.length === 0) return <div style={{ color: T.textMuted, fontFamily: T.fontMono, fontSize: 12, textAlign: "center", padding: 16 }}>No nodes yet.</div>;
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(130px, 1fr))", gap: 8 }}>
      {nodes.map(node => {
        const stat = nodeStats.get(node);
        const isIde = node === ideNode;
        const color = isIde ? T.red : stat ? T.green : T.textMuted;
        return (
          <div key={node} style={{
            background: T.surface,
            border: `1px solid ${isIde ? T.red + "60" : stat ? T.green + "30" : T.border}`,
            borderRadius: 8,
            padding: "8px 10px",
            transition: "border-color 0.3s",
          }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
              <span style={{ fontFamily: T.fontMono, fontSize: 12, fontWeight: 700, color }}>{node}</span>
              {isIde && <Pill label="IDE" color={T.red} />}
            </div>
            {stat ? (
              <>
                <div style={{ fontFamily: T.fontMono, fontSize: 11, color: T.green }}>ACC <strong>{(stat.accuracy * 100).toFixed(2)}%</strong></div>
                <div style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary }}>LOSS {stat.loss.toFixed(4)}</div>
                <div style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textMuted }}>EP {stat.epoch}</div>
              </>
            ) : (
              <div style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textMuted }}>waiting…</div>
            )}
          </div>
        );
      })}
    </div>
  );
}

/* ─── COMM BREAKDOWN ────────────────────────────────────── */
const COMM_COLORS = {
  GRADIENT:    T.cyan,
  GOSSIP_VOTE: T.purple,
  DEPOT:       T.amber,
  GLOBAL_MODEL: T.green,
  STATE:       T.red,
};

function CommBreakdown({ counts, total }) {
  if (!total) return <div style={{ color: T.textMuted, fontFamily: T.fontMono, fontSize: 12, textAlign: "center", padding: 8 }}>No communications yet.</div>;
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      {Object.entries(counts).map(([type, count]) => {
        const pct = total ? count / total : 0;
        const color = COMM_COLORS[type] || T.textSecondary;
        return (
          <div key={type}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 3 }}>
              <span style={{ fontFamily: T.fontMono, fontSize: 11, color }}>{type}</span>
              <span style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary }}>{count} <span style={{ color: T.textMuted }}>({(pct * 100).toFixed(0)}%)</span></span>
            </div>
            <div style={{ background: T.surface, borderRadius: 3, height: 5, overflow: "hidden" }}>
              <div style={{ height: "100%", width: `${pct * 100}%`, background: color, borderRadius: 3, transition: "width 0.5s ease" }} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

/* ─── SIDEBAR ───────────────────────────────────────────── */
function Sidebar({ onStart, onStop, onClear, onClearAllTabs, connected, sessionStatus, currentEpoch, maxEpoch, speed, onSpeedChange }) {
  return (
    <aside style={{
      width: 300,
      background: T.surface,
      borderRight: `1px solid ${T.border}`,
      height: "100vh",
      position: "fixed",
      top: 0,
      left: 0,
      zIndex: 100,
      display: "flex",
      flexDirection: "column",
      fontFamily: T.fontUI,
    }}>
      {/* Logo */}
      <div style={{ padding: "18px 20px", borderBottom: `1px solid ${T.border}` }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 6 }}>
          <div style={{
            width: 32, height: 32, borderRadius: 8,
            background: `linear-gradient(135deg, ${T.cyan}, #0060ff)`,
            display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: 16,
          }}>⬡</div>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, color: T.textPrimary, letterSpacing: "0.04em" }}>PeerSim DJL</div>
            <div style={{ fontSize: 10, color: T.textSecondary, letterSpacing: "0.06em", textTransform: "uppercase" }}>Federated Learning</div>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 10 }}>
          <span style={{
            width: 8, height: 8, borderRadius: "50%",
            background: connected ? T.green : T.red,
            animation: connected ? "pulse-dot 2s ease-in-out infinite" : "none",
            boxShadow: connected ? `0 0 8px ${T.green}` : "none",
            display: "inline-block", flexShrink: 0,
          }} />
          <span style={{ fontSize: 11, fontFamily: T.fontMono, color: connected ? T.green : T.red }}>
            {connected ? "WS CONNECTED" : "WS OFFLINE"}
          </span>
          <span style={{ marginLeft: "auto" }}><StatusDot status={sessionStatus} /></span>
        </div>
      </div>

      {/* Epoch progress in sidebar */}
      <div style={{ padding: "10px 20px", borderBottom: `1px solid ${T.border}` }}>
        <div style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textSecondary, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }}>Training Progress</div>
        <div style={{ background: T.card, borderRadius: 4, height: 6, overflow: "hidden", marginBottom: 4 }}>
          <div style={{ height: "100%", width: `${maxEpoch > 0 ? Math.min(100, currentEpoch / maxEpoch * 100) : 0}%`, background: `linear-gradient(90deg, ${T.cyan}, ${T.green})`, borderRadius: 4, transition: "width 0.6s ease" }} />
        </div>
        <div style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textPrimary }}>Epoch <strong style={{ color: T.cyan }}>{currentEpoch}</strong> / {maxEpoch}</div>
      </div>

      {/* Animation speed slider */}
      <div style={{ padding: "10px 20px", borderBottom: `1px solid ${T.border}` }}>
        <div style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textSecondary, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }}>Event Playback Speed</div>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <input
            type="range"
            min={0.10}
            max={1}
            step={0.05}
            value={speed}
            onChange={e => onSpeedChange(Number(e.target.value))}
            style={{ flex: 1 }}
          />
          <div style={{ minWidth: 44, textAlign: "right", fontFamily: T.fontMono, fontSize: 12, color: T.textPrimary }}>{speed.toFixed(2)}x</div>
        </div>
      </div>

      {/* Launch form */}
      <div style={{ flex: 1, overflowY: "auto", padding: 16 }}>
        <div style={{
          background: T.card,
          border: `1px solid ${T.border}`,
          borderRadius: 10,
          overflow: "hidden",
        }}>
          <div style={{ padding: "10px 14px", borderBottom: `1px solid ${T.border}` }}>
            <span style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary, textTransform: "uppercase", letterSpacing: "0.06em" }}>Session Control</span>
          </div>
          <div style={{ padding: 12 }}>
            <LaunchForm onStart={onStart} onStop={onStop} onClear={onClear} />
          </div>
        </div>

        {/* Clear All Tabs Button - Summary tab protected */}
        <button onClick={onClearAllTabs} style={{
          width: "100%",
          background: T.card,
          border: `1px solid ${T.border}`,
          color: T.amber,
          padding: "10px",
          borderRadius: 8,
          cursor: "pointer",
          fontFamily: T.fontMono,
          fontSize: 11,
          letterSpacing: "0.04em",
          textTransform: "uppercase",
          marginTop: 12,
          transition: "all 0.2s",
        }}
          onMouseEnter={e => { e.currentTarget.style.borderColor = T.amber; e.currentTarget.style.background = T.amber + "22"; }}
          onMouseLeave={e => { e.currentTarget.style.borderColor = T.border; e.currentTarget.style.background = T.card; }}
        >
          🗑 Clear All Tabs
        </button>
        <div style={{ fontFamily: T.fontMono, fontSize: 9, color: T.textMuted, marginTop: 4, textAlign: "center" }}>Summary protected</div>
      </div>

      <div style={{ padding: "10px 20px", borderTop: `1px solid ${T.border}`, fontFamily: T.fontMono, fontSize: 10, color: T.textMuted }}>
        v1.0 · React + Spring Boot
      </div>
    </aside>
  );
}

/* ─── HEADER ────────────────────────────────────────────── */
function Header({ globalMetrics, connected, onRefresh }) {
  const { epoch, accuracy, loss, dataset } = globalMetrics;
  return (
    <header style={{
      background: T.surface,
      borderBottom: `1px solid ${T.border}`,
      padding: "0 24px",
      height: 56,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      position: "sticky",
      top: 0,
      zIndex: 50,
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 24, fontFamily: T.fontMono, fontSize: 12 }}>
        <span style={{ color: T.textSecondary }}>GLOBAL</span>
        <span>ACC <strong style={{ color: T.green }}>{accuracy != null ? (accuracy * 100).toFixed(2) + "%" : "—"}</strong></span>
        <span>LOSS <strong style={{ color: accuracy != null ? T.amber : T.textMuted }}>{loss != null ? loss.toFixed(4) : "—"}</strong></span>
        <span style={{ color: T.textMuted }}>DS <strong style={{ color: T.textSecondary }}>{dataset > 0 ? dataset.toLocaleString() : "—"}</strong></span>
        <span style={{ color: T.textMuted }}>EP <strong style={{ color: T.cyan }}>{epoch || "—"}</strong></span>
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <button onClick={onRefresh} style={{
          background: "transparent",
          border: `1px solid ${T.border}`,
          color: T.textSecondary,
          padding: "6px 14px",
          borderRadius: 7,
          cursor: "pointer",
          fontFamily: T.fontMono,
          fontSize: 11,
          letterSpacing: "0.04em",
          transition: "all 0.2s",
        }}
          onMouseEnter={e => { e.currentTarget.style.borderColor = T.cyan; e.currentTarget.style.color = T.cyan; }}
          onMouseLeave={e => { e.currentTarget.style.borderColor = T.border; e.currentTarget.style.color = T.textSecondary; }}
        >↻ REFRESH</button>
      </div>
    </header>
  );
}

/* ─── TAB BAR ───────────────────────────────────────────── */
function TabBar({ active, onChange }) {
  const tabs = ["dashboard", "nodes", "comms", "summary"];
  const labels = { dashboard: "Dashboard", nodes: "Node Grid", comms: "Communications", summary: "Summary" };
  return (
    <div style={{ display: "flex", gap: 2, padding: "0 0 0 0", marginBottom: 20 }}>
      {tabs.map(t => (
        <button key={t} onClick={() => onChange(t)} style={{
          background: active === t ? T.cyan + "18" : "transparent",
          color: active === t ? T.cyan : T.textSecondary,
          border: `1px solid ${active === t ? T.cyan + "44" : T.border}`,
          borderRadius: 8,
          padding: "7px 16px",
          cursor: "pointer",
          fontFamily: T.fontMono,
          fontSize: 11,
          letterSpacing: "0.05em",
          textTransform: "uppercase",
          transition: "all 0.2s",
        }}>{labels[t]}</button>
      ))}
    </div>
  );
}

/* ─── NETWORK PANEL ─────────────────────────────────────── */
function NetworkPanel({ nodes, ideNode, sessionNodes, communications, speed }) {
  const recentComms = communications.slice(-60);
  return (
    <div>
      <NetworkTraceGraph
        nodes={nodes}
        ideNode={ideNode}
        sessionNodes={sessionNodes}
        communications={recentComms}
        speed={speed}
      />
    </div>
  );
}

/* ─── SUMMARY TAB ───────────────────────────────────────── */
function SummaryTab({ completedSessions }) {
  const [selectedSessionId, setSelectedSessionId] = useState(null);
  const selectedSession = completedSessions.find(s => s.id === selectedSessionId);

  useEffect(() => {
    if (!selectedSessionId && completedSessions.length > 0) {
      setSelectedSessionId(completedSessions[0].id);
    }
  }, [selectedSessionId, completedSessions]);

  /* Helper to count communication types */
  const parseCommTypeCounts = (communications) => {
    const counts = {};
    for (const c of communications || []) {
      counts[c.commType] = (counts[c.commType] || 0) + 1;
    }
    return counts;
  };

  if (completedSessions.length === 0) {
    return (
      <Card title="Session History" subtitle="Completed learning sessions">
        <div style={{ color: T.textMuted, fontFamily: T.fontMono, fontSize: 12, textAlign: "center", padding: 32 }}>
          Run and complete a session to see the history here.
        </div>
      </Card>
    );
  }

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 16 }}>
      {/* Sessions List */}
      <Card title="Sessions" subtitle={`${completedSessions.length} completed`}>
        <div style={{ maxHeight: 600, overflowY: "auto" }}>
          {completedSessions.map(s => (
            <div
              key={s.id}
              onClick={() => setSelectedSessionId(s.id)}
              style={{
                background: selectedSessionId === s.id ? T.surface : "transparent",
                border: `1px solid ${selectedSessionId === s.id ? T.cyan + "44" : T.border}`,
                borderRadius: 8,
                padding: "10px 12px",
                marginBottom: 8,
                cursor: "pointer",
                transition: "all 0.2s",
              }}
              onMouseEnter={e => e.currentTarget.style.borderColor = T.cyan + "44"}
              onMouseLeave={e => e.currentTarget.style.borderColor = selectedSessionId === s.id ? T.cyan + "44" : T.border}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
                <span style={{ fontFamily: T.fontMono, fontSize: 11, fontWeight: 700, color: T.textPrimary, overflow: "hidden", textOverflow: "ellipsis" }}>{s.id}</span>
                <span style={{ fontFamily: T.fontMono, fontSize: 10, color: T.green }}>{s.duration}s</span>
              </div>
              <div style={{ display: "flex", gap: 8, marginBottom: 4, fontSize: 10 }}>
                <span style={{ color: T.cyan }}>⬡ {s.networkSize}</span>
                <span style={{ color: T.purple }}>↗ {s.communications?.length || 0}</span>
              </div>
              {s.accuracy && <div style={{ fontFamily: T.fontMono, fontSize: 10, color: T.green }}>ACC {(s.accuracy.globalAccuracy * 100).toFixed(4)}%</div>}
            </div>
          ))}
        </div>
      </Card>

      {/* Session Details */}
      {selectedSession ? (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <Card title={selectedSession.id} subtitle={selectedSession.dataset || "—"}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 12, marginBottom: 16 }}>
              <MetricCard label="Duration" value={selectedSession.duration} unit="s" color={T.cyan} />
              <MetricCard label="Nodes Used" value={selectedSession.networkSize} color={T.purple} />
              <MetricCard label="Messages" value={selectedSession.communications?.length || 0} color={T.amber} />
              <MetricCard label="Final Accuracy" value={selectedSession.accuracy ? (selectedSession.accuracy.globalAccuracy * 100).toFixed(4) + "%" : "—"} color={T.green} />
            </div>
            
            {/* Accuracy Chart */}
            {selectedSession.accuracyPoints?.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <div style={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary, textTransform: "uppercase", marginBottom: 8, letterSpacing: "0.06em" }}>Training Accuracy</div>
                <AccuracyChart accuracyPoints={selectedSession.accuracyPoints} height={200} showLegend={true} />
              </div>
            )}
          </Card>

          {/* Communications Breakdown */}
          {selectedSession.communications?.length > 0 && (
            <Card title="Communication Summary" subtitle={`${selectedSession.communications.length} messages exchanged`}>
              <CommBreakdown counts={parseCommTypeCounts(selectedSession.communications)} total={selectedSession.communications.length} />
            </Card>
          )}

          {/* Detailed Communications Log */}
          {selectedSession.communications?.length > 0 && (
            <Card title="Message Log" subtitle="All communications during this session">
              <div style={{ height: 300, overflowY: "auto", fontFamily: T.fontMono, fontSize: 11 }}>
                {selectedSession.communications.slice(-50).reverse().map((c, i) => {
                  const color = COMM_COLORS[c.commType] || T.textSecondary;
                  return (
                    <div key={i} style={{
                      display: "flex", alignItems: "center", gap: 8,
                      padding: "4px 0", borderBottom: `1px solid ${T.border}30`,
                      animation: "fadeIn 0.2s ease",
                    }}>
                      <span style={{ background: color + "22", color, border: `1px solid ${color}44`, borderRadius: 4, padding: "0px 4px", fontSize: 9, fontWeight: 700, minWidth: 70, textAlign: "center" }}>{c.commType}</span>
                      <span style={{ color: T.textPrimary, fontWeight: 700, minWidth: 60 }}>{c.source}</span>
                      <span style={{ color: T.textMuted }}>→</span>
                      <span style={{ color: T.textPrimary, minWidth: 60 }}>{c.dest}</span>
                      {c.epoch != null && <span style={{ color: T.textMuted, marginLeft: "auto", fontSize: 9 }}>E{c.epoch}C{c.cycle}</span>}
                    </div>
                  );
                })}
              </div>
            </Card>
          )}
        </div>
      ) : (
        <Card title="Session Details" subtitle="Select a session to view details">
          <div style={{ color: T.textMuted, fontFamily: T.fontMono, fontSize: 12, textAlign: "center", padding: 32 }}>
            Click on a session to view all its information.
          </div>
        </Card>
      )}
    </div>
  );
}

/* ─── COMMS TAB ─────────────────────────────────────────── */
function CommsTab({ communications, ideNode }) {
  /* Display ALL communications (not just last 100) */
  const allComms = communications.slice().reverse(); /* Reverse but keep all */
  return (
    <Card title="Communication Log" subtitle={`${communications.length} total messages`}>
      <div style={{ height: 480, overflowY: "auto", fontFamily: T.fontMono, fontSize: 12 }}>
        {allComms.length === 0 && <div style={{ color: T.textMuted, textAlign: "center", padding: 24 }}>No structured communications received yet.</div>}
        {allComms.map((c, i) => {
          const color = COMM_COLORS[c.commType] || T.textSecondary;
          return (
            <div key={c.id || i} style={{
              display: "flex", alignItems: "center", gap: 10,
              padding: "5px 0", borderBottom: `1px solid ${T.border}30`,
              animation: "fadeIn 0.2s ease",
            }}>
              <span style={{ color: T.textMuted, minWidth: 70, fontSize: 10 }}>{c.time}</span>
              <span style={{ background: color + "22", color, border: `1px solid ${color}44`, borderRadius: 4, padding: "1px 6px", fontSize: 10, fontWeight: 700, whiteSpace: "nowrap", minWidth: 90, textAlign: "center" }}>{c.commType}</span>
              <span style={{ color: T.textPrimary, fontWeight: 700 }}>{c.source}</span>
              <span style={{ color: T.textMuted }}>→</span>
              <span style={{ color: c.dest === ideNode ? T.red : T.textPrimary }}>{c.dest}</span>
              {c.epoch != null && <span style={{ color: T.textMuted, marginLeft: "auto", fontSize: 10 }}>E{c.epoch}C{c.cycle}</span>}
              {c.param && <span style={{ color: T.purple, fontSize: 10 }}>{c.param}</span>}
              {c.voteCount && <span style={{ color: T.amber, fontSize: 10 }}>{c.voteCount}</span>}
            </div>
          );
        })}
      </div>
    </Card>
  );
}

/* ─── MAIN APP ──────────────────────────────────────────── */
export default function App() {
  useFonts();

  const { events, communications: wsCommunications, connected, clearEvents, clearCommunications } = useWebSocket(
    "ws://localhost:8080/ws",
    "http://localhost:8080/api/simulations/events?limit=2000"
  );

  const [networkSize, setNetworkSize]       = useState(0);
  const [selectedTab, setSelectedTab]       = useState("dashboard");
  const [lastNetworkStats, setLastNetworkStats] = useState({ activeNodes: 0, nodes: [], ideNode: null, lastUpdated: null });
  const [lastAccuracyPoints, setLastAccuracyPoints] = useState([]);
  const [lastComms, setLastComms]           = useState([]);
  const [accuracyViewMode, setAccuracyViewMode] = useState("combined");
  const [eventSpeed, setEventSpeed]         = useState(1);
  const [completedSessions, setCompletedSessions] = useState(() => {
    try {
      const raw = localStorage.getItem("peersim.completedSessions");
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  });
  const [currentSessionStart, setCurrentSessionStart] = useState(null);
  
  /* ---- Refs to store current values for useEffect access ---- */
  const currentDataRef = useRef({});
  const previousStatusRef = useRef("IDLE");

  /* ---- derived state ---- */
  const filteredEvents = useMemo(() => events || [], [events]);

  const accuracyPoints  = useMemo(() => parseAccuracy(filteredEvents, wsCommunications),       [filteredEvents, wsCommunications]);
  const nodeStats       = useMemo(() => parseNodeStats(filteredEvents),       [filteredEvents]);
  const globalMetrics   = useMemo(() => parseGlobalMetrics(filteredEvents),   [filteredEvents]);
  const epochProgress   = useMemo(() => parseEpochProgress(filteredEvents),   [filteredEvents]);
  const sessionStats    = useMemo(() => parseSessionStats(filteredEvents),     [filteredEvents]);
  const networkStats    = useMemo(() => parseNetworkStats(filteredEvents),     [filteredEvents]);
  const sessions        = useMemo(() => parseSessions(filteredEvents),         [filteredEvents]);
  const communications = useMemo(() => {
    const fromEvents = parseCommunications(filteredEvents);
    if (!wsCommunications || wsCommunications.length === 0) return fromEvents;
    const merged = new Map();
    for (const c of fromEvents) merged.set(c.id, c);
    for (const c of wsCommunications) {
      const id = c.id || `${c.ts || c.timestamp || ""}-${c.type || c.commType || "COMM"}-${c.source || c.from || "?"}-${c.dest || c.to || "?"}-${c.epoch ?? ""}-${c.cycle ?? ""}`;
      merged.set(id, {
        id,
        time: c.timestamp || c.time || "",
        source: c.source || c.from,
        dest: c.dest || c.to,
        commType: c.commType || c.type,
        epoch: c.epoch,
        cycle: c.cycle,
        detail: c.detail || "",
        value: c.value,
        param: c.param,
        voteCount: c.voteCount,
      });
    }
    return [...merged.values()];
  }, [filteredEvents, wsCommunications]);
  const commCounts      = useMemo(() => parseCommTypeCounts(communications),   [communications]);
  const paramEvolution  = useMemo(() => parseParamEvolution(filteredEvents),   [filteredEvents]);
  const learningEvents  = useMemo(() => (filteredEvents || []).filter(e => isLearningLog(e?.message)), [filteredEvents]);
  const displayedAccuracyPoints = useMemo(
    () => mergeAccuracySeries(lastAccuracyPoints, accuracyPoints),
    [lastAccuracyPoints, accuracyPoints]
  );

  const allNodes = useMemo(() => {
    if (!networkSize || networkSize < 1) return networkStats.nodes;
    return Array.from({ length: networkSize }, (_, i) => `N${i}`);
  }, [networkSize, networkStats.nodes]);

  /* ---- persist last-good values ---- */
  useEffect(() => { if (networkStats.nodes.length > 0) setLastNetworkStats(networkStats); }, [networkStats]);
  useEffect(() => {
    if (accuracyPoints.length === 0) return;
    setLastAccuracyPoints(prev => mergeAccuracySeries(prev, accuracyPoints));
  }, [accuracyPoints]);
  useEffect(() => {
    if (!communications.length) return;
    setLastComms(prev => {
      const merged = new Map();
      for (const c of prev) merged.set(c.id, c);
      for (const c of communications) merged.set(c.id, c);
      const arr = [...merged.values()];
      return arr.length > 2000 ? arr.slice(arr.length - 2000) : arr;
    });
  }, [communications]);

  /* ---- Update ref with latest data ---- */
  useEffect(() => {
    currentDataRef.current = {
      sessionStats,
      currentSessionStart,
      lastComms,
      lastAccuracyPoints,
      accuracyPoints,
      epochProgress,
      globalMetrics,
      nodeStats,
      lastNetworkStats,
      sessions,
      completedSessions,
      displayedAccuracyPoints,
    };
  }, [sessionStats, currentSessionStart, lastComms, lastAccuracyPoints, accuracyPoints, epochProgress, globalMetrics, nodeStats, lastNetworkStats, sessions, completedSessions, displayedAccuracyPoints]);

  useEffect(() => {
    try {
      localStorage.setItem("peersim.completedSessions", JSON.stringify(completedSessions));
    } catch {
    }
  }, [completedSessions]);

  const archiveSessionSnapshot = useCallback((reason = "auto") => {
    const data = currentDataRef.current;
    if (!data?.currentSessionStart) return;

    const lastSession = data.sessions?.length > 0 ? data.sessions[data.sessions.length - 1] : null;
    const snapshotId =
      lastSession?.id ||
      data.sessionStats?.currentSessionId ||
      `${reason}-${Date.now()}`;

    setCompletedSessions(prev => {
      if (prev.some(s => s.id === snapshotId)) return prev;
      const mergedAccuracy = mergeAccuracySeries(data.lastAccuracyPoints, data.accuracyPoints);
      const accuracySeries = mergedAccuracy.length > 0 ? mergedAccuracy : (data.displayedAccuracyPoints || []);
      const snapshot = {
        id: snapshotId,
        dataset: lastSession?.dataset || "Session",
        status: "DONE",
        createdAt: lastSession?.createdAt || data.currentSessionStart?.toISOString?.() || new Date().toISOString(),
        lastUpdated: new Date().toISOString(),
        samples: lastSession?.samples || 0,
        nodesUsed: data.lastNetworkStats?.nodes?.length || 0,
        accuracy: accuracySeries.length > 0 ? accuracySeries[accuracySeries.length - 1] : null,
        communications: [...(data.lastComms || [])],
        accuracyPoints: [...accuracySeries],
        duration: Math.max(1, Math.round((new Date() - data.currentSessionStart) / 1000)),
        epochs: data.epochProgress,
        globalMetrics: data.globalMetrics,
        nodeStats: Object.fromEntries(data.nodeStats || []),
        networkSize: data.lastNetworkStats?.nodes?.length || 0,
      };
      return [snapshot, ...prev];
    });
  }, []);

  useEffect(() => {
    const prev = previousStatusRef.current;
    const curr = sessionStats.currentStatus;
    if (prev !== "DONE" && curr === "DONE") {
      archiveSessionSnapshot("done");
      setCurrentSessionStart(null);
    }
    previousStatusRef.current = curr;
  }, [sessionStats.currentStatus, archiveSessionSnapshot]);

  /* ---- handlers ---- */
  const handleStart = useCallback(async (payload) => {
    try {
      setLastAccuracyPoints([]);
      /* Note: Do NOT clear lastComms - keep communications from previous session */
      setCurrentSessionStart(new Date());
      if (payload?.sessionConfigs?.[0]?.networkSize) setNetworkSize(payload.sessionConfigs[0].networkSize);
      if (!payload?.formData || !payload?.sessionConfigs) return;
      for (const config of payload.sessionConfigs) {
        const fd = new FormData();
        for (const [k, v] of payload.formData.entries()) { if (k !== "config") fd.append(k, v); }
        fd.append("config", JSON.stringify(config));
        const resp = await fetch("http://localhost:8080/api/simulations/start", { method: "POST", body: fd });
        if (!resp.ok) { const d = await resp.json(); alert(d.error || "Failed to start"); break; }
      }
    } catch (e) { alert(e.message); }
  }, []);

  const handleStop = useCallback(async () => {
    try {
      const r = await fetch("http://localhost:8080/api/simulations/stop", { method: "POST" });
      if (!r.ok) { const d = await r.json(); alert(d.error || "Failed to stop"); }
      archiveSessionSnapshot("stop");
      
      /* Clear current session marker only (keep chart history until explicit clear) */
      setCurrentSessionStart(null);
    } catch (e) { alert(e.message); }
  }, [archiveSessionSnapshot]);

  const handleClear = useCallback(() => {
    setNetworkSize(0);
    setLastNetworkStats({ activeNodes: 0, nodes: [], ideNode: null, lastUpdated: null });
    setLastAccuracyPoints([]);
    setLastComms([]);
    setCurrentSessionStart(null);
  }, []);

  const handleClearAllTabs = useCallback(() => {
    /* Clear ONLY Dashboard, Communications, and Events tabs. Summary tab is PROTECTED */
    setNetworkSize(0);
    setLastNetworkStats({ activeNodes: 0, nodes: [], ideNode: null, lastUpdated: null });
    setLastAccuracyPoints([]);
    setLastComms([]);
    setCurrentSessionStart(null);
    clearEvents();          // ← NEW: Clear Event Log
    clearCommunications();  // ← NEW: Clear Message Types
    /* Note: completedSessions stays intact for Summary tab */
  }, [clearEvents, clearCommunications]);

  const effectiveNodes = allNodes.length > 0 ? allNodes : lastNetworkStats.nodes;
  const ideNode = lastNetworkStats.ideNode;
  const sessionNodes = lastNetworkStats.nodes;
  const commTotal = Object.values(commCounts).reduce((a, b) => a + b, 0);

  return (
    <div style={{ display: "flex", minHeight: "100vh", background: T.bg, fontFamily: T.fontUI }}>
      <Sidebar
        onStart={handleStart}
        onStop={handleStop}
        onClear={handleClear}
        onClearAllTabs={handleClearAllTabs}
        connected={connected}
        sessionStatus={sessionStats.currentStatus}
        currentEpoch={epochProgress.currentEpoch}
        maxEpoch={epochProgress.maxEpoch}
        speed={eventSpeed}
        onSpeedChange={setEventSpeed}
      />

      <div style={{ flex: 1, marginLeft: 300, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <Header
          globalMetrics={globalMetrics}
          connected={connected}
          onRefresh={() => setLastComms(prev => [...prev])}
        />

        <main style={{ flex: 1, padding: "24px", maxWidth: 1500, margin: "0 auto", width: "100%" }}>
          <TabBar active={selectedTab} onChange={setSelectedTab} />

          {/* ── DASHBOARD TAB ── */}
          {selectedTab === "dashboard" && (
            <>
              {/* Stat Row */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 12, marginBottom: 20 }}>
                <MetricCard label="Global Acc" value={globalMetrics.accuracy != null ? (globalMetrics.accuracy * 100).toFixed(2) + "%" : "—"} color={T.green} icon="◎" />
                <MetricCard label="Global Loss" value={globalMetrics.loss != null ? globalMetrics.loss.toFixed(4) : "—"} color={T.amber} icon="△" />
                <MetricCard label="Active Nodes" value={lastNetworkStats.activeNodes || effectiveNodes.length} color={T.cyan} icon="⬡" />
                <MetricCard label="Messages" value={commTotal || communications.length} color={T.purple} icon="↗" />
                <MetricCard label="Events" value={learningEvents.length} color={T.textSecondary} icon="▣" />
              </div>

              {/* Main Grid */}
              <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1fr", gap: 16, marginBottom: 16 }}>
                {/* Network topology */}
                <Card title="Network Topology" subtitle={`${effectiveNodes.length} nodes · IDE: ${ideNode || "—"}`}
                  action={lastNetworkStats.lastUpdated && <span style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textMuted }}>{lastNetworkStats.lastUpdated}</span>}
                >
                  <NetworkPanel
                    nodes={effectiveNodes}
                    ideNode={ideNode}
                    sessionNodes={sessionNodes}
                    communications={lastComms}
                    speed={eventSpeed}
                  />
                </Card>

                {/* Accuracy chart */}
                <Card title="Accuracy" subtitle="Local vs Global per epoch">
                  <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginBottom: 8 }}>
                    <label style={{ fontFamily: T.fontMono, color: T.textMuted, fontSize: 12, alignSelf: "center" }}>View:</label>
                    <select value={accuracyViewMode} onChange={e => setAccuracyViewMode(e.target.value)} style={{ fontFamily: T.fontMono, fontSize: 12 }}>
                      <option value="combined">Local (train) + Global</option>
                      <option value="global-only">Global only</option>
                      <option value="local-on-global">Local (evaluated on global dataset)</option>
                    </select>
                  </div>
                  <AccuracyChart
                    events={learningEvents}
                    accuracyPoints={displayedAccuracyPoints}
                    viewMode={accuracyViewMode}
                    height={280}
                    showLegend={true}
                  />
                </Card>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16, marginBottom: 16 }}>
                {/* Param heatmap */}
                <Card title="Parameter Heatmap" subtitle="Aggregated depot values per epoch">
                  <ParamHeatmap data={paramEvolution} />
                </Card>

                {/* Communication breakdown */}
                <Card title="Message Types" subtitle={`${commTotal} total`}>
                  <CommBreakdown counts={commCounts} total={commTotal} />
                </Card>

                {/* Sessions */}
                <Card title="Sessions" subtitle={`${sessions.length} session(s)`}>
                  {sessions.length === 0 && <div style={{ color: T.textMuted, fontFamily: T.fontMono, fontSize: 12, textAlign: "center", padding: 16 }}>No sessions yet.</div>}
                  {sessions.map(s => (
                    <div key={s.id} style={{ background: T.surface, border: `1px solid ${T.border}`, borderRadius: 8, padding: "10px 12px", marginBottom: 8 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <span style={{ fontFamily: T.fontMono, fontSize: 12, fontWeight: 700, color: T.textPrimary }}>{s.id}</span>
                        <StatusDot status={s.status} />
                      </div>
                      {s.dataset && <div style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textMuted, marginTop: 4, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{s.dataset.split(/[\\/]/).pop()}</div>}
                      {s.nodesUsed && <div style={{ fontFamily: T.fontMono, fontSize: 10, color: T.cyan, marginTop: 2 }}>{s.nodesUsed} nodes</div>}
                    </div>
                  ))}
                </Card>
              </div>

              {/* Event Feed */}
              <Card title="Event Log" subtitle={`Showing last ${Math.min(500, learningEvents.length)} events`}>
                <EventFeed events={filteredEvents} />
              </Card>
            </>
          )}

          {/* ── NODE GRID TAB ── */}
          {selectedTab === "nodes" && (
            <Card title="Node Status" subtitle={`${effectiveNodes.length} nodes in network`}>
              <NodeStatusGrid nodes={effectiveNodes} nodeStats={nodeStats} ideNode={ideNode} />
            </Card>
          )}

          {/* ── COMMS TAB ── */}
          {selectedTab === "comms" && (
            <CommsTab communications={lastComms} ideNode={ideNode} />
          )}

          {/* ── SUMMARY TAB ── */}
          {selectedTab === "summary" && (
            <SummaryTab completedSessions={completedSessions} />
          )}
        </main>
      </div>
    </div>
  );
}
