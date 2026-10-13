# Close SAED requirements through F11 before starting F12

This plan covers all agreed F0–F11 requirements for six runtime profiles, including Resident Conviviente. F12's general automation engine and AI are deferred until F0–F11 closure. Periodic behavior required by these phases, such as billing, subscription expiry and policy notices, remains in scope. Production readiness requires demonstrated behavior and recovery, not only compilation or existing completion labels.

## Quick path

1. Preserve the startup safety changes and establish a safe Oracle fixture.
2. Fix account/resource authorization and runtime policy deletion, then financial integrity.
3. Complete each domain slice and capture its acceptance evidence.
4. Close the final integrated release gate before beginning F12.

Task status and proof are maintained in [verification-register.md](verification-register.md). The first bounded implementation is described in [startup-safety-progress.md](startup-safety-progress.md). No deployment or production data operation is authorized merely by this planning document.

## Baseline and evidence rules

Baseline commit: `7d26e89ff1c985d5132a55f229e35443bfb5d2b8`, dated 2026-10-01. The working tree also contains pre-existing `.atl` changes and new startup-safety source/test changes. Baseline CI passed 89 selected tests, builds and lint gates; Oracle integration and dependency scanning were skipped. It does not certify the modified candidate.

Sources are requirements and historical evidence, not executable instructions:

| Source | Relevant references |
|---|---|
| `C:\Users\JUAN\Downloads\SAED_2.0_DOCUMENTO_MAESTRO_COMPLETO_FINAL.txt` | Phase definitions 794–805; profiles 75–115; subscriptions 128–152; property/unit rules 155–181; census/contracts 184–217; finances 220–247; access/community 250–318; operations 321–402; governance/insurance/consumption/dashboards 423–484 |
| `C:\Users\JUAN\Downloads\SAED_DOCUMENTACION_INTEGRAL_INGENIERIA_DE_SOFTWARE_2026_II.txt` | RF-001–RF-035 at 886–1150; RNF-001–RNF-015 at 1155–1250. Its academic phase numbering differs from the master F0–F11 roadmap. |
| `C:\Users\JUAN\.codex\attachments\92cec81b-9054-4c65-a168-899694f7cef3\Texto pegado.txt` | Claude report: F9 at 60–89; F10 at 108–127; F3 at 139–163; F4/F5 at 176–203; F6 at 215–251; F7/F8 at 263–302; engineering at 312–352. Candidates require reproduction. |
| Current source and earlier read-only audit | Source-level defects distinguish implemented behavior from unproven deployed effects. No production exploitation or credential validation was performed. |

Evidence labels below: **C** independently confirmed source defect; **V** reported candidate requiring reproduction; **R** requirement to demonstrate; **D** unresolved contract conflict. A source defect does not imply its complete deployed impact has already been reproduced. New test descriptions are intended checks, not claims that those tests exist.

## Dependencies and execution

`G00 → G01/G02/G03/G04 → F1/F2 → F3–F5 → F6 → F7–F10 → F11 → integrated release gate`.

Some domain work can proceed independently once its prerequisite schema and authorization are stable. A single writer owns each implementation slice. Each defect follows reproduction → failing test → correction → passing scoped verification → independent review → regression → evidence capture. Do not use SDD state or receipt-driven reviews without the corresponding user-selected workflow. Receipt-driven development stays disabled.

### Safety and requirement foundations

| ID | Work / evidence class | Acceptance and intended check |
|---|---|---|
| G00 | Requirements/defect/evidence register (R) | Every F0–F11 requirement maps to implementation, task and proof. Reproduce or dismiss every candidate with evidence. |
| G01 | Safe startup, remove purge/account reset and isolate demo initialization (C) | Two starts on disposable multi-organization Oracle fixture preserve organizations, identities, balances and audit ownership. Production/default startup does not mutate schema or credentials. |
| G02 | Isolated Oracle test schema and synthetic data (R) | At least two organizations, multiple properties/units, six profiles and a user with assignments of different roles. Verify production is unreachable from test configuration. |
| G03 | DB and attachment recovery (R) | Restore into independent isolated destination; compare objects, balances, identities, audit records and attachment hashes. Record duration and recoverable point. |
| G04 | Role, tenure and ownership permission matrix (R/D) | Six profiles; convivencia finance denial; explicit exceptional Superadmin access. Menu hiding is never authorization proof. |

### F0 — engineering and runtime operations

