-- Clan di test: IronForge (chunk 0,0) e ShadowPeak (chunk 0,1) nel mondo "world"
-- Leader clan 1: Gabriele6543210 (per test permessi come membro)
-- Leader clan 2: UUID fittizio (clan nemico per test cross-claim)

DELETE cc FROM clan_claims cc
INNER JOIN clans c ON c.id = cc.clan_id
WHERE c.name IN ('IronForge', 'ShadowPeak');

DELETE cm FROM clan_members cm
INNER JOIN clans c ON c.id = cm.clan_id
WHERE c.name IN ('IronForge', 'ShadowPeak');

DELETE FROM clans WHERE name IN ('IronForge', 'ShadowPeak');

INSERT INTO clans (name, tag, leader_uuid) VALUES
    ('IronForge', 'IRON', 'c56dec0e-6f45-3c81-84e7-13a72949373d'),
    ('ShadowPeak', 'SHDW', '11111111-1111-1111-1111-111111111111');

INSERT INTO clan_members (clan_id, player_uuid, role)
SELECT id, leader_uuid, 'LEADER' FROM clans WHERE name IN ('IronForge', 'ShadowPeak');

INSERT INTO clan_claims (clan_id, world, chunk_x, chunk_z)
SELECT id, 'world', 0, 0 FROM clans WHERE name = 'IronForge';

INSERT INTO clan_claims (clan_id, world, chunk_x, chunk_z)
SELECT id, 'world', 0, 1 FROM clans WHERE name = 'ShadowPeak';

SELECT c.id, c.name, c.tag, cc.chunk_x, cc.chunk_z
FROM clans c
LEFT JOIN clan_claims cc ON cc.clan_id = c.id
WHERE c.name IN ('IronForge', 'ShadowPeak')
ORDER BY c.id;
