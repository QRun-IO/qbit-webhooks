-- qbit-webhooks 1.0: the five webhooks_ tables (PostgreSQL).
--
-- Column names are the snake_case of the field names (WebhooksQBitConfig
-- snakeCaseColumnNames, the default). Hosts that configure securityFields add
-- those columns to webhooks_endpoint, webhooks_subscription and
-- webhooks_delivery. The unique keys matter: webhooks_delivery.dedup_key stops
-- an event being queued twice, and (delivery_id, attempt_number) on
-- webhooks_delivery_attempt is the claim that stops two replicas sending the
-- same attempt.

CREATE TABLE webhooks_endpoint
(
   id SERIAL PRIMARY KEY,
   create_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   modify_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
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
   last_success_date TIMESTAMP WITH TIME ZONE,
   last_failure_date TIMESTAMP WITH TIME ZONE,
   last_failure_message VARCHAR(500),
   unhealthy_since_date TIMESTAMP WITH TIME ZONE,
   auto_disabled_date TIMESTAMP WITH TIME ZONE,
   CONSTRAINT webhooks_endpoint_name_uk UNIQUE (name)
);


CREATE TABLE webhooks_endpoint_secret
(
   id SERIAL PRIMARY KEY,
   create_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   modify_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   endpoint_id INTEGER NOT NULL REFERENCES webhooks_endpoint (id),
   status VARCHAR(20) DEFAULT 'ACTIVE',
   source VARCHAR(20) NOT NULL,
   secret_ref VARCHAR(100),
   encrypted_secret TEXT,
   fingerprint VARCHAR(20),
   expires_date TIMESTAMP WITH TIME ZONE,
   retired_date TIMESTAMP WITH TIME ZONE,
   note VARCHAR(250)
);

CREATE INDEX webhooks_endpoint_secret_endpoint_ix ON webhooks_endpoint_secret (endpoint_id, status);


CREATE TABLE webhooks_subscription
(
   id SERIAL PRIMARY KEY,
   create_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   modify_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   endpoint_id INTEGER NOT NULL REFERENCES webhooks_endpoint (id),
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
   id SERIAL PRIMARY KEY,
   create_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   modify_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   event_id VARCHAR(40) NOT NULL,
   event_type_name VARCHAR(200) NOT NULL,
   endpoint_id INTEGER NOT NULL,
   subscription_id INTEGER,
   source VARCHAR(20),
   source_table VARCHAR(100),
   source_record_id VARCHAR(100),
   source_event_id VARCHAR(100),
   dedup_key VARCHAR(250) NOT NULL,
   status VARCHAR(20) NOT NULL,
   attempt_count INTEGER DEFAULT 0,
   cycle_attempt_count INTEGER DEFAULT 0,
   replay_count INTEGER DEFAULT 0,
   occurred_date TIMESTAMP WITH TIME ZONE,
   next_attempt_date TIMESTAMP WITH TIME ZONE,
   lease_expires_date TIMESTAMP WITH TIME ZONE,
   last_http_status INTEGER,
   last_error VARCHAR(500),
   delivered_date TIMESTAMP WITH TIME ZONE,
   dead_letter_date TIMESTAMP WITH TIME ZONE,
   payload_bytes INTEGER,
   payload TEXT,
   CONSTRAINT webhooks_delivery_dedup_key_uk UNIQUE (dedup_key),
   CONSTRAINT webhooks_delivery_event_id_uk UNIQUE (event_id)
);

CREATE INDEX webhooks_delivery_due_ix ON webhooks_delivery (endpoint_id, status, next_attempt_date);

CREATE INDEX webhooks_delivery_status_ix ON webhooks_delivery (status, modify_date);

CREATE INDEX webhooks_delivery_delivered_ix ON webhooks_delivery (delivered_date);


CREATE TABLE webhooks_delivery_attempt
(
   id SERIAL PRIMARY KEY,
   create_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   modify_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
   delivery_id INTEGER NOT NULL,
   endpoint_id INTEGER NOT NULL,
   attempt_number INTEGER NOT NULL,
   outcome VARCHAR(20) NOT NULL,
   claimed_by VARCHAR(100),
   lease_expires_date TIMESTAMP WITH TIME ZONE,
   start_date TIMESTAMP WITH TIME ZONE,
   end_date TIMESTAMP WITH TIME ZONE,
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

