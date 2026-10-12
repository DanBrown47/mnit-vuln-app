"""Standalone seed script: create the db file and populate it."""
import sys
import config
import db


def main(path=None):
    path = path or config.DEFAULT_DB_PATH
    db.init_db(path)
    db.seed(path)
    print(f"seeded {path}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
