# Backend UPSGLAM Architecture & Operations

## Overview

This backend is composed of five reactive microservices plus a CUDA-enabled image processor behind an API gateway. All Java services use Spring Boot WebFlux (Reactor Mono/Flux), persist data in Firebase Firestore, and exchange authenticated requests via custom JWTs. Docker Compose orchestrates the full stack for local development.

| Service       | Port | Responsibilities                                                                  | Upstream Calls                                          |
| ------------- | ---- | --------------------------------------------------------------------------------- | ------------------------------------------------------- |
| auth-service  | 8081 | Firebase-backed registration/login, JWT issuance, user profile seeding            | user-service                                            |
| user-service  | 8085 | Profile CRUD, avatar history, follower/following lists (≤10 followers constraint) | —                                                       |
| post-service  | 8082 | Post CRUD, likes/comments, personalized feed based on following                   | user-service                                            |
| image-service | 8083 | Image upload, CUDA-based filtering via Python service, avatar uploads to Supabase | cuda_service, Supabase                                  |
| cuda_service  | 5000 | FastAPI GPU kernels: Sobel/Gaussian/Emboss/Mean/UPS highlight                     | —                                                       |
| api-gateway   | 8080 | Spring Cloud Gateway routing & JWT validation                                     | auth-service, post-service, image-service, user-service |

Inter-service traffic is routed directly in local development; the gateway aggregates routes for clients in production scenarios.

---

## Service Details

### auth-service (`auth-service/src/main/java/...`)

- **Endpoints**
  - `POST /auth/register` — creates Firebase user and seeds `user-service` profile.
  - `POST /auth/login` — delegates to Firebase Identity Toolkit, issues internal JWT, backfills profile if missing.
  - `GET /auth/test` — health probe.
- **Internals**
  - Uses `FirebaseAuth` Java SDK for account creation.
  - Calls `https://identitytoolkit.googleapis.com` via WebClient for password sign-in.
  - Delegates to `UserServiceClient` to `POST /users` when a profile is absent; handles 409 conflicts by fetching existing data.
  - Username generation strips diacritics and non-alphanumerics, falling back to UID seeds.
  - Default avatar URL is templated via `user-service.default-avatar-url` (UI Avatars).
- **Configuration**
  - `firebase.api-key` **must** be set to a valid Identity Toolkit REST API key (currently wired to `${JWT_SECRET}` in `application.yml`, which is a placeholder).
  - `user-service.base-url` defaults to `http://localhost:8085`.

### user-service (`user-service/src/main/java/...`)

- **Endpoints**
  - `POST /users` — create profile (requires `X-User-Uid`).
  - `PUT /users/{id}` — update profile fields & optionally append avatar.
  - `GET /users/{id}` — fetch profile document.
  - `POST /users/{id}/avatars` — append avatar URL to history.
  - `POST /users/{id}/followers` — current user follows `{id}`.
  - `DELETE /users/{id}/followers` — current user unfollows `{id}`.
  - `GET /users/{id}/followers` — follower ids + count.
  - `GET /users/{id}/following` — following ids + count.
- **Business Rules**
  - Follower lists are capped at 10 entries (`followUser` returns 400 if target is full).
  - Follower/following lists are stored as Firestore arrays and guarded against duplicates.
  - Avatar history is deduplicated (no consecutive duplicates).
- **Data Model** (`User`)
  - `id`, `name`, `username`, `bio`, `avatarUrl`, `avatarHistory[]`, `followers[]`, `following[]`, `createdAt`.
- **Persistence**
  - Firestore collection `users` using `UserRepository`; blocking `ApiFuture` wrapped in Reactor through `Mono.fromCompletionStage`.

### post-service (`post-service/src/main/java/...`)

- **Endpoints**
  - `POST /posts` — create post from uploaded image reference.
  - `GET /posts/feed` — personalized feed for `X-User-Uid` (self + following, newest first, max 50).
  - `GET /posts/{id}`, `GET /posts`, `GET /posts/user/{userId}` — fetch operations.
  - `PUT /posts/{id}` — update content/image/metadata if caller owns post.
  - `DELETE /posts/{id}` — owner-only deletion.
  - `POST /posts/{id}/likes` / `DELETE /posts/{id}/likes` — toggle likes.
  - `POST /posts/{id}/comments` — append comment with generated UUID.
- **Feed Composition**
  - Resolves following IDs via `user-service` (`/users/{id}/following`), unions with requesting user ID, loads posts per user, sorts by `createdAt` desc, limits to 50, enriches with author & commenter profile data.
  - Known issue: a 500 error occurs when invoking `/posts/feed` locally. No stack trace is available yet; likely caused by Firestore array union on missing doc or WebClient error propagation from `getFollowingIds`. Investigation pending.
- **Reactivity**
  - Uses Reactor `Flux`/`Mono` with sequential post hydration to avoid starving the event loop.
- **Firestore Repository**
  - Collection `posts`, storing `likes[]` and `comments[]` as arrays (comments as embedded maps).
  - Utility `monoFromApiFuture` centralizes async bridging on boundedElastic scheduler.

### image-service (`image-service/src/main/java/...`)

- **Endpoints**
  - `POST /images/upload` (multipart) — uploads original file to Supabase, submits to CUDA service with `mask` (kernel size) & `filter`, uploads processed result, returns both URLs.
  - `POST /images/avatar` (multipart) — stores avatar for current user (requires `X-User-Uid`) and returns Supabase URL + timestamp.
- **Supabase Integration**
  - `supabaseClient` posts raw bytes to `/storage/v1/object/{bucket}/{filename}` with `x-upsert=true` and `apikey` header.
  - Filenames include UUID-based prefixes (`original` vs `processed`, avatars under `avatars/{userId}/`).
