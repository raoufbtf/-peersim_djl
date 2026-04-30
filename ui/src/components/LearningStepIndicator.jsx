import React, { useMemo } from "react";

const STEPS = [
  { key: "INIT", label: "Initialization", keywords: ["initialisation", "session créée"] },
  { key: "DISTRIBUTE", label: "Gradient Distribution", keywords: ["gradient", "param", "put gradient"] },
  { key: "AGGREGATE", label: "Aggregation", keywords: ["aggregation", "aggregate", "fedavg"] },
  { key: "UPDATE", label: "Model Update", keywords: ["global", "update", "fusion"] },
  { key: "VOTE", label: "Convergence Vote", keywords: ["vote", "convergence", "accuracy"] },
  { key: "DONE", label: "Completed", keywords: ["done", "terminé", "finished"] },
];

export default function LearningStepIndicator({ events, currentEpoch }) {
  const currentStep = useMemo(() => {
    if (!events || events.length === 0) return 0;
    // Scan from most recent event backwards
    const reversed = [...events].reverse();
    for (const evt of reversed) {
      const msg = evt?.message || "";
      for (let i = STEPS.length - 1; i >= 0; i--) {
        const step = STEPS[i];
        if (step.keywords.some(kw => msg.toLowerCase().includes(kw))) {
          return i;
        }
      }
    }
    return 0;
  }, [events]);

  return (
    <div style={{ marginBottom: 20, padding: "12px 16px", backgroundColor: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)" }}>
      <div style={{ fontSize: "0.85rem", color: "#6B7280", marginBottom: 8 }}>Learning Progress</div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, overflowX: "auto" }}>
        {STEPS.map((step, idx) => {
          const isActive = idx === currentStep;
          const isPast = idx < currentStep;
          const bg = isActive ? "#3B82F6" : isPast ? "#10B981" : "#E5E7EB";
          const color = isActive || isPast ? "#fff" : "#374151";
          return (
            <React.Fragment key={step.key}>
              {idx > 0 && (
                <div style={{ flex: 1, height: 2, backgroundColor: isPast ? "#10B981" : "#E5E7EB" }} />
              )}
              <div
                style={{
                  backgroundColor: bg,
                  color,
                  borderRadius: 6,
                  padding: "4px 10px",
                  fontSize: "0.75rem",
                  fontWeight: isActive ? 700 : 400,
                  whiteSpace: "nowrap",
                  transition: "background-color 0.3s",
                }}
              >
                {step.label}
              </div>
            </React.Fragment>
          );
        })}
      </div>
      {currentEpoch != null && (
        <div style={{ marginTop: 8, fontSize: "0.8rem", color: "#374151" }}>
          Current Epoch: <strong>{currentEpoch}</strong>
        </div>
      )}
    </div>
  );
}
