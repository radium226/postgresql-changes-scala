CREATE TABLE
    persons (
        id SERIAL PRIMARY KEY,
        firstName TEXT, 
        lastName TEXT, 
        tags TEXT ARRAY
    );

ALTER TABLE persons REPLICA IDENTITY FULL;

SELECT pg_create_logical_replication_slot('my_slot', 'pgoutput');

CREATE PUBLICATION my_publication FOR ALL TABLES;

SELECT * FROM pg_logical_slot_get_binary_changes('my_slot', NULL, NULL, 'proto_version', '1', 'publication_names', 'my_publication');
