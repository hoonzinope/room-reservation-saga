# AGENTS.md

## Project Overview

This repository is a Gradle multi-module Spring Boot project for a room reservation saga example.

- `outer-system`: reservation-facing API, reservation persistence, room lookup, and idempotency records.
- `inner-system`: inner service module.
- Root Gradle tasks can run checks across all modules.

## Working Rules

- Prefer small, focused changes that match the existing package structure.
- Do not move responsibilities across modules unless the task explicitly requires it.
- Keep DTO field names compatible with the current API shape unless the user asks for an API change.
- Put shared reservation codes and states under `outer-system/src/main/java/home/example/room_reserve_outer/data/type`.
- Keep service methods readable by naming private helpers after the business step they perform.
- Use comments sparingly. Add comments for business flow or non-obvious transactional/idempotency behavior, not for trivial assignments.

## Java Style

- Follow the current Spring style: constructor injection, `@Service`, repositories as final fields.
- Prefer enums over repeated string literals for domain status, operation, result, and error codes.
- If an enum is serialized in API responses, preserve existing wire values with `@JsonValue`.
- Avoid returning `null` from new public APIs when an explicit result type is practical. For existing private helper flow, keep changes conservative.
- Keep transactional boundaries at service use-case methods such as `book()`.

## Validation

Run the narrowest useful Gradle task after changes:

```sh
./gradlew :outer-system:test
```

Run the full test task before wrapping up broader changes:

```sh
./gradlew test
```

The project currently may have no test sources in some modules, so a successful build can report `NO-SOURCE` for tests.

## Git Hygiene

- Check the worktree before broad edits.
- Do not revert or overwrite unrelated user changes.
- Keep generated build output and IDE metadata out of commits unless explicitly requested.

## Commit Message Rules

When committing and pushing to GitHub, write commit messages that explain why
the change was made, not only what changed.

- Keep the subject line within 50 characters when practical.
- Do not end the subject line with a period.
- Use the imperative mood, such as `Fix`, not `Fixed`.
- Separate the subject and body with a blank line.
- In the body, explain the reason and intent of the change.
- Wrap body lines at about 72 characters.
- Use Conventional Commits type prefixes.
- write Korean.

Common commit types:

- `feat`: add a new feature.
- `fix`: fix a bug.
- `docs`: update documentation.
- `style`: change formatting without changing behavior.
- `refactor`: restructure code without adding or fixing behavior.
- `test`: add or update tests.
- `chore`: update build, tooling, package, or maintenance work.

Example:

```text
feat: 로그인 실패 시 에러 메시지 표시

기존에는 로그인 실패 시 아무런 피드백이 없어 사용자가
오류를 인지하기 어려웠음

- 사용자 경험 개선을 위해 화면 상단에 토스트 메시지 출력
- 아이디/비밀번호 불일치 및 서버 에러 분기 처리 포함
```
