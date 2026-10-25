#!/usr/bin/env bash
set -Eeuo pipefail

# Resumable backup of an F-Droid Termux installation.  The destination is
# deliberately outside the repository; it may contain credentials and large
# package/build trees.

HOST="${1:-flip7}"
DEFAULT_BACKUP_ROOT="${TERMUX_BACKUP_ROOT:-$PWD/../termux-backups}"
DEST="${2:-$DEFAULT_BACKUP_ROOT/termux-fdroid-$(date +%F)}"
REMOTE_ROOT="/data/data/com.termux"
RETRY_DELAY="${RETRY_DELAY:-20}"

SSH_OPTS=(-o ConnectTimeout=12 -o ServerAliveInterval=15 -o ServerAliveCountMax=2)
RSYNC_OPTS=(-a --numeric-ids --partial --append-verify --human-readable \
    --info=progress2,stats2 --timeout=30)

mkdir -p "$DEST/files/home" "$DEST/files/usr" \
    "$DEST/package-data/shared_prefs" "$DEST/package-data/databases"
chmod 700 "$DEST"
rm -f "$DEST/BACKUP-COMPLETE"

exec 9>"$DEST/.backup.lock"
if ! flock -n 9; then
    printf 'A backup is already running for %s\n' "$DEST" >&2
    exit 1
fi

log() {
    printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

ssh_once() {
    ssh "${SSH_OPTS[@]}" "$HOST" "$@"
}

run_with_retry() {
    local label="$1"
    shift
    local attempt=1

    while true; do
        log "$label (attempt $attempt)"
        if "$@"; then
            log "$label completed"
            return 0
        fi
        log "$label failed; preserving partial data and retrying in ${RETRY_DELAY}s"
        attempt=$((attempt + 1))
        sleep "$RETRY_DELAY"
    done
}

sync_tree() {
    local label="$1"
    local remote="$2"
    local local_path="$3"

    log "$label: syncing $HOST:$remote/ -> $local_path/"
    log "$label: rsync will first scan the remote file list; progress appears once transfer begins"
    log "$label: a quiet period means file-list scanning or connection setup; I/O timeout is 30s"
    run_with_retry "Sync $label" rsync "${RSYNC_OPTS[@]}" \
        -e "ssh ${SSH_OPTS[*]}" \
        "$HOST:$remote/" "$local_path/"
}

sync_optional_tree() {
    local label="$1"
    local remote="$2"
    local local_path="$3"

    if remote_dir_exists "$remote"; then
        sync_tree "$label" "$remote" "$local_path"
    else
        log "$label is absent on the phone; recording an empty backup"
    fi
}

remote_dir_exists() {
    local remote="$1"
    local status

    while true; do
        ssh_once "test -d '$remote'" && return 0
        status=$?
        if [[ "$status" -eq 1 ]]; then
            return 1
        fi
        log "Checking $remote failed due to the connection; retrying in ${RETRY_DELAY}s"
        sleep "$RETRY_DELAY"
    done
}

record_remote_file() {
    local name="$1"
    local command="$2"
    local temporary="$DEST/package-data/.${name}.tmp"
    local output="$DEST/package-data/$name"

    while true; do
        log "Collecting $name"
        if ssh_once "$command" >"$temporary"; then
            mv -f "$temporary" "$output"
            return 0
        fi
        log "Collecting $name failed; retrying in ${RETRY_DELAY}s"
        sleep "$RETRY_DELAY"
    done
}

verify_tree() {
    local label="$1"
    local remote="$2"
    local local_path="$3"
    local report="$DEST/package-data/.verify-${label// /_}.tmp"

    while true; do
        log "$label verification"
        : >"$report"
        if rsync "${RSYNC_OPTS[@]}" --dry-run --delete --itemize-changes \
            --out-format='%i %n%L' -e "ssh ${SSH_OPTS[*]}" \
            "$HOST:$remote/" "$local_path/" >"$report"; then
            if [[ ! -s "$report" ]]; then
                rm -f "$report"
                log "$label verified"
                return 0
            fi
            log "$label differs; retrying after ${RETRY_DELAY}s"
        else
            log "$label verification lost the connection; retrying after ${RETRY_DELAY}s"
        fi
        sleep "$RETRY_DELAY"
    done
}

log "Starting resumable backup from $HOST"
run_with_retry "Checking remote Termux" ssh_once "test -d '$REMOTE_ROOT/files/home'"

sync_tree "Termux home" "$REMOTE_ROOT/files/home" "$DEST/files/home"
sync_tree "Termux prefix" "$REMOTE_ROOT/files/usr" "$DEST/files/usr"

# These are outside files/ and would otherwise be lost by uninstalling the
# Android package.  They are small and copied atomically after reconnects.
sync_optional_tree "Termux shared preferences" "$REMOTE_ROOT/shared_prefs" "$DEST/package-data/shared_prefs"
sync_optional_tree "Termux databases" "$REMOTE_ROOT/databases" "$DEST/package-data/databases"

record_remote_file manual-packages.txt "apt-mark showmanual | sort"
record_remote_file installed-packages.tsv \
    "dpkg-query -W -f='\${binary:Package}\t\${Version}\n' | sort"

log "Verifying directory metadata with rsync dry runs"
verify_tree "Termux home" "$REMOTE_ROOT/files/home" "$DEST/files/home"
verify_tree "Termux prefix" "$REMOTE_ROOT/files/usr" "$DEST/files/usr"

printf 'host=%s\ncompleted=%s\n' "$HOST" "$(date --iso-8601=seconds)" >"$DEST/BACKUP-COMPLETE"
chmod 600 "$DEST/BACKUP-COMPLETE"
log "Backup complete: $DEST"
