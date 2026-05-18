-- Create a large table to take up some space
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100),
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data TEXT
);

-- Insert 100,000 rows
INSERT INTO users (username, email, data)
SELECT
    'user_' || i,
    'user_' || i || '@example.com',
    repeat('xyz', 100) -- Add some padding to increase DB size
FROM generate_series(1, 100000) i;

-- Create another table without an index to simulate slow queries
CREATE TABLE logs (
    id SERIAL PRIMARY KEY,
    user_id INT,
    action VARCHAR(50),
    log_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert 500,000 rows into logs (this will take a few seconds during init)
INSERT INTO logs (user_id, action)
SELECT
    (random() * 100000)::INT,
    'action_' || (random() * 10)::INT
FROM generate_series(1, 500000) i;