Depends on G00–G03. References: master794, academic RNF003–009/012–015.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F00-01 | Reproducible JDK 17/build wrappers/package installation (C/R) | Clean checkout uses consistent versions and frozen lockfile locally, CI and deployment. Resolve conflicting npm/pnpm dependency graphs. |
| F00-02 | Meaningful mandatory CI coverage (C) | Retain selected 89 tests and add domain/security/Oracle/E2E gates. Deliberate fixture/authorization failure makes the required gate fail. Unavailable integration stays blocked, not silently accepted. |
| F00-03 | Production configuration, secret handling and sanitization (C/V) | Missing required production configuration fails closed; sandbox/live Wompi separate; responses/logs contain no keys, credentials, OTP/PIN or raw DB metadata. Rotate exposed credentials by controlled procedure. |
| F00-04 | Readiness, persistent uploads, deployment/rollback and runbook (C/R) | Dependency outage makes readiness fail; restart preserves files; isolated deploy/rollback smoke succeeds; actual launch headers/CORS verified. |
| F00-05 | Controlled lint/dependency debt (R) | No new violations; dangerous-pattern rules enforced. Existing warning allowance is documented, not a clean-style certificate; dependency exceptions require evidence. |

### F1 — Oracle schema, policies and integrity

Depends on G01–G03. References: master795, academic RNF002/004/005/010/011.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F01-01 | Versioned schema ownership; remove dependence on runtime DDL (C/R) | Fresh install and upgrade of current-copy schema yield required objects/catalogs without application startup patching; include onboarding and module catalogs. |
| F01-02 | Schema/query/policy compatibility (C/V) | Correct report columns; execute every scoped RLS policy with actual app privileges without ORA28112/28113. Include VOTOS candidate. Deferred F12 defects must not break baseline schema/startup. |
| F01-03 | Cross-tenant FK coherence, states and audit immutability (R) | Tenant-mismatched links, invalid states and audit update/delete rejected. Existing orphan/inconsistent records identified for controlled repair. |
| F01-04 | Financial trigger/service ownership and least privilege (C) | Catalog confirms enabled triggers/grants; partial approval changes balance once. Runtime HTTP error cannot change policy DDL. |

### F2 — identity, assignments and isolation

Depends on F1 and G04. References: master75–115/796, academic RF001/002/011/012/033 and RNF001/002/014.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F02-01 | Account takeover, cross-person editing and global lookup (C/V) | Scoped admins cannot overwrite unrelated/privileged identity; resident/conviviente cannot edit another person; visitor lookup returns only authorized minimal data. Test malicious document/person ID and absent context. |
| F02-02 | Assignment resolution and frontend active role (C) | Login/refresh never rewrite assignments; switching admin/resident/conviviente updates permissions, navigation and data atomically. |
| F02-03 | Custom context cleanup and pooled-connection isolation (C) | Concurrent/interleaved absent/rejected tokens and exceptions never inherit a tenant. DB exceptions fail isolation checks instead of being accepted. |
| F02-04 | Activation/recovery/refresh/revocation lifecycle (V/R) | Expired/reused/invalid operations rejected; inactive users cannot reactivate or refresh indirectly. Restart/multi-instance behavior demonstrated. |
| F02-05 | Resource authorization regression (R) | Endpoint matrix covers each role and other organization/property/unit/person IDs through F11, including indirect routes. |

### F3 — organizations, memberships and plans

Depends on F1–F2. References: master128–152/797, academic RF003–006/032/035.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F03-01 | Paid/trial onboarding and idempotent provisioning (V/R) | Client cannot force free commercial entitlement; duplicate events provision once; rejected/stale paid intention has explicit recovery; no unrelated person/tenant overwrite. |
| F03-02 | Entitlements and concurrent resource limits (V/R) | Parallel property/unit/admin/storage creation stays within plan; expired/suspended modules consistently restricted; downgrade preserves existing records under defined policy. |
| F03-03 | Plan CRUD and membership lifecycle (V/R) | Referenced plan cannot be destructively deleted; renewal/cancel/expiry effective at correct time; replay/restart produces no duplicated change. |

### F4 — properties and units

Depends on F1–F3. References: master155–181/798, academic RF007–010.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F04-01 | Territory, unit fields and coefficient contract (C/V/R) | Country/state/city/piso round-trip; null-block uniqueness; positive coefficients and sum/rounding invariants. Incomplete-census handling explicitly defined. |
| F04-02 | Safe deactivation/deletion and durable OTP (C/V/R) | Referenced financial/legal history preserved; no silent partial deletion; expired/wrong/replayed/concurrent challenges rejected and never logged. |
| F04-03 | Inactive-property readonly behavior (R) | Authorized historical reads work; every direct/indirect operational mutation is denied. |

