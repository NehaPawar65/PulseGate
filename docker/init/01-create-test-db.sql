-- Runs only when the Postgres volume is first created.
-- Separate database so automated tests never touch dev data.
CREATE DATABASE pulsegate_test OWNER pulsegate;
