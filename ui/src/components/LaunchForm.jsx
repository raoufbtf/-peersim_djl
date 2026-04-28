import { useState } from "react";

  export default function LaunchForm({ onStart, onStop, onClear }) {
    const [datasetFiles, setDatasetFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [nodeCount] = useState(20);
  const [sessionReq, setSessionReq] = useState("");
  const [sessions, setSessions] = useState([
    { id: 1, modelType: "MLP" },
  ]);
  const [federatedEpochs, setFederatedEpochs] = useState(10);
  const [error, setError] = useState("");

  const parseList = (value) =>
    value
      .split(",")
      .map((v) => v.trim())
      .filter(Boolean);

  const handleStart = () => {
      // Client‑side validation
      for (const f of datasetFiles) {
        if (!f.name.toLowerCase().endsWith(".csv")) {
          setError("Only CSV files are allowed: " + f.name);
          return;
        }
        if (f.size > 50 * 1024 * 1024) {
          setError("File too large (max 50 MB): " + f.name);
          return;
        }
      }
      if (datasetFiles.length === 0) {
        setError("Please select at least one CSV file.");
        return;
      }

      // Construire le FormData pour le backend
      const fd = new FormData();
      datasetFiles.forEach((f) => fd.append("files", f));

      const sessionConfigs = sessions.map((session) => ({
        modelType: session.modelType,
        networkSize: Number(nodeCount),
        sessionRequirements: parseList(sessionReq).map(Number).filter((n) => !isNaN(n)),
        federatedEpochs: Number(federatedEpochs),
        learningRate: 0.001,
        batchStrategy: "ROUND_ROBIN",
        maxBatchesPerNode: 2,
        preprocessOnUpload: false,
        simulationCycles: 24,
      }));

      setError("");
      setUploadProgress(0);

      // Passer le FormData au parent (App.jsx) qui envoie la requête
      if (onStart) onStart({ formData: fd, sessionConfigs });
    };

  const handleStop = async () => {
      try {
        const resp = await fetch("http://localhost:8080/api/simulations/stop", { method: "POST" });
        if (!resp.ok) {
          let msg = "Failed to stop simulation";
          try {
            const data = await resp.json();
            msg = data.error || data.message || msg;
          } catch { /* ignore */ }
          setError(msg);
          return;
        }
        setError("");
        if (onStop) onStop();
      } catch (e) {
        setError(e.message);
      }
    };

  const handleClear = () => {
      setDatasetFiles([]);
      setSessionReq("");
      setSessions([{ id: 1, modelType: "MLP" }]);
      setFederatedEpochs(10);
      setError("");
      setUploadProgress(0);
      if (onClear) onClear();
    };

    return (
      <div
        style={{
          maxWidth: "100%",
          margin: "0 auto",
          padding: "18px",
          border: "1px solid #e5e7eb",
          borderRadius: "12px",
          backgroundColor: "#ffffff",
          boxShadow: "0 10px 24px rgba(0,0,0,0.08)",
          fontFamily: "'Inter', sans-serif",
        }}
      >
        <h2 style={{ margin: "0 0 14px", fontSize: "1.25rem", fontWeight: 700, color: "#111827" }}>Launch Simulation</h2>
        <p style={{ margin: "0 0 16px", color: "#6b7280", fontSize: "0.9rem" }}>
          Configure datasets and sessions before starting.
        </p>

        <label style={{ display: "block", marginBottom: "12px", color: "#111827", fontWeight: 600 }}>
          Dataset files (CSV only, max 50 MB each)
          <input
            type="file"
            accept=".csv"
            multiple
            onChange={(e) => setDatasetFiles(Array.from(e.target.files))}
            style={{
              width: "100%",
              padding: "8px",
              marginTop: "4px",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
              backgroundColor: "#f9fafb",
            }}
          />
          {datasetFiles.length > 0 && (
            <ul style={{ marginTop: "4px", listStyle: "disc", paddingLeft: "20px" }}>
              {datasetFiles.map((f, i) => (
                <li key={i} style={{ color: "#111827" }}>{f.name} ({(f.size/1024/1024).toFixed(1)} MB)</li>
              ))}
            </ul>
          )}
        </label>

        <div style={{ marginBottom: "12px", color: "#111827", fontWeight: 600 }}>
          Node count (fixed)
          <div
            style={{
              marginTop: "4px",
              backgroundColor: "#f9fafb",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
              padding: "8px",
              color: "#374151",
            }}
          >
            {nodeCount}
          </div>
        </div>

        <label style={{ display: "block", marginBottom: "12px", color: "#111827", fontWeight: 600 }}>
          Session requirements (comma‑separated integers)
          <input
            type="text"
            value={sessionReq}
            onChange={(e) => setSessionReq(e.target.value)}
            placeholder="e.g. 2,6"
            style={{
              width: "100%",
              padding: "8px",
              marginTop: "4px",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
            }}
          />
        </label>

        <div style={{ marginBottom: "12px" }}>
          <div style={{ color: "#111827", fontWeight: 600, marginBottom: "6px" }}>
            Sessions (models)
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            {sessions.map((session) => (
              <div
                key={session.id}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                }}
              >
                <select
                  value={session.modelType}
                  onChange={(e) =>
                    setSessions((prev) =>
                      prev.map((s) =>
                        s.id === session.id ? { ...s, modelType: e.target.value } : s
                      )
                    )
                  }
                  style={{
                    flex: 1,
                    padding: "8px",
                    border: "1px solid #cbd5e1",
                    borderRadius: "8px",
                    backgroundColor: "#fff",
                  }}
                >
                  <option value="MLP">MLP</option>
                  <option value="CNN">CNN</option>
                </select>
                {sessions.length > 1 && (
                  <button
                    type="button"
                    onClick={() =>
                      setSessions((prev) => prev.filter((s) => s.id !== session.id))
                    }
                    style={{
                      backgroundColor: "#ef4444",
                      color: "#fff",
                      border: "none",
                      padding: "8px 10px",
                      borderRadius: "8px",
                      cursor: "pointer",
                    }}
                  >
                    Remove
                  </button>
                )}
              </div>
            ))}
          </div>
          <button
            type="button"
            onClick={() =>
              setSessions((prev) => [
                ...prev,
                { id: Date.now(), modelType: "MLP" },
              ])
            }
            style={{
              marginTop: "8px",
              backgroundColor: "#2563eb",
              color: "#fff",
              border: "none",
              padding: "8px 12px",
              borderRadius: "8px",
              cursor: "pointer",
              fontWeight: 600,
            }}
          >
            + Add Session
          </button>
        </div>

        <label style={{ display: "block", marginBottom: "12px", color: "#111827", fontWeight: 600 }}>
          Federated epochs
          <input
            type="number"
            min={1}
            value={federatedEpochs}
            onChange={(e) => setFederatedEpochs(e.target.value)}
            style={{
              width: "100%",
              padding: "8px",
              marginTop: "4px",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
            }}
          />
        </label>

        {error && (
          <div style={{ color: "#dc2626", marginBottom: "12px", fontSize: "0.9rem" }}>{error}</div>
        )}
        {uploadProgress > 0 && uploadProgress < 100 && (
          <progress value={uploadProgress} max="100" style={{ width: "100%", marginBottom: "8px" }}>{uploadProgress}%</progress>
        )}

        <div style={{ display: "flex", gap: "8px", justifyContent: "space-between" }}>
          <button
            onClick={handleStart}
            style={{
              flex: 1,
              backgroundColor: "#16a34a",
              color: "#fff",
              border: "none",
              padding: "10px",
              borderRadius: "8px",
              cursor: "pointer",
              fontWeight: 600,
            }}
          >
            Start
          </button>
          <button
            onClick={handleStop}
            style={{
              flex: 1,
              backgroundColor: "#dc2626",
              color: "#fff",
              border: "none",
              padding: "10px",
              borderRadius: "8px",
              cursor: "pointer",
              fontWeight: 600,
            }}
          >
            Stop
          </button>
          <button
            onClick={handleClear}
            style={{
              flex: 1,
              backgroundColor: "#6b7280",
              color: "#fff",
              border: "none",
              padding: "10px",
              borderRadius: "8px",
              cursor: "pointer",
              fontWeight: 600,
            }}
          >
            Clear
          </button>
        </div>
      </div>
    );
  }
