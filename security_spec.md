# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Strict Multi-Tenant Ownership**: Every document in `/cad_projects/{projectId}` and `/printer_profiles/{printerId}` MUST belong to an authenticated user (`request.auth != null`) and have `userId == request.auth.uid`.
2. **Immutable Ownership & Creation Timestamp**: On update, `userId` and `createdAt` MUST remain identical to `resource.data.userId` and `resource.data.createdAt`.
3. **Strict Key Allowlisting & Schema Bounds**: Every `create` and `update` operation MUST pass `isValidCadProject(incoming())` or `isValidPrinterProfile(incoming())` and strictly constrain keys using `hasAll` and `hasOnly` / `affectedKeys().hasOnly(...)`.
4. **Query Enforcer**: Every `list` operation MUST verify `resource.data.userId == request.auth.uid`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("cad_projects").get()` -> Reject (`PERMISSION_DENIED`).
2. **Cross-User Read**: Alice reading Bob's `cad_projects/bob_proj` -> Reject (`PERMISSION_DENIED`).
3. **Unfiltered List Query**: Alice calling `cad_projects.get()` without `.where("userId", "==", aliceUid)` -> Reject (`PERMISSION_DENIED`).
4. **Identity Spoofing on Create**: Alice creating a project with `userId: "bob_456"` -> Reject (`PERMISSION_DENIED`).
5. **Ownership Hijack on Update**: Alice updating `userId` on her project to `"bob_456"` -> Reject (`PERMISSION_DENIED`).
6. **Shadow Field Injection**: Adding `"isVerified": true` to a `cad_projects` create or update payload -> Reject (`PERMISSION_DENIED`).
7. **Timestamp Mutation on Update**: Changing `createdAt` during an update -> Reject (`PERMISSION_DENIED`).
8. **Future Timestamp Spoofing**: Submitting `createdAt` in the future (`> request.time`) -> Reject (`PERMISSION_DENIED`).
9. **Oversized String DoS**: Submitting `name` > 120 chars or `componentsJson` > 200,000 chars -> Reject (`PERMISSION_DENIED`).
10. **Invalid Numeric Range**: Submitting `explodeFactor` < 0.0 or > 1.0, or `partCount` < 1 -> Reject (`PERMISSION_DENIED`).
11. **Cross-User Delete**: Alice deleting Bob's `cad_projects/bob_proj` -> Reject (`PERMISSION_DENIED`).
12. **Printer Profile Cross-User Access**: Alice reading or modifying Bob's `printer_profiles/bob_printer` -> Reject (`PERMISSION_DENIED`).
