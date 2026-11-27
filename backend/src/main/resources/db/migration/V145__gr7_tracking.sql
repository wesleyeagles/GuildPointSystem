ALTER TABLE resource_mob DROP CONSTRAINT resource_mob_tier_check;
ALTER TABLE resource_mob ADD CONSTRAINT resource_mob_tier_check CHECK(tier BETWEEN 0 AND 8);
INSERT INTO resource_mob(name,map_code,tier) VALUES
('[gr7] Mech. Commander',55,8);
