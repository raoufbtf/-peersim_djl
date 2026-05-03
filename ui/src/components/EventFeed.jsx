import { useRef, useEffect } from "react";

const T = {
  surface: "#0d1526", card: "#111d35", border: "#1e2d4a",
  cyan: "#00c8ff", green: "#10d98a", amber: "#f5a623", red: "#f43f5e", purple: "#a78bfa",
  textPrimary: "#e2eaf8", textSecondary: "#6b82a8", textMuted: "#3d5070",
  fontMono: "'JetBrains Mono', 'Fira Code', 'Courier New', monospace",
};

const LEVEL_STYLE = {
  INFO:  { bg: T.cyan + "22",   color: T.cyan,   label: "INFO" },
  WARN:  { bg: T.amber + "22",  color: T.amber,  label: "WARN" },
  ERROR: { bg: T.red + "22",    color: T.red,    label: "ERR" },
  DEBUG: { bg: T.purple + "22", color: T.purple, label: "DBG" },
};

const TYPE_COLORS = {
  GRADIENT:    T.cyan,
  GOSSIP_VOTE: T.purple,
  DEPOT:       T.amber,
  GLOBAL_MODEL: T.green,
  STATE:       T.red,
  ACCURACY:    T.green,
  SIM_LOG:     T.textSecondary,
};

export default function EventFeed({ events }) {
  const scrollRef = useRef(null);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = 0;
  }, [events.length]);

  const displayed = (events || []).slice(-500).slice().reverse();

  return (
    <div ref={scrollRef} style={{
      height: 360,
      overflowY: "auto",
      background: T.surface,
      border: `1px solid ${T.border}`,
      borderRadius: 8,
      fontFamily: T.fontMono,
      fontSize: 12,
    }}>
      {/* Header line */}
      <div style={{
        position: "sticky", top: 0,
        background: T.card,
        borderBottom: `1px solid ${T.border}`,
        padding: "6px 12px",
        display: "flex", gap: 16,
        fontSize: 10, color: T.textMuted,
        textTransform: "uppercase", letterSpacing: "0.06em",
      }}>
        <span style={{ minWidth: 70 }}>Time</span>
        <span style={{ minWidth: 48 }}>Level</span>
        <span style={{ minWidth: 90 }}>Type</span>
        <span>Message</span>
      </div>

      {displayed.length === 0 && (
        <div style={{ color: T.textMuted, textAlign: "center", padding: 24 }}>
          <div style={{ fontSize: 24, marginBottom: 8 }}>▣</div>
          Waiting for events…
        </div>
      )}

      {displayed.map((evt, idx) => {
        const ts = evt.timestamp != null
          ? new Date(evt.timestamp).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false })
          : "--:--:--";
        const lvl = LEVEL_STYLE[evt.level] || LEVEL_STYLE.INFO;
        const typeColor = TYPE_COLORS[evt.type] || T.textSecondary;

        return (
          <div key={evt.timestamp ? evt.timestamp + "-" + idx : idx} style={{
            display: "flex",
            alignItems: "flex-start",
            gap: 10,
            padding: "5px 12px",
            borderBottom: `1px solid ${T.border}20`,
            lineHeight: "1.5",
          }}
            onMouseEnter={e => { e.currentTarget.style.background = "#ffffff06"; }}
            onMouseLeave={e => { e.currentTarget.style.background = "transparent"; }}
          >
            <span style={{ color: T.textMuted, whiteSpace: "nowrap", minWidth: 70, fontSize: 10, paddingTop: 1 }}>{ts}</span>

            <span style={{
              background: lvl.bg, color: lvl.color,
              borderRadius: 4, padding: "1px 5px",
              fontSize: 10, fontWeight: 700,
              minWidth: 38, textAlign: "center",
              flexShrink: 0,
            }}>{lvl.label}</span>

            <span style={{
              color: typeColor,
              fontWeight: 600,
              minWidth: 90,
              fontSize: 10,
              paddingTop: 1,
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
              flexShrink: 0,
            }} title={evt.type}>{evt.type || "—"}</span>

            <span style={{ color: T.textPrimary, flex: 1, wordBreak: "break-word", fontSize: 11 }}>
              {evt.message || "—"}
            </span>
          </div>
        );
      })}
    </div>
  );
}
