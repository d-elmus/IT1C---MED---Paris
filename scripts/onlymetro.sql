-- =========================================================================
-- SCRIPT D'ÉLAGAGE ET NETTOYAGE GLOBAL (CIBLE : MÉTRO & TRAIN/RER)
-- =========================================================================

-- 1. On ne garde que le Métro (1) et le Train/RER (2). Tout le reste (Bus, Tram).
DELETE FROM routes
WHERE route_type <> 1 AND route_type <> 2;

-- 2. On supprime les agences qui n'ont plus aucune ligne associée
DELETE FROM agency
WHERE agency_id NOT IN (SELECT DISTINCT agency_id FROM routes WHERE agency_id IS NOT NULL);

-- 3. On supprime les trips (trajets) qui n'appartiennent plus à aucune ligne valide
DELETE FROM trips
WHERE route_id NOT IN (SELECT route_id FROM routes);

-- 4. On nettoie le calendrier régulier orphelin
DELETE FROM calendar
WHERE service_id NOT IN (SELECT DISTINCT service_id FROM trips);

-- 5. On nettoie les exceptions de calendrier orphelines
DELETE FROM calendar_dates
WHERE service_id NOT IN (SELECT DISTINCT service_id FROM trips);

-- 6. On supprime les millions d'horaires (stop_times) liés à des trajets supprimés
DELETE FROM stop_times
WHERE trip_id NOT IN (SELECT trip_id FROM trips);

-- 7. On supprime les arrêts/gares (stops) qui ne sont plus desservis par aucun horaire
DELETE FROM stops
WHERE stop_id NOT IN (SELECT DISTINCT stop_id FROM stop_times);

-- 8. On nettoie les tunnels/couloirs (pathways) liés à des arrêts supprimés
DELETE FROM pathways
WHERE from_stop_id NOT IN (SELECT stop_id FROM stops)
   OR to_stop_id NOT IN (SELECT stop_id FROM stops);

-- 9. On nettoie les correspondances (transfers) liées à des arrêts supprimés
DELETE FROM transfers
WHERE from_stop_id NOT IN (SELECT stop_id FROM stops)
   OR to_stop_id NOT IN (SELECT stop_id FROM stops);

-- 10. On nettoie les extensions NeTEx/IDFM liées à des arrêts supprimés
DELETE FROM stop_extensions
WHERE object_id NOT IN (SELECT stop_id FROM stops);

VACUUM FULL ANALYZE;