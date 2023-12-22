-- --------------------------------------------------------
-- Host:                         PowerACE-Inputdata
-- Server-Version:               3.44.0
-- Server-Betriebssystem:        
-- HeidiSQL Version:             12.6.0.6765
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES  */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Exportiere Datenbank-Struktur für PowerACE-Inputdata
CREATE DATABASE IF NOT EXISTS "PowerACE-Inputdata";
;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.CarbonPrices
CREATE TABLE IF NOT EXISTS "CarbonPrices" (
	"day_of_year" INTEGER NULL, "2025" INTEGER NULL, "2030" INTEGER NULL, "2040" INTEGER NULL, "2050" INTEGER NULL);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.CarbonPrices: -1 rows
/*!40000 ALTER TABLE "CarbonPrices" DISABLE KEYS */;
/*!40000 ALTER TABLE "CarbonPrices" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.Companies
CREATE TABLE IF NOT EXISTS "Companies" (
	"ID" INTEGER NOT NULL, "company_name" VARCHAR(50) NULL DEFAULT NULL, "market_area" VARCHAR(50) NULL DEFAULT NULL,
	PRIMARY KEY ("ID")
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.Companies: -1 rows
/*!40000 ALTER TABLE "Companies" DISABLE KEYS */;
INSERT INTO "Companies" ("ID", "company_name", "market_area") VALUES
	(1, 'PowerACE', 'DE; IT; FI');
/*!40000 ALTER TABLE "Companies" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.Demand_Profiles
CREATE TABLE IF NOT EXISTS "Demand_Profiles" (
	"area" CHAR(2) NULL DEFAULT NULL,
	"hour_of_year" INTEGER NULL DEFAULT NULL, "2025" INTEGER NULL, "2030" INTEGER NULL, "2040" INTEGER NULL, "2050" INTEGER NULL);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.Demand_Profiles: -1 rows
/*!40000 ALTER TABLE "Demand_Profiles" DISABLE KEYS */;
/*!40000 ALTER TABLE "Demand_Profiles" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.ExchangeFlows_From_XX_To_YY
CREATE TABLE IF NOT EXISTS "ExchangeFlows_From_XX_To_YY" (
	"day_of_year" INTEGER NULL,
	"2025" INTEGER NULL,
	"2030" INTEGER NULL,
	"2040" INTEGER NULL,
	"2050" INTEGER NULL
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.ExchangeFlows_From_XX_To_YY: -1 rows
/*!40000 ALTER TABLE "ExchangeFlows_From_XX_To_YY" DISABLE KEYS */;
/*!40000 ALTER TABLE "ExchangeFlows_From_XX_To_YY" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.FuelPrices
CREATE TABLE IF NOT EXISTS "FuelPrices" (
	"nr" INTEGER NOT NULL,
	"name" CHAR(10) NULL DEFAULT NULL,
	"2025" INTEGER NULL,
	"2030" INTEGER NULL,
	"2040" INTEGER NULL,
	"2050" INTEGER NULL,
	PRIMARY KEY ("nr")
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.FuelPrices: -1 rows
/*!40000 ALTER TABLE "FuelPrices" DISABLE KEYS */;
INSERT INTO "FuelPrices" ("nr", "name", "2025", "2030", "2040", "2050") VALUES
	(1, 'uranium', NULL, NULL, NULL, NULL),
	(2, 'coal', NULL, NULL, NULL, NULL),
	(3, 'lignite', NULL, NULL, NULL, NULL),
	(4, 'oil', NULL, NULL, NULL, NULL),
	(5, 'gas', NULL, NULL, NULL, NULL);
/*!40000 ALTER TABLE "FuelPrices" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.NetTransferCapacities
CREATE TABLE IF NOT EXISTS "NetTransferCapacities" (
	"year" INTEGER NULL,
	"from" CHAR(50) NULL DEFAULT NULL,
	"to" CHAR(50) NULL DEFAULT NULL,
	"winter" INTEGER NULL,
	"summer" INTEGER NULL
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.NetTransferCapacities: -1 rows
/*!40000 ALTER TABLE "NetTransferCapacities" DISABLE KEYS */;
/*!40000 ALTER TABLE "NetTransferCapacities" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.NukeAvailability
CREATE TABLE IF NOT EXISTS "NukeAvailability" (
	"month" INTEGER NULL,
	"2025" INTEGER NULL,
	"2030" INTEGER NULL
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.NukeAvailability: -1 rows
/*!40000 ALTER TABLE "NukeAvailability" DISABLE KEYS */;
INSERT INTO "NukeAvailability" ("month", "2025", "2030") VALUES
	(1, NULL, NULL);
