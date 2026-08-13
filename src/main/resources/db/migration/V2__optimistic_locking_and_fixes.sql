-- Concurrency guard + small schema corrections found in review.

-- 1) Optimistic locking on expense_requests.
-- Without it, two approvers acting at the same moment both read the pre-commit status, both pass
-- the transition check, and both write — leaving a request whose audit trail records two
-- conflicting decisions. Hibernate increments this column on every update and fails the second
-- writer, which the API surfaces as 409 Conflict.
ALTER TABLE expense_requests
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2) Index the approvals -> users foreign key.
-- Postgres indexes primary keys but not the referencing side of a foreign key, so actor-scoped
-- lookups (and cascading user deletes) would sequential-scan the audit table.
CREATE INDEX idx_approvals_actor ON approvals (actor_id);

-- 3) currency: CHAR(3) blank-pads values ('US ' vs 'US'), which does not match the entity's
-- variable-length String mapping and would fail Hibernate's schema validation.
ALTER TABLE expense_requests
    ALTER COLUMN currency TYPE VARCHAR(3);
