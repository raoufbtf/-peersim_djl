import React, { useMemo, useRef, useEffect } from "react";

const T = {
  surface: "#0d1526", border: "#1e2d4a",
  cyan: "#00c8ff", textSecondary: "#6b82a8", textMuted: "#3d5070",
  fontMono: "'JetBrains Mono', 'Fira Code', monospace",
};

export default function ParamHeatmap({ data, maxParams = 200, maxEpochs = 30 }) {
  const canvasRef = useRef(null);

  const { epochs, params, matrix, min, max } = useMemo(() => {
    if (!data || data.epochs.length === 0) return { epochs: [], params: [], matrix: [], min: 0, max: 1 };
    const epochs = data.epochs.slice(-maxEpochs);
    const params = data.params.slice(-maxParams);
    const matrix = epochs.map(ep => params.map(pm => data.values.get(ep)?.get(pm) ?? null));
    let mn = Infinity, mx = -Infinity;
    for (const row of matrix) for (const v of row) { if (v == null) continue; mn = Math.min(mn, v); mx = Math.max(mx, v); }
    if (!isFinite(mn) || !isFinite(mx) || mn === mx) { mn = -1; mx = 1; }
    return { epochs, params, matrix, min: mn, max: mx };
  }, [data, maxParams, maxEpochs]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || epochs.length === 0 || params.length === 0) return;
    const cell = 10;
    canvas.width = params.length * cell;
    canvas.height = epochs.length * cell;
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (let y = 0; y < epochs.length; y++) {
      for (let x = 0; x < params.length; x++) {
        const v = matrix[y][x];
        const n = v == null ? 0.5 : (v - min) / (max - min);
        /* Dark color scale: deep blue → cyan → white */
        let r, g, b;
        if (n < 0.5) {
          const t = n * 2;
          r = Math.round(7 + t * 0);
          g = Math.round(12 + t * (200 - 12));
          b = Math.round(24 + t * (255 - 24));
        } else {
          const t = (n - 0.5) * 2;
          r = Math.round(0 + t * 226);
          g = Math.round(200 + t * (226 - 200));
          b = Math.round(255 + t * (226 - 255));
        }
        ctx.fillStyle = v == null ? "#0d1526" : `rgb(${r},${g},${b})`;
        ctx.fillRect(x * cell, y * cell, cell, cell);
      }
    }
  }, [epochs, params, matrix, min, max]);

  if (!data || data.epochs.length === 0) {
    return (
      <div style={{ height: 120, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 8 }}>
        <div style={{ color: T.border, fontSize: 24 }}>▤</div>
        <div style={{ color: T.textMuted, fontFamily: T.fontMono, fontSize: 12 }}>No param data yet</div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textSecondary, marginBottom: 8 }}>
        {Math.min(maxEpochs, data.epochs.length)} epochs × {Math.min(maxParams, data.params.length)} params
      </div>
      <div style={{ overflow: "auto", background: T.surface, border: `1px solid ${T.border}`, borderRadius: 6, padding: 6 }}>
        <canvas ref={canvasRef} style={{ display: "block", imageRendering: "pixelated" }} />
      </div>
      {/* gradient legend */}
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginTop: 8 }}>
        <span style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textSecondary }}>{min.toFixed(4)}</span>
        <div style={{ flex: 1, height: 6, borderRadius: 3, background: "linear-gradient(90deg, #07182c, #00c8ff, #e2e2e2)" }} />
        <span style={{ fontFamily: T.fontMono, fontSize: 10, color: T.textSecondary }}>{max.toFixed(4)}</span>
      </div>
    </div>
  );
}
