DROP TABLE IF EXISTS stop_extensions CASCADE;
DROP TABLE IF EXISTS stop_times CASCADE;
DROP TABLE IF EXISTS transfers CASCADE;
DROP TABLE IF EXISTS pathways CASCADE;
DROP TABLE IF EXISTS stops CASCADE;
DROP TABLE IF EXISTS trips CASCADE;
DROP TABLE IF EXISTS calendar_dates CASCADE;
DROP TABLE IF EXISTS calendar CASCADE;
DROP TABLE IF EXISTS routes CASCADE;
DROP TABLE IF EXISTS agency CASCADE;

-- -------------------------------------------------------------------------
-- TABLE: agency
-- -------------------------------------------------------------------------
CREATE TABLE agency (
agency_id VARCHAR(50) PRIMARY KEY,
agency_name VARCHAR(255) NOT NULL,
agency_url TEXT NOT NULL,
agency_timezone VARCHAR(100) NOT NULL,
agency_lang VARCHAR(10),
agency_phone VARCHAR(50),
agency_email VARCHAR(150),
agency_fare_url TEXT
);

-- -------------------------------------------------------------------------
-- TABLE: calendar
-- -------------------------------------------------------------------------
CREATE TABLE calendar (
service_id VARCHAR(100) PRIMARY KEY,
monday BOOLEAN NOT NULL DEFAULT FALSE,
tuesday BOOLEAN NOT NULL DEFAULT FALSE,
wednesday BOOLEAN NOT NULL DEFAULT FALSE,
thursday BOOLEAN NOT NULL DEFAULT FALSE,
friday BOOLEAN NOT NULL DEFAULT FALSE,
saturday BOOLEAN NOT NULL DEFAULT FALSE,
sunday BOOLEAN NOT NULL DEFAULT FALSE,
start_date DATE NOT NULL,
end_date DATE NOT NULL
);

-- -------------------------------------------------------------------------
-- TABLE: routes
-- -------------------------------------------------------------------------
CREATE TABLE routes (
route_id VARCHAR(100) PRIMARY KEY,
agency_id VARCHAR(50),
route_short_name VARCHAR(50),
route_long_name VARCHAR(255),
route_desc TEXT,
route_type INT NOT NULL,
route_url TEXT,
route_color VARCHAR(10),
route_text_color VARCHAR(10),
route_sort_order INT
); 

-- -------------------------------------------------------------------------
-- TABLE: calendar_dates
-- -------------------------------------------------------------------------
CREATE TABLE calendar_dates (
service_id VARCHAR(100) NOT NULL,
date DATE NOT NULL,
exception_type INT NOT NULL
);

-- -------------------------------------------------------------------------
-- TABLE: stops
-- -------------------------------------------------------------------------
CREATE TABLE stops (
stop_id VARCHAR(100) PRIMARY KEY,
stop_code VARCHAR(50),
stop_name VARCHAR(255) NOT NULL,
stop_desc TEXT,
stop_lon NUMERIC(9, 6) NOT NULL,
stop_lat NUMERIC(9, 6) NOT NULL,
zone_id VARCHAR(50),
stop_url TEXT,
location_type INT DEFAULT 0,
parent_station VARCHAR(100),
stop_timezone VARCHAR(100),
level_id VARCHAR(50),
wheelchair_boarding INT DEFAULT 0,
platform_code VARCHAR(50)
);

-- -------------------------------------------------------------------------
-- TABLE: trips
-- -------------------------------------------------------------------------
CREATE TABLE trips (
route_id VARCHAR(100) NOT NULL,
service_id VARCHAR(100) NOT NULL,
trip_id VARCHAR(100) PRIMARY KEY,
trip_headsign VARCHAR(255),
trip_short_name VARCHAR(100),
direction_id INT,
block_id VARCHAR(100),
shape_id VARCHAR(100),
wheelchair_accessible INT DEFAULT 0,
bikes_allowed INT DEFAULT 0
);

-- -------------------------------------------------------------------------
-- TABLE: pathways
-- -------------------------------------------------------------------------
CREATE TABLE pathways (
pathway_id VARCHAR(100) PRIMARY KEY,
from_stop_id VARCHAR(100) NOT NULL,
to_stop_id VARCHAR(100) NOT NULL,
pathway_mode INT NOT NULL,
is_bidirectional INT NOT NULL DEFAULT 1,
length NUMERIC(6, 2),
traversal_time INT,
stair_count INT,
max_slope NUMERIC(4, 2),
min_width NUMERIC(4, 2),
signposted_as VARCHAR(255),
reversed_signposted_as VARCHAR(255)
);

-- -------------------------------------------------------------------------
-- TABLE: transfers
-- -------------------------------------------------------------------------
CREATE TABLE transfers (
from_stop_id VARCHAR(100) NOT NULL,
to_stop_id VARCHAR(100) NOT NULL,
transfer_type INT DEFAULT 0,
min_transfer_time INT
);

-- -------------------------------------------------------------------------
-- TABLE: stop_extensions
-- -------------------------------------------------------------------------
CREATE TABLE stop_extensions (
object_id VARCHAR(100) NOT NULL,
object_system VARCHAR(100) NOT NULL,
object_code VARCHAR(150) NOT NULL
);

-- -------------------------------------------------------------------------
-- TABLE: stop_times
-- -------------------------------------------------------------------------
CREATE TABLE stop_times (
trip_id VARCHAR(100) NOT NULL,
arrival_time VARCHAR(8) NOT NULL,
departure_time VARCHAR(8) NOT NULL,
stop_id VARCHAR(100) NOT NULL,
stop_sequence INT NOT NULL,
pickup_type INT DEFAULT 0,
drop_off_type INT DEFAULT 0,
local_zone_id VARCHAR(50),
stop_headsign VARCHAR(255),
timepoint INT DEFAULT 1
);


-- =========================================================================
-- CENTRALISATION DES INDEX
-- =========================================================================
CREATE INDEX idx_agency_name ON agency(agency_name);
CREATE INDEX idx_calendar_dates ON calendar(start_date, end_date);
CREATE INDEX idx_calendar_dates_single ON calendar_dates(date);
CREATE INDEX idx_routes_agency ON routes(agency_id);
CREATE INDEX idx_routes_type ON routes(route_type);
CREATE INDEX idx_stops_coordinates ON stops(stop_lat, stop_lon);
CREATE INDEX idx_stops_parent ON stops(parent_station);
CREATE INDEX idx_stops_name ON stops(stop_name);
CREATE INDEX idx_trips_route ON trips(route_id);
CREATE INDEX idx_trips_service ON trips(service_id);
CREATE INDEX idx_trips_shape ON trips(shape_id);
CREATE INDEX idx_pathways_from ON pathways(from_stop_id);
CREATE INDEX idx_pathways_to ON pathways(to_stop_id);
CREATE INDEX idx_transfers_from ON transfers(from_stop_id);
CREATE INDEX idx_transfers_to ON transfers(to_stop_id);
CREATE INDEX idx_stop_extensions_code ON stop_extensions(object_code);
CREATE INDEX idx_stop_extensions_system ON stop_extensions(object_system);
CREATE INDEX idx_stop_times_trip ON stop_times(trip_id);
CREATE INDEX idx_stop_times_stop ON stop_times(stop_id);