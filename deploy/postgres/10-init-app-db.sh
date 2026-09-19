#!/bin/sh
# The official PostgreSQL image runs this only while initializing an empty
# PGDATA directory. It intentionally has no retry, repair, reset, or rotation
# path for an existing cluster.
set -eu

LC_ALL=C
export LC_ALL

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

DB_NAME=${DB_NAME-}
DB_USERNAME=${DB_USERNAME-}
DB_PASSWORD=${DB_PASSWORD-}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD-}

validate_identifier() {
    identifier=$1
    variable_name=$2

    case "$identifier" in
        ''|[!a-z_]*|*[!a-z0-9_]*)
            fail "$variable_name must be an ASCII lower-case PostgreSQL identifier"
            ;;
    esac

    if [ "${#identifier}" -gt 63 ]; then
        fail "$variable_name must be at most 63 characters"
    fi

    case "$identifier" in
        postgres|template0|template1|pg_*)
            fail "$variable_name uses a reserved PostgreSQL name"
            ;;
    esac
}

line_feed='
'
carriage_return="$(printf '\r')"

validate_secret() {
    secret=$1
    variable_name=$2

    [ -n "$secret" ] || fail "$variable_name must be non-empty"
    case "$secret" in
        *"$line_feed"*|*"$carriage_return"*)
            fail "$variable_name must be single-line"
            ;;
    esac
}

validate_identifier "$DB_NAME" DB_NAME
validate_identifier "$DB_USERNAME" DB_USERNAME
validate_secret "$DB_PASSWORD" DB_PASSWORD
validate_secret "$POSTGRES_PASSWORD" POSTGRES_PASSWORD

if [ "$DB_PASSWORD" = "$POSTGRES_PASSWORD" ]; then
    fail "DB_PASSWORD and POSTGRES_PASSWORD must differ"
fi

psql -X -w --quiet --username=postgres --dbname=postgres \
    --set=ON_ERROR_STOP=1 \
    --set=db_username="$DB_USERNAME" \
    --command 'CREATE ROLE :"db_username" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;'

# psql encrypts this client-side before sending ALTER ROLE. The secret is
# supplied only on stdin, never in SQL, an argv value, a file, or shell output.
printf '%s\n%s\n' "$DB_PASSWORD" "$DB_PASSWORD" | \
    psql -X -w --quiet --username=postgres --dbname=postgres \
        --set=ON_ERROR_STOP=1 \
        --set=db_username="$DB_USERNAME" \
        --command '\password :db_username'

psql -X -w --quiet --username=postgres --dbname=postgres \
    --set=ON_ERROR_STOP=1 \
    --set=db_name="$DB_NAME" \
    --set=db_username="$DB_USERNAME" \
    --command 'CREATE DATABASE :"db_name" OWNER :"db_username";'

psql -X -w --quiet --username=postgres --dbname="$DB_NAME" \
    --set=ON_ERROR_STOP=1 \
    --set=db_name="$DB_NAME" \
    --set=db_username="$DB_USERNAME" <<'SQL'
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"db_username";
GRANT CONNECT ON DATABASE :"db_name" TO :"db_username";
GRANT USAGE, CREATE ON SCHEMA public TO :"db_username";
CREATE EXTENSION vector WITH SCHEMA public;
SQL
