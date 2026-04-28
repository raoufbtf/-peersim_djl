import { useRef, useEffect } from "react";

  const LEVEL_COLORS = {
    INFO: "#16a34a",
    WARN: "#ea580c",
    ERROR: "#dc2626",
  };

  export default function EventFeed({ events }) {
    const scrollRef = useRef(null);

    useEffect(() => {
      if (scrollRef.current) {
        scrollRef.current.scrollTop = 0;
      }
    }, [events.length]);

    const displayed = (events || []).slice(-500).slice().reverse();

    return (
      <div
        ref={scrollRef}
        style={{
          height: "400px",
          overflowY: "auto",
          border: "1px solid #d1d5db",
          borderRadius: "6px",
          padding: "8px",
          backgroundColor: "#f9fafb",
          fontFamily: "monospace",
          fontSize: "13px",
        }}
      >
        {displayed.length === 0 && (
          <div style={{ color: "#9ca3af", textAlign: "center", padding: "16px" }}>
            No events yet.
          </div>
        )}

        {displayed.map((evt, idx) => {
          const ts =
            evt.timestamp != null
              ? new Date(evt.timestamp).toLocaleTimeString("en-GB", {
                  hour: "2-digit",
                  minute: "2-digit",
                  second: "2-digit",
                  hour12: false,
                })
              : "--:--:--";

          const levelColor = LEVEL_COLORS[evt.level] || "#6b7280";

          return (
            <div
              key={evt.timestamp ? evt.timestamp + "-" + idx : idx}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                padding: "4px 8px",
                borderBottom: "1px solid #e5e7eb",
                lineHeight: "1.4",
              }}
            >
              <span style={{ color: "#6b7280", whiteSpace: "nowrap", minWidth: "70px" }}>{ts}</span>

              <span
                style={{
                  color: "#fff",
                  backgroundColor: levelColor,
                  borderRadius: "4px",
                  padding: "1px 6px",
                  fontSize: "11px",
                  fontWeight: 600,
                  textTransform: "uppercase",
                  minWidth: "54px",
                  textAlign: "center",
                }}
              >
                {evt.level || "INFO"}
              </span>

              <span
                style={{
                  color: "#2563eb",
                  fontWeight: 500,
                  minWidth: "90px",
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
                title={evt.type}
              >
                {evt.type || "—"}
              </span>

              <span style={{ color: "#1f2937", flex: 1, wordBreak: "break-word" }}>{evt.message || "—"}</span>
            </div>
          );
        })}
      </div>
    );
  }