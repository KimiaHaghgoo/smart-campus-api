# smart-campus-api
**5. Stop**

Right-click the project → Stop, or hit the Stop button in the toolbar.

---

## curl Examples

```bash
# Discovery — check the API is up and see available resources
curl http://localhost:8080/smart-campus-api/api/v1/

# Get all rooms
curl http://localhost:8080/smart-campus-api/api/v1/rooms

# Get one room by ID
curl http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301

# Create a new room
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"HALL-01","name":"Main Hall","capacity":200}'

# Delete a room (returns 409 if sensors are still assigned to it)
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/HALL-01

# Get all sensors
curl http://localhost:8080/smart-campus-api/api/v1/sensors

# Filter sensors by type
curl "http://localhost:8080/smart-campus-api/api/v1/sensors?type=CO2"

# Register a new sensor (roomId must exist or you get a 422)
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":0.0,"roomId":"LIB-301"}'

# Post a new reading to a sensor
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.5}'

# Get the full reading history for a sensor
curl http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings
```

---

## Report Answers

### Part 1 — Q1: JAX-RS Resource Lifecycle

By default JAX-RS creates a fresh instance of each resource class for every incoming request. Ten simultaneous requests means ten separate RoomResource objects. This is fine for handling requests, but it means you cannot store anything inside the resource class itself — it gets discarded after each request finishes.

To work around this I put all the data in a separate DataStore class that uses the singleton pattern. There's only ever one DataStore alive at a time and all the resource classes grab the same instance, so the HashMaps survive between requests.

The downside is that concurrent requests could theoretically hit the same HashMap at the same time and cause a race condition. For this coursework that's not a concern, but in a real system you'd swap HashMap for ConcurrentHashMap or add synchronization.

### Part 1 — Q2: HATEOAS

HATEOAS means the API response tells you where to go next, rather than you having to look it up in documentation. The discovery endpoint returns links to `/api/v1/rooms` and `/api/v1/sensors` directly in the JSON — a client can just follow those rather than hardcoding URLs.

The practical benefit is that if the URL structure ever changes, clients that navigate by following links won't break. Clients that hardcode URLs from static docs will. It also makes the API easier to explore from scratch — you can start at the root and find everything from there without reading a manual.

### Part 2 — Q1: Full Objects vs IDs in Lists

Returning only IDs forces the client to make a separate request for each one to get any useful information. For a list of 50 rooms that's 51 requests total, which is wasteful. Returning full objects means one request gets everything, but the response payload grows with the number of rooms.

For this project returning full objects made more sense — a university campus has a manageable number of rooms and the overhead is small. At larger scale you'd want pagination and probably a lightweight summary object rather than the full thing.

### Part 2 — Q2: DELETE Idempotency

Yes, DELETE is idempotent here. The first time you call DELETE on a room that exists, it gets removed and you get back 204 No Content. Call it again and the room is already gone, so you get 404 Not Found. The state of the server is the same after both calls — the room doesn't exist either way. The response code changes, but idempotency is about server state, not response codes.

### Part 3 — Q1: @Consumes and Wrong Content-Type

`@Consumes(MediaType.APPLICATION_JSON)` tells Jersey this endpoint only accepts JSON. If a request comes in with `Content-Type: text/plain` or `application/xml`, Jersey rejects it automatically before it ever reaches the method and sends back 415 Unsupported Media Type. No custom code needed for this — the framework handles it entirely, which means badly formatted requests never reach the business logic at all.

### Part 3 — Q2: Query Params vs Path Segments for Filtering

Putting the filter value in the path like `/sensors/type/CO2` implies that `CO2` is itself a resource with its own identity. It isn't — it's a filter condition. Query parameters are the right tool for filtering because they're optional by nature, so `GET /sensors` and `GET /sensors?type=CO2` both work through the same endpoint without any extra routing.

Path-based filtering also falls apart quickly when you need multiple filters. `?type=CO2&status=ACTIVE` is clean and obvious. The path equivalent would be something like `/sensors/type/CO2/status/ACTIVE` which is awkward and hard to extend.

### Part 4 — Q1: Sub-Resource Locator Pattern

Rather than handling `/sensors/{id}/readings` directly inside SensorResource and making that class responsible for two completely different things, I used a sub-resource locator. SensorResource just hands off anything under `/{sensorId}/readings` to SensorReadingResource, which deals with it entirely on its own.

The result is that each class has one clear job. If the readings logic needs to change, only SensorReadingResource needs touching. In a large API this matters a lot — cramming every nested path into one controller class makes it very hard to read and even harder to debug.

### Part 5 — Q1: 422 vs 404 for Missing Room Reference

404 means the endpoint URL doesn't exist. But `POST /api/v1/sensors` absolutely does exist — it's working fine. The problem is that the roomId inside the request body points to a room that isn't in the system. The URL is valid, the JSON structure is valid, the data just references something that doesn't exist. That's a semantic problem with the content, not a missing endpoint, which is exactly what 422 Unprocessable Entity is for.

Returning 404 here would mislead the client into thinking the sensors endpoint itself is broken, which it isn't.

### Part 5 — Q2: Stack Traces and Security

A stack trace hands an attacker a map of the codebase. It shows every class name and package, which reveals the project structure. It shows exactly which libraries are in use and often their version numbers — the attacker can then look up CVEs for those specific versions and know exactly which exploits might work. In some cases stack traces also expose file paths on the server or configuration values depending on where the exception was thrown.

The GlobalExceptionMapper catches anything that isn't handled by a more specific mapper and returns a plain "An unexpected error occurred" message. The real exception gets logged on the server where only developers can see it, and the client gets nothing useful to an attacker.

### Part 5 — Q3: Filters vs Logging in Every Method

Adding Logger.info() to every resource method means remembering to add it every time a new method is written, and updating every method if the log format needs changing. It also clutters the methods with code that has nothing to do with their actual job.

The LoggingFilter registers once and runs for every request and response automatically. The resource methods don't need to know it exists. New endpoints get logged for free. The logging logic lives in one place, which is the only place that needs changing if something about it needs to change.