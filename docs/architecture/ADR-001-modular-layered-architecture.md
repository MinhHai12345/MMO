# ADR-001: Modular Monolith with Layered Architecture

- Status: Accepted
- Date: 2026-11-18
- Scope: MMO application architecture

## Context

MMO is an existing Java 17 / Spring Boot application with several hundred production classes, a working MySQL schema, provider ingestion, workflow execution, analytics, content and publication capabilities. The repository has accumulated mixed responsibilities in a top-level `core` package and inconsistent module boundaries.

A full Hexagonal Architecture would introduce ports/adapters across many internal use cases without proportional benefit at the current scale. Microservices would add deployment, messaging, consistency and operations costs before the product requires them.

## Decision

Adopt a Modular Monolith with Layered Architecture:

- One Spring Boot deployable.
- Business modules organized by ownership.
- Presentation/API, Application, Domain, Repository and Infrastructure responsibilities.
- Public application services/contracts for cross-module collaboration.
- Selective interfaces only where they represent a real external boundary, multiple implementations or useful test seam.
- No top-level `core` package in the target structure.
- No mandatory ports/adapters structure in every module.

## Consequences

Positive:
- Lower conceptual and implementation overhead.
- Clearer business ownership.
- Easier incremental migration of existing code.
- Preserves current deployment and database model.
- Supports future extraction if a module develops independent scaling or deployment needs.

Trade-offs:
- Modules share one runtime and database.
- Architecture boundaries must be enforced through review and automated tests.
- Some JPA coupling may remain during early migration and should be removed only where it causes concrete problems.

## Rejected alternatives

- Microservices: premature operational complexity.
- Full Hexagonal Architecture: too much structural overhead for internal use cases.
- Big-bang rewrite: unacceptable migration and data risk.
- Retaining a generic top-level core: encourages responsibility dumping and cross-module coupling.

## Implementation rule

Apply the decision incrementally. Preserve API behavior, Quartz job identity, JPA mappings and production data during package-only moves. Every deletion or schema change requires consumer and data-retention evidence.
