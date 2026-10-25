-- Run this script in MySQL Workbench before starting Parcelly.
CREATE DATABASE IF NOT EXISTS parcelly CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE parcelly;
-- The Java application creates/updates the tables automatically at startup.
-- Tables: workspace_metadata, customers, agents, accounts, parcels, tracking_events, payments.
