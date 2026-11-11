-- ============================================================================
--  Paradox - Simple Deadlock Detection System
--  MySQL schema (run once):  mysql -u root -p paradox_db < database/schema.sql
--
--  Three tables only:
--    simulations - one row per saved simulation
--    processes   - processes of a simulation (RUNNING / WAITING / TERMINATED)
--    resources   - single-instance resources; allocated_process_id tells which
--                  process currently holds the resource (NULL = available)
-- ============================================================================

CREATE TABLE IF NOT EXISTS simulations (
    simulation_id INT      NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (simulation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Resources are created before processes because processes reference them.
CREATE TABLE IF NOT EXISTS resources (
    resource_id          INT      NOT NULL AUTO_INCREMENT,
    simulation_id        INT      NOT NULL,
    name                 VARCHAR(20) NOT NULL,
    allocated_process_id INT      NULL,          -- NULL means the resource is free
    PRIMARY KEY (resource_id),
    UNIQUE KEY uq_resource_name (simulation_id, name),
    CONSTRAINT fk_resource_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS processes (
    process_id              INT      NOT NULL AUTO_INCREMENT,
    simulation_id           INT      NOT NULL,
    name                    VARCHAR(20) NOT NULL,
    status                  ENUM('RUNNING', 'WAITING', 'TERMINATED')
                                     NOT NULL DEFAULT 'RUNNING',
    waiting_for_resource_id INT      NULL,      -- set only while WAITING
    PRIMARY KEY (process_id),
    UNIQUE KEY uq_process_name (simulation_id, name),
    CONSTRAINT fk_process_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_process_waiting
        FOREIGN KEY (waiting_for_resource_id) REFERENCES resources (resource_id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Resources store which process holds them (added after processes exist).
ALTER TABLE resources
    ADD CONSTRAINT fk_resource_holder
    FOREIGN KEY (allocated_process_id) REFERENCES processes (process_id)
    ON DELETE SET NULL;
