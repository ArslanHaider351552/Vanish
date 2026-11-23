# Vanish — Class Network

**An ephemeral Android messaging application where messages disappear after 30 seconds.**

Vanish is an Android-based messaging application developed as part of the Vanish Class Network project. It allows students to communicate in a shared chat room through a common WebSocket relay.

Messages are assigned a 30-second lifetime by the relay. The application displays messages with an expiry countdown and removes them when their lifetime ends. It also handles temporary network disconnections through an offline message queue and prevents expired queued messages from being sent.

Vanish follows the class-approved **Vanish Relay Protocol v1**, ensuring compatibility with other students' applications.

---

## 1. Project Overview

| Property | Details |
|---|---|
| Project Name | Vanish |
| Platform | Android |
| Language | Kotlin |
| IDE | Android Studio |
| UI Framework | Jetpack Compose |
| Communication | WebSocket |
| WebSocket Library | OkHttp |
| Data Format | JSON |
| Protocol Version | 1 |
| Message Lifetime | 30 seconds |
| Network Architecture | Client–Relay |
| Relay Type | Shared hosted relay |

## 2. Features

- **Ephemeral Messaging:** Messages automatically expire 30 seconds after the relay sends them.
- **Shared Class Chat:** Students connected to the same relay can communicate in a shared room.
- **Live Countdown:** Displays the remaining lifetime of messages.
- **Server-Based Expiry:** Uses relay-provided timestamps instead of relying solely on the phone's clock.
- **Offline Message Queue:** Holds outgoing messages when the application is disconnected.
- **Automatic Message Dropping:** Removes queued messages that exceed their permitted 30-second waiting period.
- **Duplicate Prevention:** Uses unique message IDs to identify and ignore previously processed messages.
- **Connection Monitoring:** Uses heartbeat messages to monitor the WebSocket connection.
- **Automatic Reconnection:** Attempts to reconnect when the connection is lost.
- **Online Presence:** Supports relay-provided information about connected members.

## 3. Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Application development |
| Android Studio | Development environment |
| Jetpack Compose | User interface |
| OkHttp | WebSocket communication |
| Gson | JSON serialization and deserialization |
| Kotlin Coroutines | Asynchronous operations |
| Kotlin Flow | Reactive state and event handling |

## 4. Shared Relay Configuration

Vanish connects to the shared class relay using the following address:

```text
wss://vanish-relay-sawaira.bonto.page/ws
```

**Connection details:**

- Protocol: Vanish Relay Protocol v1
- Transport: Secure WebSocket (WSS)
- Message format: JSON text frames
- Message lifetime: 30,000 milliseconds
- Maximum message length: 500 characters
- Maximum display-name length: 30 characters

The shared relay is responsible for coordinating communication between connected applications, assigning message timestamps, broadcasting messages, and announcing message expiry.

**Important:** The application connects directly to the shared hosted relay. Running the previous local Python test relay is not required for normal application use.

## 5. Message Lifecycle

Every message follows a defined lifecycle:

1. **Created:** The user creates a message and the application assigns a unique ID.
2. **Waiting:** If the application is offline, the message enters the outgoing queue.
3. **Sent:** The message is transmitted to the relay.
4. **Delivered:** The relay broadcasts the message and sends a delivery confirmation to the sender.
5. **Displayed:** Connected applications display the message with its remaining lifetime.
6. **Expired:** The message reaches its relay-assigned expiry time and is removed from the application.
7. **Dropped:** A queued message is discarded if its permitted waiting lifetime expires before transmission.

## 6. Time Synchronization and Message Expiry

The relay determines the authoritative message timestamps.

When an application joins, the relay provides its current server time. The application uses this value to calculate the difference between the server clock and the phone clock.

**Clock Offset = Server Time − Phone Time**

The remaining message lifetime is calculated as:

**Remaining Time = (Expires At − Clock Offset) − Current Phone Time**

This approach helps maintain consistent countdowns across devices with different system-clock settings.

The application uses the absolute expiry timestamp rather than relying on a countdown that simply continues decreasing. This allows the remaining lifetime to be recalculated when the application resumes.

## 7. Offline Message Handling

