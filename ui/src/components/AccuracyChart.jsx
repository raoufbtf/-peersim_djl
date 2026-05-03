import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Area, AreaChart, ReferenceLine } from "recharts";

const T = {
  bg: "#070c18", card: "#111d35", border: "#1e2d4a",
  cyan: "#00c8ff", green: "#10d98a", textPrimary: "#e2eaf8", textSecondary: "#6b82a8",
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
      {payload.map(p => (
        <div key={p.dataKey} style={{ color: p.color, display: "flex", justifyContent: "space-between", gap: 16 }}>
          <span>{p.name}</span>
          <strong>{(p.value * 100).toFixed(2)}%</strong>
        </div>
      ))}
    </div>
  );
}

export default function AccuracyChart({ events, accuracyPoints, height = 280, showLegend = true }) {
  const fromEvents = (events || [])
    .filter(e => e.type === "ACCURACY" && e.payload)
    .map(e => ({ epoch: e.payload.epoch, localAccuracy: e.payload.localAccuracy, globalAccuracy: e.payload.globalAccuracy }))
    .sort((a, b) => a.epoch - b.epoch);

  const data = Array.isArray(accuracyPoints) && accuracyPoints.length > 0 ? accuracyPoints : fromEvents;

  if (data.length === 0) {
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
        <AreaChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: -10 }}>
          <defs>
            <linearGradient id="localGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={T.cyan} stopOpacity={0.15} />
              <stop offset="95%" stopColor={T.cyan} stopOpacity={0} />
            </linearGradient>
            <linearGradient id="globalGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={T.green} stopOpacity={0.15} />
              <stop offset="95%" stopColor={T.green} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke={T.border} />
          <XAxis
            dataKey="epoch"
            stroke={T.textSecondary}
            tick={{ fontFamily: T.fontMono, fontSize: 10, fill: T.textSecondary }}
            label={{ value: "Epoch", position: "insideBottomRight", offset: -5, fill: T.textSecondary, fontFamily: T.fontMono, fontSize: 10 }}
          />
          <YAxis
            domain={[0.5, 1]}
            stroke={T.textSecondary}
            tick={{ fontFamily: T.fontMono, fontSize: 10, fill: T.textSecondary }}
            tickFormatter={v => `${(v * 100).toFixed(0)}%`}
          />
          <Tooltip content={<CustomTooltip />} />
          {showLegend && (
            <Legend
              wrapperStyle={{ fontFamily: T.fontMono, fontSize: 11, color: T.textSecondary }}
            />
          )}
          <Area type="monotone" dataKey="localAccuracy" name="Local Acc" stroke={T.cyan} fill="url(#localGrad)" strokeWidth={2} dot={false} activeDot={{ r: 5, fill: T.cyan }} />
          <Area type="monotone" dataKey="globalAccuracy" name="Global Acc" stroke={T.green} fill="url(#globalGrad)" strokeWidth={2} dot={{ r: 3, fill: T.green, strokeWidth: 0 }} activeDot={{ r: 5, fill: T.green }} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
