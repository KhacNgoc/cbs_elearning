# User entities and transaction boundaries

The security filter loads a `CustomUserDetails` in its own transaction. Its `User`
is detached by the time a controller or service handles the request. Adding
`@Transactional` to a downstream method does not reattach that principal.

## Rules for application code

- Use the principal for identity and the immutable permission snapshot. Do not
  read its learning collections or mutate its entity relationships.
- `AuthorizationService.current()` reloads the principal's immutable user ID in
  the current persistence context and checks the current account status. Keep a
  service transaction open through relationship access, policy checks and DTO
  mapping. A transaction on `current()` alone does not extend to its caller.
- Services receiving a username should load their user within the transaction
  performing the operation. Pass IDs or scalar DTOs across asynchronous or
  external-service boundaries.
- A call from one method to another on the same object bypasses Spring's
  transactional proxy. Put the transaction on the public entry point actually
  called by the controller, or delegate to a separate transactional service.
- For AI calls, perform learning authorization in a short transaction before the
  HTTP call. Do not hold a database transaction open while waiting for an answer.
- Avoid changing learning collections to EAGER or enabling Open Session in View
  as a workaround. Those settings can conceal missing transaction boundaries.

## Audit and corrections

- Shared authorization: reload the user instead of returning the detached
  security principal. This covers asset access, course images, administrative
  operations, scopes and role management using `AuthorizationService`.
- Admin options: keep permission filtering and nested DTO mapping within one
  read-only transaction.
- Video streaming: transact `authorizeStreamPath`, which internally calls the
  authorization method on the same instance.
- RAG chat: run course authorization in `TrainingStructureService.requireCourse`
  so learning collections are loaded in a short transaction, separate from the
  external AI request.
- Authentication metadata: give legacy users an empty role-policy snapshot
  before returning from `UserDetailsServiceImpl`; reading role names later must
  not trigger the detached `assignedRoles` collection.
- Existing course, lesson, quiz, progress, learner deadline and workspace flows
  already load users inside their service/controller transactions. Their
  transaction boundaries were retained.

## Regression checks

`DetachedPrincipalIntegrationTest` deliberately runs without a test transaction
and with `spring.jpa.open-in-view=false`. It covers detached user reload, PDF
access, registration revocation, sequential lesson gates, account locking, dynamic/legacy authentication metadata, admin options,
video streaming, and chat authorization before the external HTTP call.

Run backend checks with JDK 17: `mvn test` from `CoreTrainingAPI`.
