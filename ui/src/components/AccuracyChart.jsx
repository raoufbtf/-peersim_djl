import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";


const T = {
  bg: "#070c18", card: "#111d35", border: "#1e2d4a",
  cyan: "#00c8ff", green: "#10d98a", amber: "#f5a623", textPrimary: "#e2eaf8", textSecondary: "#6b82a8",
  fontMono: "'JetBrains Mono', 'Fira Code', monospace",
};

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: T.card, border: `1px solid ${T.border}`,
      borderRadius: 8, padding: "10px 14px", fontFamily: T.fontMono, fontSize: 12,
    }}>
      <div style={{ color: T.textSecondary, marginBottom: 6 }}>Epoch {label}</div>
      {payload.map(p => {
        const raw = p.value;
        const val = (raw == null || Number.isNaN(raw)) ? null : (Number(raw) > 1 ? Number(raw) / 100 : Number(raw));
        const metricLabel = p.dataKey === "localAccuracy"
          ? "Local accuracy"
          : p.dataKey === "displayLocalAccuracy"
            ? "Local accuracy"
            : p.dataKey === "localOnGlobal"
              ? "Local-on-global accuracy"
              : p.dataKey === "displayGlobalAccuracy"
                ? "Global accuracy"
                : p.dataKey === "globalAccuracy"
                  ? "Global accuracy"
                  : p.name;
        return (
          <div key={p.dataKey} style={{ color: p.color, display: "flex", justifyContent: "space-between", gap: 16 }}>
            <span>{metricLabel}</span>
            <strong>{val == null ? "—" : `${(val * 100).toFixed(4)}%`}</strong>
          </div>
        );
      })}
    </div>
  );
}

export default function AccuracyChart({ events, accuracyPoints, height = 280, showLegend = true, viewMode = "local-only" }) {
  const fromEvents = (events || [])
    .filter(e => e.type === "ACCURACY" && e.payload)
    .map(e => ({ epoch: e.payload.epoch, localAccuracy: e.payload.localAccuracy, globalAccuracy: e.payload.globalAccuracy }))
    .sort((a, b) => a.epoch - b.epoch);

  const data = Array.isArray(accuracyPoints) && accuracyPoints.length > 0 ? accuracyPoints : fromEvents;
  const displayedData = data.map(point => ({
    ...point,
    displayLocalAccuracy: point.localAccuracy != null ? point.localAccuracy : point.localOnGlobal,
    displayGlobalAccuracy: point.globalAccuracy,
    displayEpoch: Number.isFinite(Number(point.epoch)) ? Number(point.epoch) + 1 : point.epoch,
  }));

  if (displayedData.length === 0) {
    return (
      <div style={{ height, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 8 }}>
        <div style={{ color: "#1e2d4a", fontSize: 32 }}>◎</div>
        <div style={{ color: T.textSecondary, fontFamily: T.fontMono, fontSize: 12 }}>No accuracy data yet</div>
      </div>
    );
  }

  return (
    <div style={{ width: "100%", height }}>
      <ResponsiveContainer>
        <LineChart data={displayedData} margin={{ top: 8, right: 16, bottom: 8, left: -10 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={T.border} />
          <XAxis
            dataKey="displayEpoch"
            stroke={T.textSecondary}
            allowDecimals={false}
            tick={{ fontFamily: T.fontMono, fontSize: 10, fill: T.textSecondary }}
            label={{ value: "Epoch", position: "insideBottomRight", offset: -5, fill: T.textSecondary, fontFamily: T.fontMono, fontSize: 10 }}
          />
          <YAxis
            domain={[0, 1]}
            stroke={T.textSecondary}
            tick={{ fontFamily: T.fontMono, fontSize: 10, fill: T.textSecondary }}
            tickFormatter={v => {
              const val = (v == null || Number.isNaN(v)) ? 0 : (Number(v) > 1 ? Number(v) / 100 : Number(v));
              return `${(val * 100).toFixed(0)}%`;
            }}
          />
          <Tooltip content={<CustomTooltip />} />
          {showLegend && (
            <Legend
              wrapperStyle={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary }}
            />
          )}
          {viewMode === "combined" && (
            <>
              <Line type="linear" dataKey="displayLocalAccuracy" name="Local accuracy" stroke={T.cyan} strokeWidth={2} dot={{ r: 3, fill: T.cyan, strokeWidth: 0 }} activeDot={{ r: 5, fill: T.cyan }} connectNulls={false} />
              <Line type="linear" dataKey="displayGlobalAccuracy" name="Global accuracy" stroke={T.green} strokeWidth={2} dot={{ r: 3, fill: T.green, strokeWidth: 0 }} activeDot={{ r: 5, fill: T.green }} connectNulls={false} />
            </>
          )}
          {viewMode === "local-only" && (
            <>
              <Line type="linear" dataKey="displayLocalAccuracy" name="Local accuracy" stroke={T.cyan} strokeWidth={2} dot={{ r: 3, fill: T.cyan, strokeWidth: 0 }} activeDot={{ r: 5, fill: T.cyan }} connectNulls={false} />
            </>
          )}
          {viewMode === "global-only" && (
            <Line type="linear" dataKey="displayGlobalAccuracy" name="Global accuracy" stroke={T.green} strokeWidth={2} dot={{ r: 3, fill: T.green, strokeWidth: 0 }} activeDot={{ r: 5, fill: T.green }} connectNulls={false} />
          )}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