Vanish supports temporary network disconnections.

When a user sends a message without an active relay connection:

- The message is placed in the outgoing queue.
- Its unique identifier is retained for transmission.
- The application checks its age when connectivity becomes available.
- If it has exceeded the 30-second queue limit, it is dropped.
- Expired queued messages are not transmitted after reconnection.

For example, if a message is created at 0:00, the network returns at 0:50, and the queue limit is 30 seconds, the application drops the message instead of sending it.

## 8. Protocol Events

Vanish follows the agreed Protocol v1 message and event structure.

| Event | Direction | Purpose |
|---|---|---|
| JOIN | App → Relay | Join the shared room |
| WELCOME | Relay → App | Confirm connection and provide server time |
| MESSAGE | App → Relay | Submit an outgoing message |
| MESSAGE | Relay → Apps | Broadcast a message |
| DELIVERED | Relay → Sender | Confirm the number of receiving applications |
| EXPIRED | Relay → Apps | Announce message expiry |
| PING | App → Relay | Heartbeat request |
| PONG | Relay → App | Heartbeat response |
| PRESENCE | Relay → Apps | Provide online member information |
| ERROR | Relay → App | Report protocol or message errors |

### Heartbeat and Reconnection

- Heartbeat interval: 15 seconds.
- PONG timeout: 30 seconds.
- Reconnection intervals: 1, 2, 4, and then repeated 8-second intervals.
- Old messages are not replayed after reconnection.

## 9. Installation and Execution

### Requirements

- Windows, Linux, or macOS computer.
- Android Studio.
- Android SDK compatible with the project configuration.
- Android emulator or physical Android device.
- Internet connection to access the shared relay.

### Steps

1. Clone or download the Vanish project repository.
2. Open the project in Android Studio.
3. Allow Gradle to synchronize the project dependencies.
4. Select an Android emulator or connect a physical Android device.
5. Build the application using **Build → Make Project**.
6. Run the application using the Android Studio Run button.
7. Join the shared chat using a display name.
8. Send messages and communicate with other students connected to the class relay.

**Note:** The previous local Python relay was used for development and testing. The current application uses the shared hosted relay address specified above.

## 10. Testing and Demonstration

The application was tested through the shared class communication environment.

The following behaviours were demonstrated:

| Test | Result |
|---|---|
| Connection to shared class relay | Successfully demonstrated |
| Online message transmission | Successfully demonstrated |
| Communication with classmates | Successfully demonstrated |
| Offline message waiting | Demonstrated |
| Expired queued message dropping | Demonstrated |
| Shared protocol integration | Implemented using Protocol v1 |

The application also includes message expiry, countdown, duplicate prevention, and connection-management functionality.

Further testing of background behaviour, clock differences, and storage-level deletion should be distinguished from the behaviours demonstrated through the available screenshots.

## 11. Limitations

Vanish provides temporary messaging within its application, but it cannot guarantee that message content disappears from every possible location.

- **Screenshots:** Recipients can capture messages before they expire.
- **Copying:** Users can manually copy or reproduce message content.
- **Relay Memory:** The relay temporarily holds messages in memory to distribute them.
- **Offline Devices:** A device that is switched off when a message expires cannot process the expiry event until it becomes active again.
- **No Dedicated Local Database:** The current implementation does not include a dedicated local database for persistent conversation storage or database-level deletion verification.
- **Shared Communication:** Protocol v1 broadcasts messages to all joined applications rather than providing private, individually addressed conversations.

These limitations reflect the practical boundaries of ephemeral messaging.

## 12. Project Purpose and Learning Outcomes

The Vanish project demonstrates practical experience in:

- Developing an Android application using Kotlin.
- Implementing WebSocket-based communication.
- Following a shared communication protocol.
- Managing message lifecycles and expiry timestamps.
- Handling offline messages and network reconnection.
- Preventing duplicate message processing.
- Understanding the limitations of temporary messaging systems.
- Integrating independently developed applications through a common relay.

---

**Project:** Vanish — Class Network  
**Protocol:** Vanish Relay Protocol v1  
**Platform:** Android  
**Relay:** `wss://vanish-relay-sawaira.bonto.page/ws`