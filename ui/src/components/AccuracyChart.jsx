import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Area } from "recharts";

  export default function AccuracyChart({ events, accuracyPoints, height = 320, showLegend = true }) {
    const fromEvents = (events || [])
      .filter((e) => e.type === "ACCURACY" && e.payload)
      .map((e) => ({
        epoch: e.payload.epoch,
        localAccuracy: e.payload.localAccuracy,
        globalAccuracy: e.payload.globalAccuracy,
      }))
      .sort((a, b) => a.epoch - b.epoch);

    const data = Array.isArray(accuracyPoints) && accuracyPoints.length > 0 ? accuracyPoints : fromEvents;

    if (data.length === 0) {
      return (
        <div style={{ padding: "16px", color: "#9ca3af", textAlign: "center" }}>
          No accuracy data yet.
        </div>
      );
    }

    return (
      <div style={{ width: "100%", height }}>
        <ResponsiveContainer>
          <LineChart data={data} margin={{ top: 12, right: 20, bottom: 12, left: 12 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis
              dataKey="epoch"
              label={{ value: "Epoch", position: "insideBottomRight", offset: -5 }}
              stroke="#6b7280"
            />
            <YAxis
              domain={[0, 1]}
              label={{ value: "Accuracy", angle: -90, position: "insideLeft" }}
              stroke="#6b7280"
            />
            <Tooltip />
            {showLegend && <Legend />}
            <Area type="monotone" dataKey="globalAccuracy" stroke="none" fill="#DCFCE7" fillOpacity={0.8} />
            <Line
              type="monotone"
              dataKey="localAccuracy"
              name="Local Accuracy"
              stroke="#2563eb"
              dot={{ r: 2 }}
              strokeWidth={2}
            />
            <Line
              type="monotone"
              dataKey="globalAccuracy"
              name="Global Accuracy"
              stroke="#16a34a"
              dot={{ r: 3 }}
              strokeWidth={2}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    );
  }