# Session History System Guide

## Overview
The application now has a complete **Session History** feature that captures all information from completed learning sessions and organizes them for review and comparison.

## How It Works

### 1. **Session Lifecycle**
- When you click **"Start"** in the Sidebar:
  - A new session begins
  - Current timestamp is recorded
  - Dashboard and Communications tabs are cleared and ready for new data
  - Speed control is available for animation playback

- When you click **"Stop"**:
  - All accumulated session data is saved to the Session History
  - Data includes: communications, accuracy charts, timing, nodes used, metrics
  - Dashboard and Communications tabs are automatically cleared
  - Session appears in the **Summary Tab** for permanent storage

### 2. **What Gets Stored**
For each completed session, the following information is preserved:

| Field | Description | Example |
|-------|-------------|---------|
| **Session ID** | Unique identifier | `e2d1f4a8-9c2f...` |
| **Dataset** | Input dataset used | `adult.csv` |
| **Duration** | Total execution time | `125s` |
| **Nodes Used** | Number of nodes in network | `10` |
| **Messages** | Total communications exchanged | `4,523` |
| **Final Accuracy** | Global accuracy at completion | `92.45%` |
| **Accuracy Chart** | Per-epoch accuracy progression | (Visual graph) |
| **Communication Breakdown** | Message types (GRADIENT, GOSSIP_VOTE, etc.) | (Pie-like breakdown) |
| **Communication Log** | All message exchanges with timestamps | (Detailed list) |

### 3. **Dashboard & Communications Tabs**
- **During an active session**: Show live, real-time data
- **After session completion**: Data is cleared automatically
- **Purpose**: Focus on current simulation, avoid clutter from multiple sessions

### 4. **Summary Tab (Session History)**
- **Left Panel**: List of all completed sessions
  - Sorted by completion time (most recent first)
  - Quick stats: duration, node count, message count, accuracy
  - Click any session to view detailed information

- **Right Panel**: Detailed session information
  - Metrics cards: Duration, Nodes, Messages, Final Accuracy
  - Full accuracy chart showing training progression
  - Communication breakdown by message type
  - Detailed message log (last 50 messages)

## Usage Example

### Step 1: Launch a Session
1. Configure your session in the **Sidebar**
2. Select dataset, network size, epochs, etc.
3. Click **"Start"**

### Step 2: Monitor Progress
- Watch real-time updates in **Dashboard Tab**
- See communications flowing in **Communications Tab**
- Adjust playback speed with the **Event Playback Speed** slider

### Step 3: Complete Session
1. Click **"Stop"** when finished (or let it auto-complete)
2. Data is automatically archived

### Step 4: Review History
1. Switch to **Summary Tab**
2. Click on any session in the left panel
3. Review all metrics, charts, and communications

## Key Benefits

✅ **Persistent Storage**: All sessions are kept until you refresh/clear the browser  
✅ **Detailed Analytics**: Full accuracy progression, communication patterns  
✅ **Quick Comparison**: Review multiple sessions side-by-side (switch sessions in the list)  
✅ **Clean UI**: Dashboard stays focused on current session, history stays separate  
✅ **Complete Audit Trail**: Every message and metric is preserved  

## Technical Details

### Session Object Structure
```javascript
{
  id: "string",                    // Session UUID
  dataset: "string",               // Dataset filename
  status: "DONE",                  // Completion status
  createdAt: "ISO-8601",          // Start timestamp
  lastUpdated: "ISO-8601",        // End timestamp
  samples: number,                 // Data samples processed
  nodesUsed: number,              // Number of nodes
  accuracy: { epoch, localAccuracy, globalAccuracy },
  communications: [],              // Array of all message objects
  accuracyPoints: [],             // Array of { epoch, localAccuracy, globalAccuracy }
  duration: number,               // Seconds (calculated)
  epochs: { currentEpoch, maxEpoch },
  globalMetrics: { epoch, accuracy, loss, dataset },
  nodeStats: {}                   // Map of node performance metrics
}
```

### Data Flow
1. **useWebSocket Hook** captures live events and communications
2. **App.jsx** aggregates data during session (Dashboard tab)
3. **handleStop()** saves complete session snapshot to `completedSessions` state
4. **SummaryTab** reads from `completedSessions` and renders history

## Browser Storage Note
- Session history is stored in React component state
- Data persists while the browser tab is open
- Page refresh will clear history (optional: add localStorage persistence)
- To add persistence, use `localStorage` in a `useEffect` hook

## Future Enhancements
- [ ] Export session data to CSV/JSON
- [ ] Filter/search sessions by dataset or date
- [ ] Compare two sessions side-by-side
- [ ] Persist to localStorage for longer-term storage
- [ ] Graph communication patterns over time
