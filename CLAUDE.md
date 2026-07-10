# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

电力负荷预测与智能告警 Agent — a 15-day summer training project building an AI Agent system integrating **load forecasting, anomaly detection, and intelligent alerting**. Power dispatchers can monitor grid load via a visualization dashboard, view AI-predicted trends, receive smart alerts, and query data through natural language.

- **Team**: 4-5 people (Java-oriented)
- **Timeline**: 15 days; currently Day 1-2 (design phase), no code written yet
- **Key metric**: 24h load forecast MAPE < 5%, alert delay < 5s, anomaly detection accuracy > 90%

## Architecture

**Single-module Spring Boot backend + React frontend**. Docker Compose only for Day 13 deployment — during development, Docker runs only MySQL + Redis, while Java and frontend run natively for fast iteration.

```
Browser → Nginx (:80) → React SPA (static) + Spring Boot (:8080, /api/*, /ws/*)
                         Backend → MySQL 8.0 + Redis 7
                         Backend → LLM API (DeepSeek/Qwen) via OkHttp
                         Backend → Python Flask inference (:5000) via OkHttp
```

### Backend Layering (Single-Module Spring Boot)

```
controller/  → @RestController, param validation (@Valid), returns R<T>
service/     → interfaces + impl/; business logic, @Transactional
mapper/      → MyBatis-Plus BaseMapper + custom @Select
entity/      → @TableName POJOs
dto/         → request/ + response/ (separate from entities)
common/      → R<T>, GlobalExceptionHandler, constants
config/      → Redis, Swagger, Async, WebSocket, CORS configs
agent/       → LLM Agent: AgentCore (~150 lines) + ToolRegistry + 2 tools (QueryLoadTool, GetStatsTool)
ml/          → ModelInferenceService interface → FlaskInferenceService (HTTP call to Python)
alert/       → ThresholdDetector (P0) + AlertTemplate (fixed string templates, not LLM)
websocket/   → STOMP over WebSocket for real-time dashboard push
```

ML scripts live independently in `ml/` at project root:
```
ml/
├── app.py               # Flask inference microservice (~60 lines)
├── train_lstm.py         # LSTM training (P1)
├── train_prophet.py      # Prophet baseline (P0)
├── generate_mock_data.py # Mock data generator
└── requirements.txt
```

### Key Design Patterns

- **Strategy pattern** for ML inference: `ModelInferenceService` interface → `FlaskInferenceService` (primary, OkHttp calls Python), DJL optional only if environment cooperates
- **Alert template, not LLM** for alert text: `AlertTemplate.generate(level, current, threshold)` returns pre-written Chinese strings — zero latency, zero LLM cost
- **Tool Registry** for Agent Function Calling: `ToolRegistry` scans Spring beans implementing `Tool`; LLM returns `function_call` → registry dispatches → result fed back to LLM; P0 needs only 2 tools
- **SSE streaming** for Agent chat: `SseEmitter` in Spring, events: `thinking` → `text` → `chart` → `done`
- **No Maven multi-module**: single `pom.xml`, single Jar — avoids build ordering issues and IDE import problems for a 15-day project
- **Training/inference split**: Models trained offline in Python (PyTorch/Prophet), exported as `.pt`/`.pkl`; loaded by Flask microservice (`ml/app.py`, port 5000), called by Java via OkHttp

### Database (MySQL 8.0 + Flyway migrations)

Key tables: `load_data`, `prediction_result`, `alert_event`, `alert_rule`, `model_version`, `user`. Flyway scripts in `backend/src/main/resources/db/migration/V1__init_schema.sql` and beyond. Index strategy optimizes for time-range queries on load data.

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend | React + TypeScript + Vite | 18 / 5 / 5 |
| UI | Ant Design + Tailwind CSS | 5 / 3 |
| Charts | ECharts (via echarts-for-react) | 5 |
| State | Zustand | 4 |
| Backend | Spring Boot + JDK 17 (single module) | 3.3.x |
| ORM | MyBatis-Plus | 3.5.7 |
| DB Migration | Flyway | 10.15 |
| API Docs | SpringDoc OpenAPI + Knife4j | 2.6 / 4.5 |
| Cache | Redis + Spring Data Redis | 7 |
| HTTP Client | OkHttp | 4.12 |
| ML Training | Python (PyTorch + Prophet + pandas) | — |
| ML Inference | Python Flask microservice (port 5000) | — |
| LLM | DeepSeek / 通义千问 (OpenAI-compatible API) | — |
| Testing | JUnit 5 + Mockito + H2 / Vitest | — |
| Lint | ESLint + Prettier (frontend only) | 9 / 3 |
| Deploy | Docker Compose (Day 13 only) | — |
| CI | GitHub Actions (build + test) | — |

