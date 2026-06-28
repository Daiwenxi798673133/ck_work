#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DDL_DIR="$SCRIPT_DIR/ddl"
CH_URL="http://localhost:8123/"
CH_CONTAINER="clickhouse-flow"

execute_sql() {
    local file="$1"
    echo "Executing: $file"
    curl -s --fail \
        --data-binary "@$file" \
        "$CH_URL"
    echo " [OK]"
}

execute_sql_multi() {
    local file="$1"
    echo "Executing (multiquery): $file"
    docker exec -i "$CH_CONTAINER" clickhouse-client \
        --user default \
        --database flow \
        --multiquery < "$file"
    echo " [OK]"
}

execute_sql "$DDL_DIR/01_dim_region.sql"
execute_sql "$DDL_DIR/02_dim_cell.sql"
execute_sql "$DDL_DIR/03_dim_user_profile.sql"
execute_sql "$DDL_DIR/04_dwd_cell_imsi_5min.sql"
execute_sql "$DDL_DIR/05_dws_user_window_loc.sql"
execute_sql_multi "$DDL_DIR/06_dim_data.sql"

echo "All DDL executed successfully."
