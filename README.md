# Smart Campus API

## Overview

This API is a backend system I built for the Client-Server Architectures coursework. It handles two main resources — **Rooms** and **Sensors** — with a third nested resource for **Sensor Readings**.

The whole thing is built around RESTful principles. Rooms represent physical spaces on campus (like labs and libraries), sensors are devices deployed inside those rooms (temperature monitors, CO2 trackers, etc.), and readings are the historical data points recorded by each sensor over time.

The resource hierarchy reflects this physical structure:
/api/v1/                               : discovery endpoint
/api/v1/rooms                          : manage campus rooms
/api/v1/rooms/{roomId}                 : a specific room
/api/v1/sensors                        : manage all sensors
/api/v1/sensors/{sensorId}/readings    : historical readings for a sensor

I built the API using **JAX-RS** as the REST framework, deployed on **Apache Tomcat** through **NetBeans**. All data is stored in-memory using HashMaps — no database is used. JSON serialisation is handled automatically by **Jackson**.

Key design decisions:
- A singleton `DataStore` class holds all data so it persists between requests
- Custom exception mappers handle all error cases and return clean JSON — no raw stack traces are ever exposed
- A logging filter records every request and response automatically
- The sub-resource locator pattern is used for sensor readings to keep the code organised

---

## How to Build and Run

**Clone the repo:**
```bash
git clone https://github.com/KimiaHaghgoo/smart-campus-api.git
```

Open NetBeans, go to File → Open Project and select the folder we just cloned.

Once it's open, right-click the project and hit **Clean and Build** — wait until you see `BUILD SUCCESS` at the bottom. Then right-click again and hit **Run**. NetBeans handles the Tomcat deployment automatically.

The API will be running at:
http://localhost:8080/smart-campus-api/api/v1/

To check it's working, hit that URL in Postman or a browser — we should get back a JSON list with the two pre-loaded rooms. To stop the server, right-click the project and hit **Stop**.

---

## curl Examples

```bash
# Discovery
curl http://localhost:8080/smart-campus-api/api/v1/

# Get all rooms
curl http://localhost:8080/smart-campus-api/api/v1/rooms

# Get one room
curl http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301

# Create a room
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"HALL-01","name":"Main Hall","capacity":200}'

# Delete a room with no sensors — returns 204
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/HALL-01

# Delete a room that still has sensors — returns 409
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301

# Get all sensors
curl http://localhost:8080/smart-campus-api/api/v1/sensors

# Filter sensors by type
curl "http://localhost:8080/smart-campus-api/api/v1/sensors?type=CO2"

# Create a sensor (roomId must exist or returns 422)
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":0.0,"roomId":"LIB-301"}'

# Post a reading
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.5}'

# Get reading history
curl http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings
```

---

## Report Answers

### Part 1 — Q1: JAX-RS Resource Lifecycle

By default JAX-RS creates a new instance of each resource class for every incoming request. This means you cannot store data inside the resource class itself — it gets thrown away after the request finishes.

To deal with this I created a separate DataStore class that uses the singleton pattern. There is only ever one DataStore alive at a time, and every resource class calls DataStore.getInstance() to get it. The HashMaps inside it persist for the lifetime of the server.

The downside is that concurrent requests could hit the same HashMap simultaneously and corrupt data. In a production system you would use ConcurrentHashMap or add synchronization. For this project it is not our concern since the load is minimal.

### Part 1 — Q2: HATEOAS

HATEOAS means the API tells the client where it can go next rather than the client having to know the URLs in advance. The discovery endpoint returns links to `/api/v1/rooms` and `/api/v1/sensors` in the response body — a client can start there and navigate the whole API without reading any documentation.

The advantage over static docs is that if the URL structure ever changes, clients following links in responses keep working. Clients that hardcoded URLs from a document break. It also makes the API easier to explore for the first time.

### Part 2 — Q1: Full Objects vs IDs in Lists

Returning only IDs means the client needs a separate GET request for each one to retrieve any actual data. For 50 rooms that is 51 round-trips just to show a list. Returning full objects costs one request regardless of how many rooms there are, but the payload grows with the size of the collection.

For this project returning full objects is fine — the number of rooms on a campus is bounded and the payloads stay small. At larger scale we would add pagination and probably return a trimmed summary object rather than the full room.

### Part 2 — Q2: DELETE Idempotency

Yes, DELETE is idempotent here. The first call on an existing room removes it and returns 204 No Content. A second identical call finds nothing and returns 404 Not Found. The server state is the same after both — the room is gone either way. The response code differs but idempotency is about server state, not response codes.

### Part 3 — Q1: @Consumes and Wrong Content-Type

`@Consumes(MediaType.APPLICATION_JSON)` tells Jersey this method only accepts JSON. If a request arrives with Content-Type: text/plain or application/xml, Jersey rejects it before the method is ever called and returns 415 Unsupported Media Type automatically. No custom handling is needed — the framework enforces it at the routing level, so malformed requests never reach the business logic.

### Part 3 — Q2: Query Params vs Path Segments for Filtering

A path segment like `/sensors/type/CO2` implies CO2 is a resource — something you can address directly. It is not, it is a filter condition. Query parameters are the right fit because they are optional by default, so GET /sensors and GET /sensors?type=CO2 both go through the same method without extra routing.

They also scale naturally when you need multiple filters. `?type=CO2&status=ACTIVE` is straightforward. The path equivalent would be `/sensors/type/CO2/status/ACTIVE` which is rigid and hard to extend.

### Part 4 — Q1: Sub-Resource Locator Pattern

Rather than handling readings inside SensorResource and making it responsible for two different things, I used a sub-resource locator. SensorResource has a method annotated with `@Path("/{sensorId}/readings")` that instantiates and returns a SensorReadingResource. Jersey routes all requests under that path to that class.

This keeps each class focused on one concern. SensorResource handles sensors, SensorReadingResource handles readings. Changes to the readings logic only touch one file. In a large API with many levels of nesting, putting everything in one controller makes the code very hard to follow and maintain.

### Part 5 — Q1: 422 vs 404 for Missing Room Reference

404 means the URL does not exist. But POST /api/v1/sensors does exist — it received the request fine. The problem is that the roomId in the JSON body references a room that is not in the system. The endpoint is valid, the JSON syntax is valid, the data just contains a broken reference.

422 Unprocessable Entity covers this exactly — the server understood the request but the content fails a semantic check. Using 404 would make the client think the sensors endpoint itself is missing, which is misleading and would make debugging harder than it needs to be.

### Part 5 — Q2: Security Risks of Exposing Stack Traces

A stack trace exposes the internal package and class structure of the application. It shows which third-party libraries are in use and often their exact version numbers — an attacker can cross-reference those against CVE databases to find known vulnerabilities for those specific versions. Depending on where the exception was thrown, traces can also reveal server file paths and configuration details.

The GlobalExceptionMapper catches anything not handled by a more specific mapper and returns a generic message. The actual exception gets written to the server log. The client never sees anything actionable.

### Part 5 — Q3: Filters vs Logging in Every Method

Adding logging directly into every resource method means remembering to do it for every new method, and updating every method if the format changes. The logging code ends up scattered across the codebase mixed in with logic it has nothing to do with.

The LoggingFilter registers once with @Provider and runs automatically for every request and response. Resource methods do not need to know it exists. Any new endpoint added later gets logged without writing a single extra line.