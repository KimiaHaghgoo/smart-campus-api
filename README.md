markdown# Smart Campus API

## Overview

This is the backend REST API I built for the 5COSC022W Client-Server Architectures coursework at the University of Westminster. The system manages rooms and sensors across a university campus, with support for tracking historical sensor readings.

The idea behind the structure is straightforward — rooms are physical spaces on campus like labs and libraries, sensors are hardware devices sitting inside those rooms measuring things like temperature and CO2 levels, and every sensor keeps a log of its past readings that can be queried at any time.

The URL layout reflects this hierarchy directly:
/api/v1/                               → discovery endpoint
/api/v1/rooms                          → all rooms
/api/v1/rooms/{roomId}                 → one specific room
/api/v1/sensors                        → all sensors
/api/v1/sensors/{sensorId}/readings    → reading history for a sensor

**Technology stack:**
- JAX-RS with Jersey 2.41 as the REST framework
- Apache Tomcat 9 as the servlet container
- Apache NetBeans 29 as the IDE
- Jackson for automatic JSON serialisation
- In-memory HashMaps for data storage — no database

A few design decisions worth noting:
- All data lives in a singleton DataStore shared across the whole application, so nothing gets lost between requests
- Every error case returns a clean JSON response — raw Java stack traces never reach the client
- A logging filter automatically captures every request and response without touching individual resource methods
- The sub-resource locator pattern is used for readings to keep SensorResource and SensorReadingResource as separate focused classes

---

## How to Build and Run

### What you need

- **Java 11 or higher** — check with `java -version`
- **Maven 3.x** — check with `mvn -version`
- **Apache Tomcat 9** — on Mac: `brew install tomcat@9`
- **Apache NetBeans 29**

### Step 1 — Clone the repository

Open Terminal and run:

```bash
git clone https://github.com/KimiaHaghgoo/smart-campus-api.git
```

### Step 2 — Open the project in NetBeans

- Open NetBeans
- Go to File → Open Project
- Navigate to the folder you just cloned and select it
- Click Open

### Step 3 — Build the project

Right-click the project name in the left Projects panel and select **Clean and Build**.

Wait for the output panel at the bottom to show `BUILD SUCCESS`. This compiles all the Java files and packages everything into a WAR file that Tomcat can deploy.

### Step 4 — Register Tomcat in NetBeans (first time only)

If Tomcat isn't registered yet:
- Go to Tools → Servers → Add Server
- Select Apache Tomcat or TomEE
- Set the Server Location to `/opt/homebrew/opt/tomcat@9/libexec`
- Set Username to `admin` and Password to `admin`
- Click Finish

### Step 5 — Run the project

Right-click the project → **Run**

NetBeans will deploy the WAR to Tomcat and open a browser window automatically. The API is now live at:
http://localhost:8080/smart-campus-api/api/v1/

### Step 6 — Verify it's working

Open Postman or your browser and hit:
http://localhost:8080/smart-campus-api/api/v1/rooms

You should get back a JSON array with two pre-loaded rooms — LIB-301 and LAB-101.

### Step 7 — Stop the server

Right-click the project in NetBeans → **Stop**, or click the red Stop button in the toolbar.

---

## curl Examples

```bash
# 1. Discovery — see API metadata and available resource links
curl http://localhost:8080/smart-campus-api/api/v1/

# 2. Get all rooms
curl http://localhost:8080/smart-campus-api/api/v1/rooms

# 3. Get one specific room
curl http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301

# 4. Create a new room
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"HALL-01","name":"Main Hall","capacity":200}'

# 5. Try to delete a room that still has sensors — returns 409
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301

# 6. Delete a room with no sensors — returns 204
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/HALL-01

# 7. Get all sensors
curl http://localhost:8080/smart-campus-api/api/v1/sensors

# 8. Filter sensors by type
curl "http://localhost:8080/smart-campus-api/api/v1/sensors?type=CO2"

# 9. Register a new sensor (roomId must exist or you get a 422)
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":0.0,"roomId":"LIB-301"}'

# 10. Post a new reading to a sensor
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.5}'

# 11. Get the full reading history for a sensor
curl http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings

# 12. Try posting a reading to a MAINTENANCE sensor — returns 403
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":99.9}'
```

---

## Report Answers

### Part 1 — Q1: JAX-RS Resource Lifecycle

By default JAX-RS creates a fresh instance of each resource class for every incoming request. Ten simultaneous requests means ten separate RoomResource objects. This is fine for handling the requests themselves, but it means you cannot store any data inside the resource class — it gets thrown away the moment the request is done.

To get around this I put all the data in a separate DataStore class that follows the singleton pattern. There is only ever one DataStore alive at a time, and every resource class fetches that same instance using DataStore.getInstance(). The HashMaps inside it survive between requests because the object itself never gets destroyed.

The trade-off is that concurrent requests could hit the same HashMap simultaneously and cause a race condition — two requests trying to write to the same entry at the same time could corrupt the data. For this coursework that is not a practical concern, but in a production system you would replace HashMap with ConcurrentHashMap or add synchronized blocks around the critical sections.

### Part 1 — Q2: HATEOAS