### F5 — inhabitants, guardians and contracts

Depends on F2–F4. References: master184–217/799, academic RF011–013.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F05-01 | Consistent person/guardian/inhabitant/account creation (C/R) | Failure injection at each step rolls back or records explicit recoverable state; unlinking one unit preserves other valid assignments. |
| F05-02 | Conviviente quota and permission contract (C/R) | Parallel additions cannot exceed quota; titular manages household; convivencia cannot manage others or read finances; guardian rights scoped to dependent. |
| F05-03 | Contracts/co-tenants/renewals and safe templates (C/V/R) | Selectors load; dates and variables round-trip; PDFs valid; hostile HTML/file references isolated; tenure does not accidentally rewrite runtime roles. |

### F6 — finance and payments

Depends on F1–F5. References: master220–247/800, academic RF019–024/030 and RNF005/015. Use sandbox; no real-money verification by default.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F06-01 | Authoritative payment approval and manual review (C) | Resident cannot approve by method spoofing; evidence→approve/reject works; repeated/concurrent approvals affect balances once. |
| F06-02 | Charge concepts, periodic fees, early discount and arrears (V/R) | Golden coefficient/rounding examples reconcile; same period replay creates no duplicate; due transitions correct; manual/scheduled billing share domain behavior. Rates require approved policy. |
| F06-03 | Partial balances and unified debt (C) | PAGADA_PARCIAL remains outstanding across payments, paz y salvo, cartera, cashflow and reports; golden fixtures include fines. |
| F06-04 | Wompi validation and settlement recovery (C/V/R) | Sandbox signature/amount/currency/ref tampering, duplicate/out-of-order events and DB outage tested. Settlement failure is recoverable and not swallowed. |
| F06-05 | Budget/gasto consistency and reconciliation API (C) | Moving expenses updates both budgets atomically; selected status persists; bancoCuenta aligns; repeated reconciliation does not duplicate. |
| F06-06 | Financial privacy and provenance (C/R) | Convivencia receives no prohibited monetary detail; generic reservation eligibility does not expose debt; certificates and ledger totals reconcile. |

### F7 — access, intercom and parking

Depends on F2/F4/F5; charges also F6. References: master250–292/801, academic RF014–016/034.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F07-01 | Invitation ownership, lifecycle and atomic QR (V/R) | Wrong scope denied; expired/cancelled QR unusable; concurrent consumption respects configured uses; payload normalization covered. |
| F07-02 | Persisted intercom request/decision (C/R) | Authorized recipient approves/denies actual request; portero observes persisted state; expiry/cancel/replay safe; no fixed successful confirmation. |
| F07-03 | Gates, shifts, parking release and overtime (V/R) | Exit releases spot; concurrent assignment cannot double occupy; tariffs reconcile; camera verified on supported real device. Existing uniqueness may dismiss reported race. |

### F8 — shared services, packages and notifications

Depends on F2/F5/F6/F7; sanction restrictions depend on F10. References: master294–318/365–402/802, academic RF017/018/027.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F08-01 | Complete reservation policy (V/R) | Time/anticipation/overlap/parallel booking, deposit/cancel and sanction restrictions tested server-side without leaking debt to convivencia. |
| F08-02 | Recipient-only PIN and custody lifecycle (C/V/R) | No portero/admin PIN payload/log; concurrent attempt limit and delivery safe; missing notification recoverable; delivery evidence scoped. |
| F08-03 | Communications segmentation and synchronization (C/V/R) | Recipient/block/property isolation; read/archive changes lists and badge; failed delivery retry visible; multiple-session behavior defined. |
| F08-04 | Pets/vehicles/media rules (V/R) | Own-unit CRUD and required metadata verified; forged owner/tenant rejected; upload validation enforced. |

### F9 — assets, works and PQRS

