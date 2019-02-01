var dashboard = dashboard || {};

dashboard = {
    init: function () {
        this.navigation.init();
        this.bootstrap.init();
        this.stages.init();
        this.feedback.init();
    },

    bootstrap: {
        activeFilter: "all",
        init: function () {
            $('#usr-settings-theme').bind('change', function () {
                $('#css').attr('href', $(this).val());
            });

            $('#search').bind('keyup', function () {
                dashboard.stages.filter($(this).val());
            });
            $('#btn-refresh').bind('click', function () {
                dashboard.stages.reload();
            });
            $('.modal').on('hide.bs.modal', function () {
                $(".shell").html("");
                dashboard.stages.reload();
            });
            $('[data-toggle="tab"]').on('click', function () {
                var stages, filter, check;
                stages = $("#all-stages").find("tr");
                filter = $(this).attr("href").slice(1);
                check = '';

                dashboard.bootstrap.activeFilter = filter;

                if (filter==='all') {
                    stages.removeClass('filter');
                } else {
                    stages.addClass('filter');
                }

                $('#empty').toggle(parseInt($(this).find('.badge').html()) === 0);

                stages.each(function () {

                    if ($(this).attr('data-origin')) {

                        if (filter === "trunks") {
                            check = '/trunk';
                        } else if (filter === "branches") {
                            check = '/branches/';
                        } else if (filter === "workspaces") {
                            check = '/workspaces/';
                        }

                        $(this).toggle(filter === 'all' || filter === '' || $(this).attr('data-origin').indexOf(check) > -1);
                    }

                });

                dashboard.stages.filter($('#search').val());

            });

        }
    },

    navigation: {
        init: function () {
            var hash;
            hash = location.hash.slice(1);
            if (hash.indexOf(':') > -1) {
                params = hash.split(':');
                hash = params[0];
            }
            this.hashChange()
            this.loadContentByHash(hash !== '' ? hash : 'dashboard');


        },

        hashChange: function () {
            $(window).bind('hashchange', function () {
                var h, params;
                h = location.hash.slice(1);
                params = null;
                if (h.indexOf(':') > -1) {
                    params = h.split(':');
                    h = params[0];
                }
                if (h !== '') {
                    dashboard.navigation.loadContentByHash(h, params);
                }
                dashboard.navigation.checkLocationHash();
            });
        },

        loadContentByHash: function (h) {

            if ($('div').find('.content.' + h).length === 0) {

                $('.content').toggle(false);
                $('.tab').removeClass('active');
                $('.content.error').toggle(true);

            } else {

                $('.content').each(function () {
                    $(this).toggle(false);
                    if ($(this).hasClass(h)) {
                        $(this).toggle(true);
                        if ($(this).attr('data-title') !== '') {
                            document.title = 'Stage  ' + $(this).attr('data-title');
                        }
                    }
                });

                $('.tab').each(function () {
                    $(this).removeClass('active');
                    if ($(this).hasClass(h)) {
                        $(this).addClass('active');
                    }
                });

            }

        },
        checkLocationHash: function () {

            var hash, i;
            hash = window.location.hash.slice(1);

            if (hash.indexOf(':') > -1) {
                parameters = hash.split(':');
                for (i = 1; i < parameters.length; i += 1) {
                    parameter = parameters[i];
                    if (parameter.indexOf('=') > -1) {
                        param = parameter.split('=');
                        if (param[0] === 'search') {
                            dashboard.stages.filter(param[1]);
                        } else if (param[0] === 'stage') {
                            dashboard.navigation.updateStageBreadcrump(param[1]);
                        }
                    }
                }
            }

        }
    },

    updateStageBreadcrump: function (stagename) {
        $('.breadcrumb .stagename').html(stagename);
    },

    stages: {
        init: function () {
            dashboard.stages.reload();
            $('[data-action]').on('click', dashboard.stages.action);
            $('#loading').remove();
            dashboard.stages.silentReload();
        },


        silentReload: function () {

            dashboard.stages.reload();
            setTimeout(function () {
                dashboard.stages.silentReload();
            }, dashboard.interval.withCounter(0));
        },

        recountTabs: function (wrapper) {
            var trunks, branches, workspaces;

            trunks = wrapper.find(".trunk").length;
            branches = wrapper.find(".branch").length;
            workspaces = wrapper.find(".workspace").length;
            $('#all-count').html(trunks + workspaces + branches);

            $('#trunks-count').html(trunks);
            $('#branches-count').html(branches);
            $('#workspaces-count').html(workspaces);
        },

        reload: function () {
            $.ajax('/stages', {
                dataType: "html",
                success: function (data) {
                    // TODO: handle removed stages. They currently remain in the dashboard until the user presses refresh.

                    var wrapper;
                    wrapper = $('#all-stages');

                    $(data).each(function (i, tr) {
                        var oldTr;
                        var actions;
                        var id;

                        id = tr.id;

                        if (id !== undefined) {
                            id = id.replace(/\./g, "\\.");
                        }
                        oldTr = wrapper.find('#' + id);

                        if (oldTr.length === 0) {
                            // new stage
                            $(tr).find('[data-action]').off('click', dashboard.stages.action);
                            wrapper.append(tr);
                            $(tr).find('[data-action]').on('click', dashboard.stages.action);
                        } else if (oldTr.attr("data-hash") !== $(tr).attr("data-hash")) {
                            // updated stage
                            $(tr).find('[data-action]').off('click', dashboard.stages.action);
                            oldTr.replaceWith(tr);
                            $(tr).find('[data-action]').on('click', dashboard.stages.action);
                        } else {
                            // no changes
                        }
                    });
                    $('[data-toggle="popover"]').popover();
                    dashboard.stages.recountTabs(wrapper);
                    dashboard.navigation.checkLocationHash();
                }
            });
        },

        action: function () {
            var stage, action, arguments, estimate, p, i, url;
            if ($(this).parent().hasClass('disabled') && $(this).attr('data-stage') === null) {
                return false;
            }

            stage = $(this).attr('data-stage');
            action = $(this).attr('data-action');
            arguments = $(this).attr('data-arguments');
            estimate = $(this).attr('data-estimate');
            p = $(this).parent().parent();

            url = "/stages/" + stage + "/" + action;
            $('#' + stage + ' a.action').addClass('disabled');

            $(p).find('.fa').toggle(true);
            $(p).find('.label').attr('class', 'label label-primary').html('n/a');

            if (arguments != null) {
                url = url + "/" + arguments;
            }

            $('.modal-title').text("Console output");
            $('.modal').modal();
            $.post(url).fail(function (r) {
                $(p).find('.fa').toggle(false);
                $(p).find('.label').attr('class', 'label label-warning').html('broken: ' + r);
                $(p).addClass('warning');
                log.error(r);
            }).done(function (r) {
                var spinner;
                $('.modal-body .shell').html("").show();
                spinner = '.fa-spinner';
                $(spinner).show();
                dashboard.stages.showLog('.modal-body .shell', r, 0, spinner, '.modal', estimate);

            });


            return false;

        },
        showLog: function (element, id, index, spinner, parent, estimate) {
            dashboard.stages.fetchLog(element, id, index, spinner, 0, parent);
        },

        fetchLog: function (element, id, index, spinner, lastSize, parent) {
            $.get("/processes/" + id + "/log", {"index": index}).done(function (data, status, response) {
                newSize = response.getResponseHeader("X-Size");
                if (newSize !== lastSize) {
                    lastSize = newSize;
                    if ($(element).text().length > 100000) {
                        $(element).empty();
                    }
                    $(element).append(data);
                    $(parent).animate({scrollTop: $(element).height()}, 'fast');
                }
                if ("true" === response.getResponseHeader("X-Running")) {
                    setTimeout(function () {
                        dashboard.stages.fetchLog(element, id, index, spinner, lastSize, parent);
                    }, 1000);
                } else {
                    $(spinner).toggle(false);
                }
            });

        },

        filter: function (search) {
            if (search !== '') {
                var activeFilter;
                activeFilter = dashboard.bootstrap.activeFilter;
                document.location.hash = '#dashboard:search=' + search;

                $('#all-stages').find('tr.stage').toggle(false);
                $('#empty').toggle(false);

                found = 0;

                $('#all-stages').find('tr.stage' + (activeFilter === 'all' ? '' : '.' + activeFilter)).each(function () {
                    if ($(this).attr('data-origin').indexOf(search) > -1 || $(this).attr('data-user').indexOf(search) > -1 || $(this).find('td.name span').html().indexOf(search) > -1) {
                        $(this).toggle(true);
                        found++;
                    }
                });

                if (found === 0) {
                    $('#empty').toggle(true);
                }

            } else {
                document.location.hash = '#dashboard';
                $('#empty').toggle(false);
                $('#all-stages').find('tr.stage' + (activeFilter === 'all' ? '' : '.' + activeFilter)).toggle(true);
            }

        }
    },
    feedback: {
        init: function () {
            $('#feedback-submit').bind('click', function (e) {
                e.preventDefault();
                var text = $('#feedback-text');
                if (text.val() !== "") {
                    $.post("/feedback", { message: text.val()})
                        .done(function () {
                            text.parent().parent().prepend("<div class=\"alert alert-success\">Thanks.</div>");
                            text.val("");
                        })
                        .fail(function () {
                            text.parent().parent()
                                .prepend("<div class=\"alert alert-danger\">Opps, something wen't wrong =(.</div>");
                        });
                }
            });
        }
    },


    interval: {
        withCounter: function (count) {
            return 5000;
        }
    }

};
dashboard.init();
