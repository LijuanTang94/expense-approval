-- Expense reimbursement approval workflow — initial schema.
-- Flyway is the single source of truth for the schema; JPA only validates against it.

-- ---------------------------------------------------------------------------
-- Departments
-- ---------------------------------------------------------------------------
CREATE TABLE departments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Users — one role per user (EMPLOYEE / MANAGER / FINANCE). A manager owns a
-- department; employees belong to one. manager_id is a convenience back-pointer.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(160) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    department_id BIGINT       REFERENCES departments (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'FINANCE'))
);
CREATE INDEX idx_users_department ON users (department_id);

-- ---------------------------------------------------------------------------
-- Expense requests — the aggregate that moves through the state machine:
--   DRAFT -> SUBMITTED -> MANAGER_APPROVED -> FINANCE_APPROVED
--                     \-> REJECTED (from SUBMITTED or MANAGER_APPROVED)
-- total_amount is denormalised from the line items for cheap listing/filtering.
-- ---------------------------------------------------------------------------
CREATE TABLE expense_requests (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_id  BIGINT        NOT NULL REFERENCES users (id),
    department_id BIGINT        NOT NULL REFERENCES departments (id),
    title         VARCHAR(200)  NOT NULL,
    description   TEXT          NOT NULL DEFAULT '',
    status        VARCHAR(24)   NOT NULL DEFAULT 'DRAFT',
    total_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency      CHAR(3)       NOT NULL DEFAULT 'USD',
    submitted_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_req_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'MANAGER_APPROVED', 'FINANCE_APPROVED', 'REJECTED')
    ),
    CONSTRAINT chk_req_total_nonneg CHECK (total_amount >= 0)
);
CREATE INDEX idx_req_requester ON expense_requests (requester_id);
CREATE INDEX idx_req_department_status ON expense_requests (department_id, status);
CREATE INDEX idx_req_status ON expense_requests (status);

-- ---------------------------------------------------------------------------
-- Expense line items — belong to one request; deleted with it.
-- ---------------------------------------------------------------------------
CREATE TABLE expense_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id  BIGINT        NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    description VARCHAR(200)  NOT NULL,
    category    VARCHAR(40)   NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    incurred_on DATE          NOT NULL,
    CONSTRAINT chk_item_amount_pos CHECK (amount > 0)
);
CREATE INDEX idx_item_request ON expense_items (request_id);

-- ---------------------------------------------------------------------------
-- Approvals — append-only audit trail of every state transition (who / what /
-- from -> to / comment). Drives the "who can approve what" history.
-- ---------------------------------------------------------------------------
CREATE TABLE approvals (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id  BIGINT       NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    actor_id    BIGINT       NOT NULL REFERENCES users (id),
    action      VARCHAR(16)  NOT NULL,
    from_status VARCHAR(24)  NOT NULL,
    to_status   VARCHAR(24)  NOT NULL,
    comment     VARCHAR(500) NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_appr_action CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT'))
);
CREATE INDEX idx_appr_request ON approvals (request_id, created_at);
