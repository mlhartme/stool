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
        }
    },

    stages: {
        init: function () {
            dashboard.stages.reload();
            $('[data-action]').on('click', dashboard.stages.action);
            $('#loading').remove();
        },


        reload: function () {
            $.ajax('/stages', {
                dataType: "html",
                success: function (data) {
                    var allStages = $('#all-stages');
                    var done = [];
                    $(data).each(function (i, newTr) {
                        var name;
                        var oldTr;
                        var actions;

                        name = $(newTr).attr("data-name");
                        done.push(name);
                        oldTr = allStages.find('[data-name="' + name + '"]');
                        if (oldTr.length === 0) {
                            // new stage
                            $(newTr).find('[data-action]').off('click', dashboard.stages.action);
                            allStages.append(newTr);
                            $(newTr).find('[data-action]').on('click', dashboard.stages.action);
                        } else if ($(oldTr).attr("data-content-hash") !== $(newTr).attr("data-content-hash")) {
                            // updated stage
                            $(newTr).find('[data-action]').off('click', dashboard.stages.action);
                            oldTr.replaceWith(newTr);
                            $(newTr).find('[data-action]').on('click', dashboard.stages.action);
                        } else {
                            // no changes
                        }
                    });
                    $(allStages).children("tr").each(function (i, tr) {
                        var name;

                        name = $(tr).attr("data-name");
                        if (!done.includes(name)) {
                            $(tr).find('[data-action]').off('click', dashboard.stages.action);
                            tr.remove();
                        }
                    });
                    $('[data-toggle="popover"]').popover();
                    dashboard.stages.filter();
                }
            });
        },

        action: function () {
            var stage, action, arguments, i, url, box;
            if ($(this).parent().hasClass('disabled') && $(this).attr('data-stage') === null) {
                return false;
            }

            stage = $(this).attr('data-stage');
            action = $(this).attr('data-action');
            arguments = $(this).attr('data-arguments');

            url = "/api/stages/" + stage + "/" + action;
            $('#' + stage + ' a.action').addClass('disabled');
            if (arguments != null) {
                url = url + "?" + arguments;
            }
            box = $('#console');
            box.modal('show');
            box.find('.modal-header').html("<h4>" + action + " " + stage + "</h4>");
            $.post(url).fail(function (r) {
                dashboard.stages.reload();
                box.find('.modal-body').html('<p>failed: ' + r + '</p>');
            }).done(function (r) {
                // TODO
                // from https://stackoverflow.com/questions/51637199/bootstrap-4-open-modal-a-close-modal-a-open-modal-b-a-not-closing
                setTimeout( function() { box.modal("hide"); }, 500 );
                dashboard.stages.reload();
            });
            return false;
        },

        filter: function () {
            var search = $('#search').val().toLowerCase();
            var category = $('#category').val().toLowerCase();

            found = 0;
            $('#all-stages').find('tr.stage').each(function (idx, tr) {
                $(this).toggle(true);
                found++;
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
