CREATE TABLE IF NOT EXISTS investigations (
 id VARCHAR(36) PRIMARY KEY, service VARCHAR(64) NOT NULL, payload TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS deployment_events (
 id VARCHAR(36) PRIMARY KEY, service VARCHAR(64) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, payload TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS deployment_service_time ON deployment_events(service, occurred_at);
