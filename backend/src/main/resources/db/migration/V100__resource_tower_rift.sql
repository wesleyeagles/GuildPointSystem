-- Tower Rift Metal ("[67]Tower Rift Metal" no jogo) em Crag Mind Field: rastreado como os recursos, com
-- categoria própria (tier 4) para o filtro do mapa. A DLL tira o "[nível]" do nome antes de enviar.
ALTER TABLE resource_mob DROP CONSTRAINT IF EXISTS resource_mob_tier_check;
ALTER TABLE resource_mob ADD CONSTRAINT resource_mob_tier_check CHECK (tier BETWEEN 0 AND 4);

INSERT INTO resource_mob(name, map_code, tier) VALUES ('Tower Rift Metal', 2, 4)
ON CONFLICT (name) DO UPDATE SET tier = 4;
