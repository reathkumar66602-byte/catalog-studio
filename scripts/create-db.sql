-- Run this in pgAdmin as the postgres superuser (Query Tool on the "postgres" database), or:
-- "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -f scripts/create-db.sql
--
-- Safe to re-run the DO block. Skip CREATE DATABASE if that database already exists.

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'catalog_studio') THEN
    CREATE ROLE catalog_studio LOGIN PASSWORD 'catalog_studio';
  ELSE
    ALTER ROLE catalog_studio WITH LOGIN PASSWORD 'catalog_studio';
  END IF;
END
$$;

CREATE DATABASE catalog_studio OWNER catalog_studio;
GRANT ALL PRIVILEGES ON DATABASE catalog_studio TO catalog_studio;
