// ui/src/hooks/useWebSocket.js
import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";

/**
 * Custom hook to manage a STOMP WebSocket connection.
 *
 * @param {string} url - Primary WebSocket endpoint (e.g. ws://localhost:8080/ws).
 * @param {string} fallbackUrl - URL to poll when the WebSocket disconnects.
 * @returns {{ events: any[], connected: boolean }} - Received events and connection state.
 */
export default function useWebSocket(url, fallbackUrl) {
  const [events, setEvents] = useState([]);
  const [communications, setCommunications] = useState([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const fallbackIntervalRef = useRef(null);

  // Append a new event, keeping only the latest 1000 entries.
  const addEvent = (event) => {
    setEvents((prev) => {
      const updated = [...prev, event];
      return updated.length > 1000 ? updated.slice(updated.length - 1000) : updated;
    });
  };

  const addCommunication = (communication) => {
    setCommunications((prev) => {
      const updated = [...prev, communication];
      return updated.length > 2000 ? updated.slice(updated.length - 2000) : updated;
    });
  };

  // Clean up any running fallback poll.
  const clearFallbackPoll = () => {
    if (fallbackIntervalRef.current) {
      clearInterval(fallbackIntervalRef.current);
      fallbackIntervalRef.current = null;
    }
  };

  // Start polling the fallback URL every 3 seconds.
  const startFallbackPoll = () => {
    clearFallbackPoll();
    fallbackIntervalRef.current = setInterval(() => {
      fetch(fallbackUrl)
        .then(resp => resp.json())
        .then(data => {
          if (Array.isArray(data)) {
            data.forEach(event => addEvent(event));
          }
        })
        .catch(() => {});
      fetch("http://localhost:8080/api/simulations/communications?limit=2000")
        .then(resp => resp.json())
        .then(data => {
          if (Array.isArray(data)) {
            data.forEach(communication => addCommunication(communication));
          }
        })
        .catch(() => {});
    }, 3000);
  };

  const fetchInitialHistory = () => {
    fetch(fallbackUrl)
      .then(resp => resp.json())
      .then(data => {
        if (Array.isArray(data)) {
          data.forEach(event => addEvent(event));
        }
      })
      .catch(() => {});

    fetch("http://localhost:8080/api/simulations/communications?limit=2000")
      .then(resp => resp.json())
      .then(data => {
        if (Array.isArray(data)) {
          data.forEach(communication => addCommunication(communication));
        }
      })
      .catch(() => {});
  };

  const fetchCommunicationsHistory = () => {
    fetch("http://localhost:8080/api/simulations/communications?limit=2000")
      .then(resp => resp.json())
      .then(data => {
        if (Array.isArray(data)) {
          data.forEach(communication => addCommunication(communication));
        }
      })
      .catch(() => {});
  };

  const clearEvents = () => {
    setEvents([]);
  };

  const clearCommunications = () => {
    setCommunications([]);
  };

  useEffect(() => {
    const client = new Client({
      brokerURL: url,
      reconnectDelay: 5000,
      debug: () => {},
    });

    client.onConnect = () => {
      setConnected(true);
      clearFallbackPoll();
      fetchInitialHistory();
      fetchCommunicationsHistory();

      // Subscribe to the topic where the server sends simulation events.
      client.subscribe("/topic/sim-events", (msg) => {
        try {
          addEvent(JSON.parse(msg.body));
        } catch {
          addEvent(msg.body);
        }
      });

      client.subscribe("/topic/communications", (msg) => {
        try {
          addCommunication(JSON.parse(msg.body));
        } catch {
          addCommunication(msg.body);
        }
      });
    };

    client.onStompError = () => {
      setConnected(false);
      startFallbackPoll();
    };

    client.onWebSocketClose = () => {
      setConnected(false);
      startFallbackPoll();
    };

    client.onWebSocketError = () => {
      setConnected(false);
      startFallbackPoll();
    };

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
      clearFallbackPoll();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, fallbackUrl]);

  return { events, communications, connected, clearEvents, clearCommunications };
}
