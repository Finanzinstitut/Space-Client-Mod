import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";

/**
 * A stand-in for D1, over the real SQLite that Node now ships with.
 *
 * Worth the twenty lines: it means the tests run the worker's actual SQL -
 * the ON CONFLICT clauses, the IN (?,?,?) binding, the COLLATE NOCASE lookup -
 * rather than a mock that agrees with whatever the code happens to do.
 */
export function makeDb(schemaPath) {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(schemaPath, "utf8"));

  const statement = (sql) => {
    let bound = [];
    const self = {
      bind(...args) { bound = args; return self; },
      async first() {
        const row = db.prepare(sql).get(...bound);
        return row === undefined ? null : row;
      },
      async all() {
        return { results: db.prepare(sql).all(...bound) };
      },
      async run() {
        return db.prepare(sql).run(...bound);
      },
      __run() { return db.prepare(sql).run(...bound); },
    };
    return self;
  };

  return {
    prepare: statement,
    async batch(statements) {
      // D1 runs a batch in one go; the important part for the tests is that
      // every statement in it runs, in order
      return statements.map((s) => s.__run());
    },
    raw: db,
  };
}
