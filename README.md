# Real-time Auction Backend (Spring Boot & MongoDB)

This is the backend of the Real-time Cricket Team Auction Platform. It uses Spring Boot, Spring Data MongoDB, and WebSockets to provide an optimized, lightning-fast, and fully real-time bidding experience.

## Prerequisites

To run this backend locally, you will need:
1. **Java Development Kit (JDK 17 or later)**:
   - Download and install from [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17) or [Oracle](https://www.oracle.com/java/technologies/downloads/).
   - Ensure the `JAVA_HOME` environment variable is set and `java` is accessible from your command prompt/terminal.
2. **MongoDB Community Server**:
   - Download and install from [MongoDB Download Center](https://www.mongodb.com/try/download/community).
   - Alternatively, run via Docker: `docker run -d -p 27017:27017 --name local-mongo mongo:latest`

## Running the Backend

1. **Start MongoDB**: Make sure your local MongoDB instance is running at `mongodb://localhost:27017`
2. **Build and Run**:
   Open a terminal in the `backend/` directory and run:
   ```cmd
   mvnw spring-boot:run
   ```
   *(Note: The Maven Wrapper `mvnw` will automatically download Maven if it's not installed on your machine. You only need a JDK installed.)*

## Backend Configuration

You can update configuration settings in `src/main/resources/application.properties`:
- `server.port`: The port on which the backend runs (defaults to `8080`).
- `spring.data.mongodb.uri`: Connection string to your MongoDB server (defaults to `mongodb://localhost:27017/auction`).

## Core API Endpoints

- **Authentication**: `POST http://localhost:8080/api/auth/login`
- **Players REST CRUD**: `GET|POST|PUT|DELETE http://localhost:8080/api/players`
- **Teams REST API**: `GET http://localhost:8080/api/teams`
- **Auction Operations**:
  - Reset Auction: `POST http://localhost:8080/api/auction/reset`
  - Fetch lots: `GET http://localhost:8080/api/auction/lots`
  - Live bid feed events: `GET http://localhost:8080/api/auction/events`
  - Place a Bid: `POST http://localhost:8080/api/auction/bids`
  - Sell Player: `POST http://localhost:8080/api/auction/lots/sell`
  - Mark Unsold: `POST http://localhost:8080/api/auction/lots/unsold`
  - Update Auction Status: `POST http://localhost:8080/api/auction/status`

## WebSockets

Real-time bid and lot status updates are broadcasted to all connected clients over:
`ws://localhost:8080/ws/auction`
