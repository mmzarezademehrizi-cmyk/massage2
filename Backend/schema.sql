CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, salt TEXT NOT NULL, hash TEXT NOT NULL, token TEXT UNIQUE NOT NULL);
CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, receiver TEXT NOT NULL, text TEXT, file_key TEXT, file_name TEXT, file_type TEXT, ts INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS i_s ON messages(sender, receiver, id);
CREATE INDEX IF NOT EXISTS i_r ON messages(receiver, sender, id);
