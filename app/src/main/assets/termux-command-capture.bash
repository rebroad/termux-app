# TERMUX_APP_MANAGED_COMMAND_CAPTURE
# Loaded from $PREFIX/etc/profile.d for Termux Bash sessions.
# The app supplies an app-private state-file path for each terminal session.
if [[ -n ${BASH_VERSION:-} && $- == *i* && -n ${TERMUX_APP_COMMAND_CAPTURE_FILE:-} && -z "$(trap -p DEBUG)" ]]; then
    __termux_app_command_state_write() {
        local command_text="$1"
        local working_directory="$PWD"
        local encoded_directory encoded_command temporary_file

        encoded_directory=$(printf '%s' "$working_directory" | base64 | tr -d '\n') || return
        encoded_command=$(printf '%s' "$command_text" | base64 | tr -d '\n') || return
        temporary_file="${TERMUX_APP_COMMAND_CAPTURE_FILE}.tmp.$$"
        if printf '%s\n%s\n' "$encoded_directory" "$encoded_command" > "$temporary_file"; then
            mv -f "$temporary_file" "$TERMUX_APP_COMMAND_CAPTURE_FILE"
        else
            rm -f "$temporary_file"
        fi
    }

    __termux_app_command_preexec() {
        local command_text="$1"
        case "$command_text" in
            __termux_app_command_*|trap\ *|PROMPT_COMMAND=*) return ;;
        esac
        __termux_app_command_state_write "$command_text"
    }

    __termux_app_command_precmd() {
        __termux_app_command_state_write ""
    }

    # Clear the active command after it completes, while retaining its last cwd.
    if declare -p PROMPT_COMMAND 2>/dev/null | grep -q '^declare -a'; then
        PROMPT_COMMAND+=(__termux_app_command_precmd)
    else
        PROMPT_COMMAND="${PROMPT_COMMAND:+${PROMPT_COMMAND}; }__termux_app_command_precmd"
    fi

    # DEBUG runs before a command begins, allowing the app to offer an explicit
    # pin action while that command is running.
    trap '__termux_app_command_preexec "$BASH_COMMAND"' DEBUG
fi
