-- Practice 7: demo users for local development only. This folder is added to spring.flyway.locations in the
-- dev profile only, so these well-known passwords never reach a test or production database.
-- The hashes are DelegatingPasswordEncoder output: {bcrypt} id, version 2a, cost 10, salt + hash.
--
--   username  | password    | roles
--   customer  | customer123 | CUSTOMER
--   admin     | admin123    | ADMIN
--   blocked   | disabled123 | CUSTOMER, but the account is disabled (login gives 401)
--
-- Version 5 because dev reads db/migration and db/seed together, and V4 is the users table itself.

INSERT INTO app_users (username, password_hash, enabled) VALUES
    ('customer', '{bcrypt}$2a$10$y0sxWy9NYdCpIJ3xyrhhmupx0bJFScW4H7wGee0vpkfM4eDdnSWE2', TRUE),
    ('admin',    '{bcrypt}$2a$10$lMX1Nm3Aaw6N4sYBeB4Ln.SnyJ6GKdhp2WAaiFvYIWFoDXoVdBzja', TRUE),
    ('blocked',  '{bcrypt}$2a$10$p9NGnF1GG3/V0ATDB0/oVO3vhe.Rj9QpF.m0J3/0RJYEpCCyHPHa2', FALSE);

INSERT INTO user_roles (user_id, role)
SELECT u.id, r.role
FROM (VALUES ('customer', 'CUSTOMER'), ('admin', 'ADMIN'), ('blocked', 'CUSTOMER')) AS r (username, role)
JOIN app_users u ON u.username = r.username;
