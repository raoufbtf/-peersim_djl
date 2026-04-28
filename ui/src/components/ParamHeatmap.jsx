import React, { useMemo, useRef, useEffect } from "react";

export default function ParamHeatmap({ data, maxParams = 200, maxEpochs = 30 }) {
  const canvasRef = useRef(null);

  const { epochs, params, matrix, min, max } = useMemo(() => {
    if (!data || data.epochs.length === 0) {
      return { epochs: [], params: [], matrix: [], min: 0, max: 1 };
    }

    const epochs = data.epochs.slice(-maxEpochs);
    const params = data.params.slice(-maxParams);
    const matrix = epochs.map((epoch) => {
      const row = [];
      for (const param of params) {
        row.push(data.values.get(epoch)?.get(param) ?? null);
      }
      return row;
    });

    let min = Infinity;
    let max = -Infinity;
    for (const row of matrix) {
      for (const value of row) {
        if (value == null) continue;
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
    }
    if (!Number.isFinite(min) || !Number.isFinite(max) || min === max) {
      min = -1;
      max = 1;
    }

    return { epochs, params, matrix, min, max };
  }, [data, maxParams, maxEpochs]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || epochs.length === 0 || params.length === 0) return;
    const cellSize = 8;
    const width = params.length * cellSize;
    const height = epochs.length * cellSize;
    canvas.width = width;
    canvas.height = height;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, width, height);

    for (let y = 0; y < epochs.length; y++) {
      for (let x = 0; x < params.length; x++) {
        const value = matrix[y][x];
        const normalized = value == null ? 0.5 : (value - min) / (max - min);
        const hue = 240 - normalized * 240; // blue -> red
        const lightness = value == null ? 92 : 55;
        ctx.fillStyle = `hsl(${hue}, 75%, ${lightness}%)`;
        ctx.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
      }
    }
  }, [epochs, params, matrix, min, max]);

  if (!data || data.epochs.length === 0) {
    return (
      <div style={{ color: "#9CA3AF", fontSize: "0.85rem" }}>
        No parameter evolution data yet.
      </div>
    );
  }

  return (
    <div>
      <div style={{ fontSize: "0.8rem", color: "#6B7280", marginBottom: 8 }}>
        Showing last {Math.min(maxEpochs, data.epochs.length)} epochs and {Math.min(maxParams, data.params.length)} parameters.
      </div>
      <div style={{ overflow: "auto", border: "1px solid #E5E7EB", borderRadius: 8, padding: 8, background: "#fff" }}>
        <canvas ref={canvasRef} style={{ display: "block" }} />
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.75rem", color: "#6B7280", marginTop: 6 }}>
        <span>Low ({min.toFixed(4)})</span>
        <span>High ({max.toFixed(4)})</span>
      </div>
    </div>
  );
}
