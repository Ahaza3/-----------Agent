# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

电力负荷预测与智能告警 Agent — a 16-day summer training project building an AI Agent system integrating **load forecasting, anomaly detection, and intelligent alerting**. Power dispatchers can monitor grid load via a visualization dashboard, view AI-predicted trends, receive smart alerts, and query data through natural language.

- **Team**: 4 people (Java-oriented)
- **Timeline**: 16 days; currently Day 2 (design phase), no code written yet
- **Key metric**: 24h load forecast MAPE < 5%, alert delay < 5s, anomaly detection accuracy > 90%

## Architecture

**Single-module Spring Boot backend + React frontend**. Docker Compose only for Day 14 deployment — during development, Docker runs only MySQL + Redis, while Java and frontend run natively for fast iteration.

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
alert/       → ThresholdDetector + AlertTemplate (NFZ-3, fixed string templates, not LLM)
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
- **No Maven multi-module**: single `pom.xml`, single Jar — avoids build ordering issues and IDE import problems for a 16-day project
- **Training/inference split**: Models trained offline in Python (PyTorch/Prophet), exported as `.pt`/`.pkl`; loaded by Flask microservice (`ml/app.py`, port 5000), called by Java via OkHttp

### Database (MySQL 8.0 + Flyway migrations)

Key tables: `load_data`, `prediction_result`, `alert_event`, `alert_rule`, `model_version`, `conversation`, `user`. Flyway scripts in `backend/src/main/resources/db/migration/V1__init_schema.sql` and beyond. Index strategy optimizes for time-range queries on load data.

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

All design docs complete. Ready for Day 3-4 scaffolding: project initialization, database setup, CI/CD skeleton. See `docs/00-项目开发计划.md` for the full 16-day plan with daily task breakdowns.

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.