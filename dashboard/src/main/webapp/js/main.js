var dashboard = dashboard || {};

dashboard = {
    init: function () {
        this.bootstrap.init();
        this.stages.init();
        this.feedback.init();
    },

    bootstrap: {
        init: function () {
            $('#search').on('keyup', dashboard.stages.filter);
            $('#category').on('change', dashboard.stages.filter);
            $('.modal').on('hide.bs.modal', function () {
                $(".shell").html("");
                dashboard.stages.reload();
            });
        }
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
            }, 5000);
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
                    dashboard.stages.filter();
                }
            });
        },

        action: function () {
            var stage, action, arguments, p, i, url;
            if ($(this).parent().hasClass('disabled') && $(this).attr('data-stage') === null) {
                return false;
            }

            stage = $(this).attr('data-stage');
            action = $(this).attr('data-action');
            arguments = $(this).attr('data-arguments');
            p = $(this).parent().parent();

            url = "/stages/" + stage + "/" + action;
            $('#' + stage + ' a.action').addClass('disabled');

            $(p).find('.fa').toggle(true);
            $(p).find('.badge').attr('class', 'badge badge-primary').html('n/a');

            if (arguments != null) {
                url = url + "/" + arguments;
            }
            $('#console').modal();
            $.post(url).fail(function (r) {
                $(p).find('.fa').toggle(false);
                $(p).find('.badge').attr('class', 'badge badge-warning').html('broken: ' + r);
                $(p).addClass('warning');
                log.error(r);
            }).done(function (r) {
                var spinner;
                $('.modal-body .shell').html("").show();
                spinner = '.fa-spinner';
                $(spinner).show();
                dashboard.stages.showLog('.modal-body .shell', r, 0, spinner, '.modal');

            });
            return false;
        },

        showLog: function (element, id, index, spinner, parent) {
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

        filter: function () {
            var search = $('#search').val();
            var category = $('#category').val();

            console.log("filter '" + search + "' in category " + category);

            found = 0;
            $('#all-stages').find('tr.stage').each(function (idx, tr) {
                if (($(tr).attr('data-origin').indexOf(category) > -1) && ($(tr).attr('id').indexOf(search) > -1 || $(tr).attr('data-user').indexOf(search) > -1)) {
                    $(this).toggle(true);
                    found++;
                } else {
                    $(this).toggle(false);
                }
            });

            $('#empty').toggle(found == 0);
        }
    },

    feedback: {
        init: function () {
            $('#feedback-submit').on('click', function (e) {
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
                                .prepend("<div class=\"alert alert-danger\">Opps, something went wrong =(.</div>");
                        });
                }
            });
        }
    }
};
dashboard.init();