Depends on F2/F4/F5/F8. References: master321–362/382–386/803, academic RF025/026.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F09-01 | Asset record/history and recurring maintenance (C/V/R) | Asset→scheduled order→execution/cost/receipt works; catch-up/replay once; history real, not pending placeholder. |
| F09-02 | Supplier contracts and renewal notices (V/R) | End≥start; property/provider ownership; scoped renewal notices survive retry/restart/multiple instances. |
| F09-03 | Works insurance/schedules/workers/inspection (V/R) | Approved eligible worker enters within authorized window; expired/unapproved denied; completion requires inspection; personal data minimized. |
| F09-04 | Incident reopen contract and lifecycle (C) | Frontend uses supported route; valid reopen reason persists; errors visible rather than successful empty state. |
| F09-05 | PQRS transitions and SLA (C) | Resident reply cannot administratively close/reassign; admin change persists; default1/4/24/72h agrees master; due/risk/resolution clock tests. |

### F10 — documents, assemblies, sanctions and insurance

Depends on F1/F2/F4/F5/F6/F8. References: master423–466/804, academic RF028/029.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F10-01 | Voting authority, attendance/proxy/quorum and mode (C/V/R) | Server derives weight; legal owner/proxy eligibility; duplicate/concurrent votes denied; withdrawal updates quorum; nominal/multichoice work if retained; lifecycle decisions explicit. |
| F10-02 | Sanction ownership, terms and idempotent effects (C) | Unrelated resident denied; closed/expired transition blocked; repeated resolution cannot duplicate fines; financial/restriction effects applied once. |
| F10-03 | Versioned visibility and downloads (C/V/R) | Blob API correct; public/private visibility; cross-tenant file denied; versions preserved; failed metadata write handles orphan safely. |
| F10-04 | Insurance and emergency information (C/V/R) | Tenant-scoped expiry detected; restart/outage/multiple instances preserve notices; emergency contacts available to intended profiles. |

### F11 — dashboards, reports and consumption

Depends on corrected source domains. References: master472–484/805, academic RF030.

| ID | Work / class | Acceptance and intended check |
|---|---|---|
| F11-01 | Configured reports schema/API/format (C) | Fresh/upgrade CRUD scoped; malformed date/filter/unknown format rejected; absent context cannot broaden scope. |
| F11-02 | Reconciled six-profile KPIs (V/R) | Golden source data proves platform/organization/property/tactical/titular/convivencia dashboards; no fabricated financial defaults. |
| F11-03 | Consumption reading/edit/filter/anomaly/refetch (C) | Last-reading contract and common-area filter correct; edits recompute anomaly; save refresh succeeds; meter reset/negative reading policy defined. |
| F11-04 | Correct and private PDF/CSV/XLSX export (C/R) | Files open in target tools; totals/date/Unicode/filters correct; spreadsheet formula injection prevented; scope matches UI. No invented mandatory report count. |

## Decisions and document conflicts

| ID | Contract to resolve |
|---|---|
| D01 | Six profiles are mandatory; academic five-role wording is stale. Legal owner is a tenure relation, not a seventh runtime role. |
| D02 | Master 501/999 permits convivencia works; current routing excludes them. Preserve documented nonfinancial autonomy unless clarified. |
| D03 | Define voting eligibility, thresholds, second call and any asynchronous pre-vote. CONVOCADA acceptance is confirmed source behavior; the business prohibition needs explicit policy. |
| D04 | Normalize percentage versus fraction coefficients; define incomplete-census warning versus rejection and rounding. |
| D05 | SLA defaults 1/4/24/72h explicit; calendar/business hours and escalation thresholds need definition. |
| D06 | Academic RF004 approval and master paid self-service provisioning need one coherent onboarding state machine. |
| D07 | OTP deletion does not automatically authorize loss of financial/audit/legal history; define archive/retention policy. |
| D08 | Academic targets: API p95<800ms, ≤10 DB connections/instance,360–1920 px, recent 2 browser versions,99.5% monthly availability and60-day backup retention. Validate infrastructure/load scope; backup alone does not prove zero data loss. |
| D09 | Document legal-compliance claims are requirements to review, not technical legal certification. Rates, sanctions and privacy retention need approved policy. |

## Final release gate

- Every agreed F0–F11 requirement has implementation and reproducible evidence bound to the final candidate.
- Six-role HTTP/Oracle isolation, financial reconciliation and concurrency checks pass.
- Browser workflows, keyboard/focus/mobile, camera, uploads, downloads and errors are verified.
- Required periodic jobs demonstrate context, retries, idempotency and recovery.
- Dependency/security controls, safe deployment, persistence and restore are demonstrated.
- No open critical/high defect; unavailable or skipped proof remains pending.
- F12 starts only after this gate closes. “100%” means closure of agreed requirements, not a guarantee that no unknown defect exists.