/*!40000 ALTER TABLE "NukeAvailability" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.OperationAndMaintenanceCosts_fixed
CREATE TABLE IF NOT EXISTS "OperationAndMaintenanceCosts_fixed" (
	"scenario" VARCHAR(50) NULL DEFAULT NULL,
	"COAL_SUB" REAL NULL DEFAULT NULL,
	"COAL_SUPER" REAL NULL DEFAULT NULL,
	"COAL_ULTRASUPER" REAL NULL DEFAULT NULL
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.OperationAndMaintenanceCosts_fixed: -1 rows
/*!40000 ALTER TABLE "OperationAndMaintenanceCosts_fixed" DISABLE KEYS */;
/*!40000 ALTER TABLE "OperationAndMaintenanceCosts_fixed" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.OperationAndMaintenanceCosts_var
CREATE TABLE IF NOT EXISTS "OperationAndMaintenanceCosts_var" (
	"scenario" VARCHAR(50) NULL,
	"COAL_SUB" REAL NULL,
	"COAL_SUPER" REAL NULL,
	"COAL_ULTRASUPER" REAL NULL
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.OperationAndMaintenanceCosts_var: -1 rows
/*!40000 ALTER TABLE "OperationAndMaintenanceCosts_var" DISABLE KEYS */;
/*!40000 ALTER TABLE "OperationAndMaintenanceCosts_var" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.PlantAvailability
CREATE TABLE IF NOT EXISTS "PlantAvailability" (
	"scenario_id" INTEGER NULL,
	"scenario" VARCHAR(50) NULL DEFAULT NULL,
	"lignite" INTEGER NULL, "ligniteWeekend" INTEGER NULL);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.PlantAvailability: -1 rows
/*!40000 ALTER TABLE "PlantAvailability" DISABLE KEYS */;
/*!40000 ALTER TABLE "PlantAvailability" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.Powerplant
CREATE TABLE IF NOT EXISTS "Powerplant" (
    power_plant_block_id INTEGER PRIMARY KEY AUTOINCREMENT,
    "bna_number" VARCHAR(20),
    block_name VARCHAR(50),
    power_gross FLOAT,
    power_net FLOAT,
    power_min FLOAT,
    "power_mustrun" FLOAT,
    efficiency FLOAT,
    "mustrun" TINYINT,
    "mustrun_chp" TINYINT,
    fuel_ref INT,
    primary_fuel VARCHAR(20),
    other_fuel VARCHAR(20),
    technology_ref INT,
    co2_fact FLOAT,
    construction_year INT,
    retrofit_year INT,
    lifetime INT,
    shut_down INT,
    system_relevance TINYINT,
    owner_ref INT,
    status_ref INT,
    deactivated TINYINT,
    "state" VARCHAR(50),
    country_ref INT,
    country VARCHAR(50),
    location VARCHAR(50),
    postal_code VARCHAR(8),
    lat FLOAT,
    lon FLOAT);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.Powerplant: -1 rows
/*!40000 ALTER TABLE "Powerplant" DISABLE KEYS */;
/*!40000 ALTER TABLE "Powerplant" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.RES_capacities
CREATE TABLE IF NOT EXISTS "RES_capacities" (
	 area_code TEXT,
    res_type TEXT,
    
    year1 FLOAT,
    year2 FLOAT,
    year3 FLOAT,
    -- Weitere Jahres-Spalten entsprechend der in der Anwendung verwendeten Jahre
    -- ...
    PRIMARY KEY (res_type, area_code)
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.RES_capacities: -1 rows
/*!40000 ALTER TABLE "RES_capacities" DISABLE KEYS */;
/*!40000 ALTER TABLE "RES_capacities" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.RES_production
CREATE TABLE IF NOT EXISTS RES_production (
    
    area TEXT,
    type TEXT,
    year INTEGER,
    hour_of_year INTEGER,
    value FLOAT,
    PRIMARY KEY (type, area, year, hour_of_year)
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.RES_production: 0 rows
/*!40000 ALTER TABLE "RES_production" DISABLE KEYS */;
/*!40000 ALTER TABLE "RES_production" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.StartupCosts
CREATE TABLE IF NOT EXISTS "StartupCosts" (
	"scenario" VARCHAR(50) NULL DEFAULT NULL,
	"COAL_SUB_Depeciation" REAL NULL DEFAULT NULL,
	"COAL_SUB_FuelFactor" REAL NULL DEFAULT NULL
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.StartupCosts: -1 rows
/*!40000 ALTER TABLE "StartupCosts" DISABLE KEYS */;
/*!40000 ALTER TABLE "StartupCosts" ENABLE KEYS */;

-- Exportiere Struktur von Tabelle PowerACE-Inputdata.TechnologyOption
CREATE TABLE IF NOT EXISTS "TechnologyOption" (
	"id" INTEGER NULL,
	"option_name" VARCHAR(50) NULL DEFAULT NULL,
	"availability_year" INTEGER NULL DEFAULT NULL,
	"expire_year" INTEGER NULL DEFAULT NULL,
	"fuel_name_index" INTEGER NULL DEFAULT NULL,
	"technology_index" INTEGER NULL DEFAULT NULL,
	"storage" INTEGER NULL DEFAULT NULL,
	"block_size" INTEGER NULL DEFAULT NULL,
	"storage_capacity" INTEGER NULL DEFAULT NULL,
	"electrical_efficiency" INTEGER NULL DEFAULT NULL,
	"lifetime" INTEGER NULL DEFAULT NULL,
	"construction_time" INTEGER NULL DEFAULT NULL,
	"investment_specific" INTEGER NULL DEFAULT NULL,
	"var_om_costs" INTEGER NULL DEFAULT NULL,
	"activated" INTEGER NULL DEFAULT NULL
);

-- Exportiere Daten aus Tabelle PowerACE-Inputdata.TechnologyOption: -1 rows
/*!40000 ALTER TABLE "TechnologyOption" DISABLE KEYS */;
/*!40000 ALTER TABLE "TechnologyOption" ENABLE KEYS */;

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
