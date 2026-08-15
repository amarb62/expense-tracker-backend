-- Adds a display color to categories for the frontend's category chips/charts.
-- New rows going forward always receive an explicit caller-supplied color; the
-- DEFAULT below only exists as a backfill/migration safety net for the rows
-- already seeded by V2.

ALTER TABLE categories ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#64748b';

-- Backfill the already-seeded rows with a rotating 7-color palette (deterministic,
-- keyed off insertion order) so they aren't all identical -- purely cosmetic.
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM categories
)
UPDATE categories c
SET color = CASE (o.rn - 1) % 7
    WHEN 0 THEN '#0f766e'
    WHEN 1 THEN '#2563eb'
    WHEN 2 THEN '#7c3aed'
    WHEN 3 THEN '#db2777'
    WHEN 4 THEN '#ea580c'
    WHEN 5 THEN '#65a30d'
    WHEN 6 THEN '#0891b2'
END
FROM ordered o
WHERE c.id = o.id;
