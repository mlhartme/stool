# Stool shell code

__scWorkspaceDir() {
  local dir="$1"
  while [ "${dir}" != "/" ] ; do
    if [ -f "${dir}"/.backstage/workspace.yaml ] ; then
      echo -n "${dir}"
      return
    fi
    dir="$(dirname "${dir}")"
  done
  echo -n ""
  return
}

__scStageIndicator() {
  local workspace=$(__scWorkspaceDir $1)
  if [ -f "${workspace}/.backstage/workspace.yaml" ] ; then
      local ctx="$(grep 'currentContext:' ${HOME}/.sc.yaml | cut -d \" -f 2)"
      local id="$(grep "stages:" -A 100 "${workspace}/.backstage/workspace.yaml" | grep "^  " | sed -e 's/^  .*: \"\(.*\)\"/\1/' -e "s/@${ctx}$//" | tr '\n' ' ')"
      id=${id%?}
      echo -n "> ${id} < "
      return
  else
    # no workspace found -> no indicator
    return
  fi
}

if [ -n "${ZSH_VERSION}" ] ; then
    if [ -z "${SC_NO_PROMPT}" ] ; then
        __scPrompt() {
            local dir=$(pwd)
            local indicator=$(__scStageIndicator $dir)
            if [ -n "$indicator" ] ; then
                echo $indicator
            fi
        }
        autoload -Uz add-zsh-hook
        add-zsh-hook precmd __scPrompt
    # else
    #   do nothing, the user does not want stool prompt
    fi
elif [ -n "${BASH_VERSION}" ] ; then
    #
    # bash prompt
    #

    if [ -z "${SC_NO_PROMPT}" ] ; then
        if [ -z ${SC_OLD_PROMPT_COMMAND} ] ; then
            export SC_OLD_PROMPT_COMMAND=${PROMPT_COMMAND}
            __scPromptUpdate() {
                eval "${SC_OLD_PROMPT_COMMAND}"
                local dir=$(pwd)
                if [ "x${dir}" != "x${__WORKSPACE_DIR}" ] ; then
                  # cwd has changed, update stage dir
                  export __WORKSPACE_DIR="$dir"
                fi
                if [ "x${SC_PS1}" = "x" ] ; then
                  # We're called for the first time: setup PS1.
                  # Note that we have to delay this setup until the first PROMPT_COMMAND invocation to give users a chance to customize
                  # PS1 settings (e.g. in their ~/.bashrc for interactive shells)
                  export SC_PS1="${PS1}"
                  PS1="\$(__scStageIndicator \${__WORKSPACE_DIR})${SC_PS1}"
                # else
                #   already initialized, nothing to do
                fi
            }
            PROMPT_COMMAND=__scPromptUpdate
        # else
        #   Already initialized, do nothing
        #   CAUTION:
        #   Initializing again would cause an infinite loop because __scPromptCommand calls itself ::scPromptCommand from $SC_OLD_PROMPT_COMMAND
        fi
    # else
    #   do nothing, the user does not want stool prompt
    fi


    #
    # bash completion
    #

    _sc_stageList() {
      # TODO
      echo none
    }

    _sc_complete() {
          local cur prev cmd
          local select_opts general_opts
          COMPREPLY=()
          cur="${COMP_WORDS[COMP_CWORD]}"
          prev="${COMP_WORDS[COMP_CWORD-1]}"
          cmd="attach build config create detach help history list remove restart setup ssh start status stop tunnel validate version"
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
                local stages=$(_sc_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            tunnel)
                local stages=$(_sc_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            attach)
                local stages=$(_sc_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            detach)
                local stages=$(_sc_stageList)
                COMPREPLY=( $(compgen -W "${stages}" -- ${cur}) )
                return 0
                ;;
            build)
                local stages=$(_sc_stageList)
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

    complete -F _sc_complete sc
fi

