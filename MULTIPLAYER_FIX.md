# Multiplayer Queue Synchronization Fix

## Problem
The queue system was not properly synchronized between multiple players:
- When Player A opened the queue GUI, they only saw themselves
- When Player B joined the same queue, Player A's GUI didn't update
- Players couldn't see other players in the queue in real-time

## Solution Implemented

### 1. Queue Screen Tracking System
Added a tracking map in `QueueViewScreen.java`:
```java
private static final Map<UUID, Integer> OPEN_QUEUE_SCREENS = new ConcurrentHashMap<>();
```
This tracks which players have queue screens open and for which difficulty.

### 2. Auto-Refresh Mechanism
Added `refreshQueueScreens()` method that:
- Finds all players with the queue screen open for a specific difficulty
- Automatically re-opens their screen with updated player list
- Runs on the server thread to ensure thread safety

### 3. Integration with Queue Manager
Modified `RaidDenQueueManager.join()` to:
- Call `refreshQueueScreens()` after a player joins
- Update all open GUIs for that difficulty level
- Show the new player's head immediately to all viewers

### 4. Proper Cleanup
Added `closeQueueScreen()` to remove tracking when:
- Player closes the GUI
- Player disconnects
- Raid starts

## How It Works

1. **Player A opens queue GUI**
   - System registers: `{PlayerA_UUID: 4}` (for 4-star queue)
   - Shows empty slots or current queued players

2. **Player B joins the queue**
   - `RaidDenQueueManager.join()` is called
   - Player B is added to the queue
   - `refreshQueueScreens()` is triggered
   - Player A's GUI automatically refreshes
   - Player A now sees Player B's head in the GUI

3. **Both players see updates**
   - Real-time synchronization
   - Player count updates
   - Player heads appear/disappear as players join/leave
   - Both can click "Start Raid" to begin

## Testing Checklist

- [x] Player A opens queue, sees empty slots
- [x] Player B joins, Player A's GUI updates automatically
- [x] Player C joins, both A and B see Player C
- [x] Players can start raid together
- [x] Queue clears after raid starts
- [x] Screen closes properly without errors

## Technical Notes

- Uses `ConcurrentHashMap` for thread-safety
- Server-side only (no client-side packets needed)
- Minimal performance impact (only refreshes open screens)
- Compatible with any number of simultaneous queues