HATEOAS means the API response tells the client where it can go next, rather than the client having to know the URL structure in advance. The discovery endpoint at /api/v1/ returns links like `/api/v1/rooms` and `/api/v1/sensors` directly in the JSON body. A new client can hit that one endpoint and discover the entire API from there without reading any documentation.

The advantage over static documentation is resilience to change. If the URL structure ever gets updated, a client that navigates by following links in responses will keep working. A client that hardcoded the URLs from a docs page will break. It also lowers the barrier for developers integrating with the API for the first time — they do not need to read through a full spec to start making requests.

### Part 2 — Q1: Full Objects vs IDs in Lists

If the list endpoint only returns IDs, the client has to fire off a separate GET request for each one to retrieve any useful data. For a list of 50 rooms that is 51 HTTP round-trips just to populate a simple table. That adds up in both latency and server load.

Returning full objects means one request gets everything, but the response payload grows proportionally with the number of records. For this API returning full objects is the right call — a university campus has a bounded number of rooms and the payloads stay small. At much larger scale you would want pagination and probably a trimmed-down summary object rather than the full room, to keep response sizes manageable.

### Part 2 — Q2: DELETE Idempotency

Yes, DELETE is idempotent in this implementation. The first call on an existing room removes it and returns 204 No Content. A second identical call on the same room finds nothing there and returns 404 Not Found. The server state is identical after both calls — the room does not exist either way. The response code is different but that does not affect idempotency, which is defined by whether the server state changes, not whether the response changes.

### Part 3 — Q1: @Consumes and Wrong Content-Type

The `@Consumes(MediaType.APPLICATION_JSON)` annotation declares that this method only accepts requests with a Content-Type of application/json. If a client sends the request with Content-Type: text/plain or Content-Type: application/xml, Jersey intercepts it before the method is ever called and returns a 415 Unsupported Media Type response automatically. No code inside the method needs to handle this — the framework enforces it at the routing level. This is useful because incorrectly formatted requests never make it into the business logic at all.

### Part 3 — Q2: Query Params vs Path Segments for Filtering

Using a path segment like `/sensors/type/CO2` implies that CO2 is a resource with its own identity — something you can GET, POST, or DELETE as a thing in itself. It is not. It is a filter condition being applied to the sensors collection. Query parameters are semantically correct for this because they describe how to narrow down a collection, not what resource to retrieve.

They are also optional by design, so GET /sensors and GET /sensors?type=CO2 both route to the same method without any extra configuration. If you need to filter on multiple things — say type and status both — query params handle that cleanly with `?type=CO2&status=ACTIVE`. The path-based equivalent would be something like `/sensors/type/CO2/status/ACTIVE`, which is rigid, hard to read, and painful to extend.

### Part 4 — Q1: Sub-Resource Locator Pattern

Rather than putting the readings logic inside SensorResource and ending up with one class that handles two completely different concerns, I used a sub-resource locator. SensorResource has a method annotated with `@Path("/{sensorId}/readings")` that simply instantiates and returns a SensorReadingResource object. Jersey then routes any request under that path to that class and handles it there.

The benefit is that each class stays focused on one thing. SensorResource deals with sensors, SensorReadingResource deals with readings. If the readings logic needs to change — say we want to add filtering by date range or limit the history size — only SensorReadingResource needs touching. In a large API where resources have many levels of nesting, putting everything in one controller class makes the code very difficult to read, test, and maintain.

### Part 5 — Q1: 422 vs 404 for Missing Room Reference

404 Not Found means the URL the client requested does not exist on the server. But POST /api/v1/sensors absolutely does exist — the endpoint is registered, working, and received the request fine. The issue is not a missing URL, it is that the roomId value inside the JSON body points to a room that is not in the system. The request structure is valid, the JSON is well-formed, the data just contains a broken reference.

422 Unprocessable Entity is designed exactly for this situation — the server understood the request, the syntax is correct, but the content fails a semantic validation check. Using 404 here would mislead the client into thinking the sensors endpoint itself is gone, which is not true and would make debugging unnecessarily confusing.

### Part 5 — Q2: Security Risks of Exposing Stack Traces

A stack trace is essentially a free map of the codebase handed to whoever is on the other end of the request. It exposes every class name and package in the call chain, which reveals the internal project structure. It shows exactly which third-party libraries are being used and in many cases their version numbers — an attacker can look those up in CVE databases and identify known vulnerabilities for those exact versions.

Depending on where the exception was thrown, stack traces can also reveal server file paths, environment configuration, and database query strings. None of that should ever be visible to an external client.

The GlobalExceptionMapper catches any exception that is not handled by a more specific mapper and returns a plain generic message. The actual exception details get written to the server log where only developers with access to the server can read them. The client gets nothing an attacker could act on.

### Part 5 — Q3: Filters vs Logging in Every Method

Adding a Logger.info() call inside every resource method means remembering to do it every time a new method is written. It also means if the log format ever needs updating, every single method has to be touched. The logging code ends up scattered across the entire codebase, mixed in with logic that has nothing to do with it.

The LoggingFilter implements ContainerRequestFilter and ContainerResponseFilter in one class, registers once with the @Provider annotation, and runs automatically for every request and response from that point forward. The resource methods do not need to know it exists. Adding a new endpoint in future gives you logging for free without writing a single extra line. The entire logging concern lives in one place, which is the only place that ever needs to change.