- **Python CUDA Bridge**
  - `pythonClient` posts form-data (`image`, `filter_type`, `kernel_size`) to `cuda_service` and expects raw PNG bytes in response.
  - Buffer decoding uses `DataBufferUtils.join` on boundedElastic scheduler to avoid blocking event loop.
- **Validation**
  - Only JPEG/PNG accepted; avatar size limited to 5MB.

### cuda_service (`cuda_service/`)

- **FastAPI** (`main.py`)
  - Routes: `POST /api/convolucion` — runs GPU kernels, returns processed PNG + CUDA metadata in headers.
  - CORS set to allow local origins.
- **Kernel Coverage** (`services/convolution_service.py` + `cuda/`)
  - Supports filters: `gaussian`, `sobel`, `emboss`, `mean`, `ups` (custom institutional filter preserving yellow/blue hues).
  - Validates kernel size fits image dimensions; provides metadata (threads, blocks, GPU time).
  - `mask_builder.py` reproduces preset/custom kernels matching legacy CUDA scripts.

### api-gateway (`api-gateway/src/main/resources/application.yml`)

- Routes `/auth/**`, `/posts/**`, `/images/**`, `/users/**` to respective services, default filters preserve host headers.
- JWT validation relies on shared `JWT_SECRET` environment variable (implementation not included in repo snippet).

---

## Data Flow Snapshot

1. **User onboarding**
   1. Client calls `POST /auth/register` with email/password/username.
   2. `auth-service` creates Firebase credentials, generates slugged username, invokes `user-service` to persist profile (followers/following start empty).
2. **Following**
   1. Caller uses `POST /users/{id}/followers` with header `X-User-Uid`.
   2. `user-service` updates both follower/following arrays, enforcing the 10-follower cap.
   3. `post-service` feed subsequently includes `{id}` posts for follower.
3. **Content publishing**
   1. Client uploads image & mask/filter to `image-service` → Supabase stores assets → CUDA service filters image.
   2. Response includes `originalUrl` and `processedUrl`; client uses `processedUrl` as `imageUrl` in `POST /posts`.
   3. `post-service` stores metadata and later resolves author & comment profiles via `user-service` (fallback if user-service unavailable).

---

## Deployment & Environment

### Docker Compose (`docker-compose.yml`)

- Mounts `firebase-key.json` into Java containers as `/secrets/firebase-key.json`.
- Environment variables:
  - `JWT_SECRET` shared by auth-service and api-gateway.
  - `USER_SERVICE_URL`, `POST_SERVICE_URL`, `IMAGE_SERVICE_URL`, `AUTH_SERVICE_URL` exported for service discovery.
  - `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_BUCKET`, `PYTHON_URL` for image-service.
- Network `upsglam-net` exposes services on host ports for manual testing (`8080` gateway, `8081-8085`, `5000`).

### Manual Development Workflow

1. Install Java 17, Gradle wrapper, Python 3.10+, CUDA toolkit, and `pip install -r cuda_service/requirements.txt`.
2. Provide Firebase service account JSON at repo root (`firebase-key.json`).
3. Export required secrets:
   ```sh
   export JWT_SECRET="<secure-random>"
   export SUPABASE_URL="https://..."
   export SUPABASE_ANON_KEY="<anon-key>"
   export SUPABASE_BUCKET="images"
   ```
4. Start CUDA service: `uvicorn cuda_service.main:app --reload --port 5000` (or via Docker Compose).
5. Launch Java services with `./gradlew bootRun` inside each module (ensure ports are free).
6. Optionally start everything with `docker compose up --build`.

---

## Known Issues & Follow-Up Work

- **Feed 500**: `/posts/feed` currently returns HTTP 500. Capture logs by running `./gradlew bootRun` directly after ensuring no other process occupies the port. Add diagnostic logging around `userServiceClient.getFollowingIds` and Firestore lookups to isolate the failure path.
- **Firebase API Key**: `auth-service/src/main/resources/application.yml` maps `firebase.api-key` to `${JWT_SECRET}`. Replace with a true Identity Toolkit API key before deploying.
- **Supabase Credentials in Source**: Public anon key is committed; rotate secrets and inject via environment variables only.
- **Follower Limit Hardcode**: Limit of 10 followers is fixed in code. Document business rationale or extract to configuration.
- **Error Propagation**: WebClient fallbacks sometimes swallow errors (e.g., `getUserProfile` returns empty). Consider structured logging/metrics to detect degraded dependencies.

---

## Testing Checklist

- `auth-service`: manual `curl` for register/login; verify JWT payload and profile creation (Firestore `users/{uid}`).
- `user-service`: follow/unfollow cycle using curl, ensure follower cap message triggers at 10, and lists reflect changes.
- `post-service`: create posts, like/unlike, add comments, and verify feed results once 500 issue fixed.
- `image-service`: upload sample image using multipart form; confirm Supabase URLs respond (public bucket) and avatar uploads append history.
- `cuda_service`: call `POST /api/convolucion` directly with various filters; validate headers `X-Width`, `X-GPU-Time-ms`.
- `api-gateway`: confirm routes proxy correctly after setting `JWT_SECRET` and CORS requirements.

---

## Next Steps

1. Investigate `/posts/feed` failure with targeted logging and integration tests mocking `user-service` responses.
2. Externalize configuration secrets (Firebase API key, Supabase credentials) to avoid committing placeholders.
3. Add automated tests: unit tests for follower logic limits, repository mocking for post-service feed, and contract tests for WebClient clients.
4. Document API Gateway JWT verification strategy and how clients should forward `Authorization` headers downstream.
5. Consider centralizing shared DTOs (e.g., `UserProfile`) or generating OpenAPI specs for each service to aid frontend integration.
