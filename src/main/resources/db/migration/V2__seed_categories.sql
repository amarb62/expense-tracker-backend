-- Initial top-level categories (design.md section 3). Hierarchical sub-categories
-- can be added later via parent_id; none are seeded by default.

INSERT INTO categories (id, parent_id, name, category_type, active, created_at, updated_at) VALUES
    (gen_random_uuid(), NULL, 'FOOD', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'GROCERIES', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'SHOPPING', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'TRANSPORT', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'FUEL', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'TRAVEL', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'RENT', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'UTILITIES', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'ENTERTAINMENT', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'HEALTHCARE', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'EDUCATION', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'INSURANCE', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'EMI', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'INVESTMENT', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'SUBSCRIPTION', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'ATM_CASH', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'BANK_CHARGES', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'TAX', 'EXPENSE', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'TRANSFER', 'TRANSFER', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'OTHER', 'EXPENSE', TRUE, now(), now());

-- Categories for manually added income (design.md section 16).
INSERT INTO categories (id, parent_id, name, category_type, active, created_at, updated_at) VALUES
    (gen_random_uuid(), NULL, 'SALARY', 'INCOME', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'BONUS', 'INCOME', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'FREELANCE_INCOME', 'INCOME', TRUE, now(), now()),
    (gen_random_uuid(), NULL, 'OTHER_INCOME', 'INCOME', TRUE, now(), now());
