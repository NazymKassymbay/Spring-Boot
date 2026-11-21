-- Demo catalogue for local development. This folder is added to spring.flyway.locations
-- only in the dev profile, so test and prod databases stay empty.
-- Rows are inserted in a fixed order, so on a fresh database the ids are the same as in http/shop-api.http.

INSERT INTO categories (name, description, created_at, updated_at) VALUES
    ('Laptops',     'Notebooks and ultrabooks', NOW(), NOW()),
    ('Smartphones', 'Android and iOS phones',   NOW(), NOW()),
    ('Books',       'Printed books',            NOW(), NOW());

INSERT INTO products (sku, name, description, price, stock_quantity, category_id, created_at, updated_at)
SELECT p.sku, p.name, p.description, p.price, p.stock_quantity, c.id, NOW(), NOW()
FROM (VALUES
    (1, 'LAP-MBA-13', 'MacBook Air 13',            'Apple M3, 16 GB RAM, 512 GB SSD', 649990.00,  5, 'Laptops'),
    (2, 'LAP-TP-X1',  'Lenovo ThinkPad X1 Carbon', 'Intel Core Ultra 7, 32 GB RAM',   899990.00,  3, 'Laptops'),
    (3, 'PHN-IP-16',  'iPhone 16',                 '128 GB, black',                   459990.00, 10, 'Smartphones'),
    (4, 'PHN-SGS-25', 'Samsung Galaxy S25',        '256 GB, silver',                  419990.00,  0, 'Smartphones'),
    (5, 'BK-SIA-5',   'Spring in Action, 5th ed.', 'Craig Walls, Manning',             18500.00, 25, 'Books')
) AS p (position, sku, name, description, price, stock_quantity, category_name)
JOIN categories c ON c.name = p.category_name
ORDER BY p.position;
