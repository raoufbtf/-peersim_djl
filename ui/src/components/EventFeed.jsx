import { useRef, useEffect, useState } from "react";

const LEVEL_COLORS = {
  INFO: "#16a34a",
  WARN: "#ea580c",
  ERROR: "#dc2626",
};

function getEventCategory(event) {
  const message = event?.message || '';
  if (/chord|stabilize|notify|successor|predecessor|route|dht|join|leave|lookup|replication|stabilisation|réparation/i.test(message)) {
    return 'network';
  }
  if (/session|IDE élu|election|élection|leader|coordinator|requête/i.test(message)) {
    return 'session';
  }
  if (/batch|dht|stockage|récupération|publication|dépôt/i.test(message)) {
    return 'dht';
  }
  if (/epoch|accuracy|loss|learning|training|gradient|agrégation|fedavg|convergence|vote|entraînement|époque/i.test(message)) {
    return 'learning';
  }
  return 'other';
}

export default function EventFeed({ events, filters }) {
  const scrollRef = useRef(null);
  const prevEventsRef = useRef([]);
  const [newEventIds, setNewEventIds] = useState(new Set());

  // Detect new events for animation
  useEffect(() => {
    const prevIds = new Set(prevEventsRef.current.map(e => `${e.timestamp}-${e.message}`));
    const currentEvents = events || [];
    const currentIds = new Set(currentEvents.map(e => `${e.timestamp}-${e.message}`));
    const newIds = new Set([...currentIds].filter(id => !prevIds.has(id)));
    if (newIds.size > 0) {
      setNewEventIds(newIds);
      // Clear animation flag after animation ends
      setTimeout(() => setNewEventIds(new Set()), 600);
    }
    prevEventsRef.current = currentEvents;
  }, [events]);

  // Auto-scroll to top on new events
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
  }, [(events || []).length]);

  // Apply filters
  const filtered = (events || []).filter(evt => {
    if (!filters) return true;
    const category = getEventCategory(evt);
    return filters[category] !== false;
  });

  const displayed = filtered.slice(-500).slice().reverse();

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
      <style>{`
        @keyframes slideIn {
          from { opacity: 0; transform: translateY(-10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .new-event {
          animation: slideIn 0.5s ease-out;
        }
      `}</style>
      {displayed.length === 0 && (
        <div style={{ color: "#9ca3af", textAlign: "center", padding: "16px" }}>
          No events yet.
        </div>
      )}

      {displayed.map((evt, idx) => {
        const eventId = `${evt.timestamp}-${evt.message}`;
        const isNew = newEventIds.has(eventId);
        const ts =
          evt.timestamp != null
            ? new Date(evt.timestamp).toLocaleTimeString("en-GB", {
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
                fractionalSecondDigits: 3,
              })
            : "--:--:--.---";

        const levelColor = LEVEL_COLORS[evt.level] || "#6b7280";

        return (
          <div
            key={eventId}
            className={isNew ? "new-event" : ""}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              padding: "4px 8px",
              borderBottom: "1px solid #e5e7eb",
              lineHeight: "1.4",
            }}
          >
            <span style={{ color: "#6b7280", whiteSpace: "nowrap", minWidth: "100px" }}>{ts}</span>

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
