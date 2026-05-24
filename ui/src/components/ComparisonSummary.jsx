import React, { useMemo } from "react";

export default function ComparisonSummary({ points = [], compact = false }) {
  const formatted = useMemo(() => {
    const rows = (points || []).map(p => ({
      epoch: p.epoch,
      localAcc: p.localAccuracy != null ? (p.localAccuracy * 100) : (p.localOnGlobal != null ? (p.localOnGlobal * 100) : null),
      globalAcc: p.globalAccuracy != null ? (p.globalAccuracy * 100) : null,
      epochTimeMs: p.epochTimeMs != null ? Number(p.epochTimeMs) : null,
    }));
    const totalTime = rows.reduce((s, r) => s + (r.epochTimeMs || 0), 0);
    const counted = rows.filter(r => r.epochTimeMs != null).length;
    const avgTime = counted ? Math.round(totalTime / counted) : null;
    const final = rows.length ? rows[rows.length - 1] : null;
    return { rows, totalTime, avgTime, final };
  }, [points]);

  const fmtMs = (ms) => {
    if (ms == null) return "—";
    if (ms < 1000) return ms + " ms";
    const s = (ms / 1000);
    return s.toFixed(2) + " s";
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <div style={{ display: "flex", gap: 12 }}>
        <div style={{ padding: 12, background: "#0d1526", border: "1px solid #1e2d4a", borderRadius: 8, minWidth: 140 }}>
          <div style={{ fontSize: 11, color: "#6b82a8", fontFamily: "'JetBrains Mono', monospace" }}>Total time</div>
          <div style={{ fontSize: 16, color: "#10d98a", fontFamily: "'JetBrains Mono', monospace", fontWeight: 700 }}>{formatted.totalTime ? fmtMs(formatted.totalTime) : "—"}</div>
        </div>
        <div style={{ padding: 12, background: "#0d1526", border: "1px solid #1e2d4a", borderRadius: 8, minWidth: 140 }}>
          <div style={{ fontSize: 11, color: "#6b82a8", fontFamily: "'JetBrains Mono', monospace" }}>Avg epoch</div>
          <div style={{ fontSize: 16, color: "#00c8ff", fontFamily: "'JetBrains Mono', monospace", fontWeight: 700 }}>{formatted.avgTime ? fmtMs(formatted.avgTime) : "—"}</div>
        </div>
        <div style={{ padding: 12, background: "#0d1526", border: "1px solid #1e2d4a", borderRadius: 8, minWidth: 140 }}>
          <div style={{ fontSize: 11, color: "#6b82a8", fontFamily: "'JetBrains Mono', monospace" }}>Final global acc</div>
          <div style={{ fontSize: 16, color: "#10d98a", fontFamily: "'JetBrains Mono', monospace", fontWeight: 700 }}>{formatted.final && formatted.final.globalAcc != null ? formatted.final.globalAcc.toFixed(2) + "%" : "—"}</div>
        </div>
        <div style={{ padding: 12, background: "#0d1526", border: "1px solid #1e2d4a", borderRadius: 8, minWidth: 140 }}>
          <div style={{ fontSize: 11, color: "#6b82a8", fontFamily: "'JetBrains Mono', monospace" }}>Final local acc</div>
          <div style={{ fontSize: 16, color: "#a78bfa", fontFamily: "'JetBrains Mono', monospace", fontWeight: 700 }}>{formatted.final && formatted.final.localAcc != null ? formatted.final.localAcc.toFixed(2) + "%" : "—"}</div>
        </div>
      </div>

      <div style={{ maxHeight: compact ? 160 : 280, overflowY: "auto", border: "1px solid #1e2d4a", borderRadius: 8 }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
          <thead style={{ position: "sticky", top: 0, background: "#0d1526", zIndex: 1 }}>
            <tr>
              <th style={{ textAlign: "left", padding: 8, color: "#6b82a8" }}>Epoch</th>
              <th style={{ textAlign: "right", padding: 8, color: "#6b82a8" }}>Local</th>
              <th style={{ textAlign: "right", padding: 8, color: "#6b82a8" }}>Global</th>
              <th style={{ textAlign: "right", padding: 8, color: "#6b82a8" }}>Epoch time</th>
            </tr>
          </thead>
          <tbody>
            {formatted.rows.map((r) => (
              <tr key={r.epoch} style={{ borderTop: "1px solid #162033" }}>
                <td style={{ padding: 8, color: "#e2eaf8" }}>{r.epoch}</td>
                <td style={{ padding: 8, textAlign: "right", color: r.localAcc != null ? "#a78bfa" : "#6b82a8" }}>{r.localAcc != null ? r.localAcc.toFixed(2) + "%" : "—"}</td>
                <td style={{ padding: 8, textAlign: "right", color: r.globalAcc != null ? "#10d98a" : "#6b82a8" }}>{r.globalAcc != null ? r.globalAcc.toFixed(2) + "%" : "—"}</td>
                <td style={{ padding: 8, textAlign: "right", color: "#00c8ff" }}>{fmtMs(r.epochTimeMs)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