## Common Commands (planned — no code yet)

```bash
# Backend (single module)
cd backend
mvn spring-boot:run                                          # Start backend (:8080)
mvn test                                                      # Run all tests
mvn test -Dtest=AlertServiceImplTest                          # Run single test class
mvn package -DskipTests                                       # Build JAR

# Frontend
cd frontend
npm run dev                                                   # Vite dev server (:5173)
npm run build                                                 # Production build
npm run lint                                                  # ESLint
npx vitest                                                    # Run tests

# ML Inference
cd ml
python app.py                                                 # Start Flask inference (:5000)
python generate_mock_data.py                                  # Generate mock data
python train_prophet.py                                       # Train Prophet baseline
python train_lstm.py                                          # Train LSTM (P1)

# Database (Docker)
docker run -d --name mysql-dev -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=power_load mysql:8.0
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine

# Deployment (Day 13 only)
docker-compose up -d                                          # Start all 5 services
docker-compose logs -f backend                                # Tail backend logs
```

## Git Conventions

- **Branch strategy**: `main ← develop ← feature/*`
- **Commit style**: [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)
- **PR required** before merging into `develop` or `main`; must pass Code Review

## API Design Patterns

- **Base path**: `/api/v1/`
- **Unified response**: `R<T>` wrapper — `{ code: 0, message: "success", data: T, timestamp: long }`
- **Auth**: JWT in `Authorization: Bearer <token>` header; access token 30min, refresh token 7d
- **Swagger UI**: `/api/doc.html` (Knife4j enhanced)
- **Health check**: `/actuator/health`
- **WebSocket**: `/ws/dashboard` (STOMP over SockJS), topics at `/topic/load`, `/topic/alerts`, `/topic/predictions`

## Environment & Configuration

- **`.env`** file at project root for secrets (LLM API keys, DB passwords) — `.gitignore`'d
- **Spring profiles**: `application-{dev,prod}.yml`; override with `SPRING_PROFILES_ACTIVE`
- **Docker Compose** passes env vars to containers; `SPRING_PROFILES_ACTIVE=docker` in container

## ML Pipeline (Training → Inference)

1. **Training** (offline, manual): `generate_mock_data.py` → CSV → pandas feature engineering → Prophet / LSTM training → export `.pt` or `.pkl`
2. **Inference** (online, Python Flask `ml/app.py`): loads model at startup → exposes `POST /predict/batch` → Java `FlaskInferenceService` calls it via OkHttp → results stored in `prediction_result` table → WebSocket push to frontend
3. **DJL fallback**: only attempt if Python environment is problematic; verify on Day 3, switch if it doesn't work within half a day

## Agent Interaction Flow

1. User sends natural language query → `POST /api/v1/agent/chat` (SSE)
2. `AgentCore` builds messages (system prompt + 2 tool definitions + user msg)
3. LLM returns `function_call` → `ToolRegistry` dispatches to `QueryLoadTool` or `GetStatsTool`
4. Tool queries DB via MyBatis-Plus and returns structured result
5. LLM receives result and generates natural language response + optional ECharts config
6. Response streamed via SSE (`thinking` → `text` → `chart` → `done`)
7. **Alert文案 uses fixed templates**, not LLM — `AlertTemplate.generate(level, current, threshold)` returns pre-written strings

## Current Status

All design docs complete. Ready for Day 3-4 scaffolding: project initialization, database setup, CI/CD skeleton. See `docs/00-项目开发计划.md` for the full 15-day plan with daily task breakdowns.
