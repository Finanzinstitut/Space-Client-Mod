-- One row per pair, never two: friendship is symmetric, and storing it twice
-- is how you end up friends in one direction only.
CREATE TABLE IF NOT EXISTS links (
  lo        TEXT NOT NULL,
  hi        TEXT NOT NULL,
  state     TEXT NOT NULL,          -- 'pending' or 'accepted'
  requester TEXT NOT NULL,
  since     INTEGER NOT NULL,
  PRIMARY KEY (lo, hi)
);

CREATE TABLE IF NOT EXISTS players (
  uuid TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  seen INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS players_by_name ON players (name COLLATE NOCASE);

CREATE TABLE IF NOT EXISTS sessions (
  token   TEXT PRIMARY KEY,
  uuid    TEXT NOT NULL,
  expires INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  sender    TEXT NOT NULL,
  recipient TEXT NOT NULL,
  body      TEXT NOT NULL,
  sent      INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS messages_for ON messages (recipient, id);
CREATE INDEX IF NOT EXISTS messages_from ON messages (sender, id);

-- A counter per person, raised whenever something of theirs changed. It is
-- what lets a waiting poll notice a new friend request, which is not a message
-- and would otherwise only show up on the next screen opening.
CREATE TABLE IF NOT EXISTS feeds (
  uuid    TEXT PRIMARY KEY,
  version INTEGER NOT NULL DEFAULT 0
);

-- Group chats. A group is a name and a set of members; anybody in it can
-- write to it and bring in their own friends. Kept apart from the one to one
-- tables, so nothing about how two people talk changed when groups arrived.
-- ("chat_groups" rather than "groups", which is a keyword in newer SQLite.)
CREATE TABLE IF NOT EXISTS chat_groups (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  name    TEXT NOT NULL,
  owner   TEXT NOT NULL,
  created INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS group_members (
  group_id INTEGER NOT NULL,
  uuid     TEXT NOT NULL,
  joined   INTEGER NOT NULL,
  PRIMARY KEY (group_id, uuid)
);
CREATE INDEX IF NOT EXISTS group_members_by_uuid ON group_members (uuid);

CREATE TABLE IF NOT EXISTS group_messages (
  id       INTEGER PRIMARY KEY AUTOINCREMENT,
  group_id INTEGER NOT NULL,
  sender   TEXT NOT NULL,
  body     TEXT NOT NULL,
  sent     INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS group_messages_by_group ON group_messages (group_id, id);
