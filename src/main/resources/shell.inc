
if [ -n "${BASH_VERSION}" ] ; then
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
            delete)
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

