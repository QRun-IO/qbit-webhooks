-- qbit-webhooks tests: the webhooks_ tables for H2 (MySQL mode), generated
-- from the same spec as docs/ddl/webhooks-postgres.sql.

DROP TABLE IF EXISTS webhooks_delivery_attempt;
DROP TABLE IF EXISTS webhooks_delivery;
DROP TABLE IF EXISTS webhooks_subscription;
DROP TABLE IF EXISTS webhooks_endpoint_secret;
DROP TABLE IF EXISTS webhooks_endpoint;
DROP TABLE IF EXISTS person;

CREATE TABLE person
(
   id INT AUTO_INCREMENT PRIMARY KEY,
   first_name VARCHAR(80),
   last_name VARCHAR(80),
   email VARCHAR(250),
   status VARCHAR(20)
);

CREATE TABLE webhooks_endpoint
(
   id INT AUTO_INCREMENT PRIMARY KEY,
   create_date TIMESTAMP NOT NULL DEFAULT now(),
   modify_date TIMESTAMP NOT NULL DEFAULT now(),
   name VARCHAR(100) NOT NULL,
   description TEXT,
   url VARCHAR(500) NOT NULL,
   active_status VARCHAR(20) DEFAULT 'ACTIVE',
   health_status VARCHAR(20) DEFAULT 'HEALTHY',
   serial_delivery BOOLEAN DEFAULT FALSE,
   max_per_minute INTEGER,
   timeout_seconds INTEGER,
   allow_unsigned BOOLEAN DEFAULT FALSE,
   allow_internal_targets BOOLEAN DEFAULT FALSE,
   internal_allowlist TEXT,
   consecutive_failures INTEGER DEFAULT 0,
   probation_failures INTEGER DEFAULT 0,
   last_success_date TIMESTAMP,
   last_failure_date TIMESTAMP,
   last_failure_message VARCHAR(500),
   unhealthy_since_date TIMESTAMP,
   auto_disabled_date TIMESTAMP,
   CONSTRAINT webhooks_endpoint_name_uk UNIQUE (name)
);


CREATE TABLE webhooks_endpoint_secret
(
   id INT AUTO_INCREMENT PRIMARY KEY,
   create_date TIMESTAMP NOT NULL DEFAULT now(),
   modify_date TIMESTAMP NOT NULL DEFAULT now(),
   endpoint_id INTEGER NOT NULL CONSTRAINT fk_webhooks_endpoint_secret_endpoint_id REFERENCES webhooks_endpoint (id) ON DELETE CASCADE,
   status VARCHAR(20) DEFAULT 'ACTIVE',
   source VARCHAR(20) NOT NULL,
   secret_ref VARCHAR(100),
   encrypted_secret TEXT,
   fingerprint VARCHAR(20),
   expires_date TIMESTAMP,
   retired_date TIMESTAMP,
   note VARCHAR(250)
);

CREATE INDEX webhooks_endpoint_secret_endpoint_ix ON webhooks_endpoint_secret (endpoint_id, status);


CREATE TABLE webhooks_subscription
(
   id INT AUTO_INCREMENT PRIMARY KEY,
   create_date TIMESTAMP NOT NULL DEFAULT now(),
   modify_date TIMESTAMP NOT NULL DEFAULT now(),
   endpoint_id INTEGER NOT NULL CONSTRAINT fk_webhooks_subscription_endpoint_id REFERENCES webhooks_endpoint (id) ON DELETE CASCADE,
   event_type_name VARCHAR(200) NOT NULL,
   active_status VARCHAR(20) DEFAULT 'ACTIVE',
   description VARCHAR(250),
   filter_json TEXT,
   detail_level VARCHAR(20) DEFAULT 'FULL',
   include_fields VARCHAR(1000),
   exclude_fields VARCHAR(1000),
   api_name VARCHAR(100),
   api_version VARCHAR(50)
);

CREATE INDEX webhooks_subscription_type_ix ON webhooks_subscription (event_type_name, active_status);

CREATE INDEX webhooks_subscription_endpoint_ix ON webhooks_subscription (endpoint_id);


CREATE TABLE webhooks_delivery
(
   id INT AUTO_INCREMENT PRIMARY KEY,
   create_date TIMESTAMP NOT NULL DEFAULT now(),
   modify_date TIMESTAMP NOT NULL DEFAULT now(),
   event_id VARCHAR(40) NOT NULL,
   event_type_name VARCHAR(200) NOT NULL,
   endpoint_id INTEGER NOT NULL CONSTRAINT fk_webhooks_delivery_endpoint_id REFERENCES webhooks_endpoint (id) ON DELETE RESTRICT,
   subscription_id INTEGER CONSTRAINT fk_webhooks_delivery_subscription_id REFERENCES webhooks_subscription (id) ON DELETE SET NULL,
   source VARCHAR(20),
   source_table VARCHAR(100),
   source_record_id VARCHAR(100),
   source_event_id VARCHAR(100),
   dedup_key VARCHAR(250) NOT NULL,
   status VARCHAR(20) NOT NULL,
   attempt_count INTEGER DEFAULT 0,
   cycle_attempt_count INTEGER DEFAULT 0,
   replay_count INTEGER DEFAULT 0,
   occurred_date TIMESTAMP,
   next_attempt_date TIMESTAMP,
   lease_expires_date TIMESTAMP,
   last_http_status INTEGER,
   last_error VARCHAR(500),
   delivered_date TIMESTAMP,
   dead_letter_date TIMESTAMP,
   payload_bytes INTEGER,
   payload TEXT,
   CONSTRAINT webhooks_delivery_dedup_key_uk UNIQUE (dedup_key),
   CONSTRAINT webhooks_delivery_event_id_uk UNIQUE (event_id)
);

CREATE INDEX webhooks_delivery_due_ix ON webhooks_delivery (endpoint_id, status, next_attempt_date);

CREATE INDEX webhooks_delivery_status_ix ON webhooks_delivery (status, modify_date);

CREATE INDEX webhooks_delivery_delivered_ix ON webhooks_delivery (delivered_date);

CREATE INDEX webhooks_delivery_subscription_ix ON webhooks_delivery (subscription_id);


CREATE TABLE webhooks_delivery_attempt
(
   id INT AUTO_INCREMENT PRIMARY KEY,
   create_date TIMESTAMP NOT NULL DEFAULT now(),
   modify_date TIMESTAMP NOT NULL DEFAULT now(),
   delivery_id INTEGER NOT NULL CONSTRAINT fk_webhooks_delivery_attempt_delivery_id REFERENCES webhooks_delivery (id) ON DELETE CASCADE,
   endpoint_id INTEGER NOT NULL CONSTRAINT fk_webhooks_delivery_attempt_endpoint_id REFERENCES webhooks_endpoint (id) ON DELETE RESTRICT,
   attempt_number INTEGER NOT NULL,
   outcome VARCHAR(20) NOT NULL,
   claimed_by VARCHAR(100),
   lease_expires_date TIMESTAMP,
   start_date TIMESTAMP,
   end_date TIMESTAMP,
   duration_millis INTEGER,
   http_status INTEGER,
   response_excerpt TEXT,
   error_message VARCHAR(1000),
   remote_address VARCHAR(64),
   request_url VARCHAR(500),
   signed_with VARCHAR(100),
   CONSTRAINT webhooks_delivery_attempt_uk UNIQUE (delivery_id, attempt_number)
);

CREATE INDEX webhooks_delivery_attempt_endpoint_ix ON webhooks_delivery_attempt (endpoint_id, start_date);

CREATE INDEX webhooks_delivery_attempt_start_ix ON webhooks_delivery_attempt (start_date);

