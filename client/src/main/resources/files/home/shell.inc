# Stool shell code

__projectDir() {
  local dir="$1"
  while [ "${dir}" != "/" ] ; do
    if [ -f "${dir}"/.backstage ] ; then
      echo -n "${dir}"
      return
    fi
    dir="$(dirname "${dir}")"
  done
  echo -n ""
  return
}

__stageIndicator() {
  local project=$(__projectDir $1)
  if [ -f "${project}/.backstage" ] ; then
      local id="$(cat "${project}/.backstage")"
      echo -n "{${id}} "
      return
  else
    # no projects at all -> no indicator
    return
  fi
}

__stageName() {
  grep \"name\" "$1"/config.json | cut -d':' -f2 | cut -d'"' -f2
}

if [ -n "${ZSH_VERSION}" ] ; then
    if [ -z "${STOOL_NO_PROMPT}" ] ; then
        __stoolPrompt() {
            local dir=$(pwd)
            echo "$(__stageIndicator $dir)"
        }
        autoload -Uz add-zsh-hook
        add-zsh-hook precmd __stoolPrompt
    # else
    #   do nothing, the user does not want stool prompt
    fi
elif [ -n "${BASH_VERSION}" ] ; then
    #
    # bash prompt
    #

    if [ -z "${STOOL_NO_PROMPT}" ] ; then
        if [ -z ${STOOL_OLD_PROMPT_COMMAND} ] ; then
            export STOOL_OLD_PROMPT_COMMAND=${PROMPT_COMMAND}
            __stoolPromptUpdate() {
                eval "${STOOL_OLD_PROMPT_COMMAND}"
                local dir=$(pwd)
                if [ "x${dir}" != "x${__PROJECT_DIR}" ] ; then
                  # cwd has changed, update stage dir
                  export __PROJECT_DIR="$dir"
                fi
                if [ "x${STOOL_PS1}" = "x" ] ; then
                  # We're called for the first time: setup PS1.
                  # Note that we have to delay this setup until the first PROMPT_COMMAND invocation to give users a chance to customize
                  # PS1 settings (e.g. in their ~/.bashrc for interactive shells)
                  export STOOL_PS1="${PS1}"
                  PS1="\$(__stageIndicator \${__PROJECT_DIR})${STOOL_PS1}"
                # else
                #   already initialized, nothing to do
                fi
            }
            PROMPT_COMMAND=__stoolPromptUpdate
        # else
        #   Already initialized, do nothing
        #   CAUTION:
        #   Initializing again would cause an infinite loop because __stoolPromptCommand calls itself ::stoolPromptCommand from $STOOL_OLD_PROMPT_COMMAND
        fi
    # else
    #   do nothing, the user does not want stool prompt
    fi


    #
    # bash completion
    #

    _stool_stageList() {
      # TODO
      echo none
    }

    _stool_complete() {
          local cur prev cmd
          local select_opts general_opts
          COMPREPLY=()
          cur="${COMP_WORDS[COMP_CWORD]}"
          prev="${COMP_WORDS[COMP_CWORD-1]}"
          cmd="attach build config create detach help history list remove restart setup start status stop tunnel validate version"
          general_opts="-v -e"
          select_opts="-stage -all"
          fail_opts="before after never"

          case "${prev}" in
            create)
                if [[ ${cur} == -* ]] ; then
                    local option="-quiet -prefix -name "
                    COMPREPLY=( $(compgen -W "${option}" -- ${cur}) )
                    return 0
                fi
                # url
                return 0
                ;;
            status)
                local stages=$(_stool_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            tunnel)
                local stages=$(_stool_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            attach)
                local stages=$(_stool_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            detach)
                local stages=$(_stool_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            build)
                local stages=$(_stool_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            stop)
                if [[ ${cur} == -* ]] ; then
                    local option="-stail"
                    COMPREPLY=( $(compgen -W "${option}" -- ${cur}) )
                fi
                ;;
            config)
                compopt +o nospace &>/dev/null
                local option="suffix expire select port.prefix build autoRefresh comment"
                COMPREPLY=( $(compgen -o nospace -W "${option}" -- ${cur}) )
                return 0
                ;;
            restart)
                if [[ ${cur} == -* ]] ; then
                    local option="-debug"
                    COMPREPLY=( $(compgen -W "${option}" -- ${cur}) )
                fi
                ;;
            remove)
                if [[ ${cur} == -* ]] ; then
                    local option="-force -batch"
                    COMPREPLY=( $(compgen -W "${option}" -- ${cur}) )
                    return 0
                fi
                ;;
            *)
                ;;
          esac

          COMPREPLY=( $(compgen -W "${cmd}" -- ${cur}) )
          return 0
    }

    complete -F _stool_complete stool
fi
