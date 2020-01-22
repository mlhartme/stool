$(function () {
  $('[data-toggle="tooltip"]').tooltip()
})
$('#logs').on('show.bs.modal', function (event) {
  var link = $(event.relatedTarget);
  var stage = link.data('stage');

  $.ajax('/api/stages/' + stage + '/logs', {
        dataType: "json",
        success: function (array) {
            var dest = $('#logs').find('.modal-body');
            console.log('success ' + array);
            $.each(array, function(index, element) {
                dest.append($('<a href="/api/stages/' + stage + '/logs/' + element + '">' + element + '</a><br/>'));
            });
        }
    });
})

// https://gist.github.com/hyamamoto/fd435505d29ebfa3d9716fd2be8d42f0
hashCode = function(s) {
  var h = 0, l = s.length, i = 0;
  if ( l > 0 )
    while (i < l)
      h = (h << 5) - h + s.charCodeAt(i++) | 0;
  return h;
};

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
            $.ajax('/api/stages?select=apps,comment,expire,last-modified-by,running,urlmap', {
                dataType: "json",
                success: function (data) {
                    var allStages = $('#all-stages');
                    var done = [];
                    $.each(data, function (name, status) {
                        var name;
                        var oldTr;
                        var up;
                        var htmlSt;
                        var htmlName;
                        var htmlUrls;
                        var htmlRestart;
                        var htmlMenu;
                        var newTr;
                        var newHash;
                        var actions;

                        done.push(name);
                        oldTr = allStages.find('[data-name="' + name + '"]');
                        up = status.running !== "";
                        htmlSt = "<td class='status'>\n" +
                                 "  <div class='status badge badge-" + (up ? "success" : "danger") + "'>\n" +
                                 "    <span>" + (up ? "up" : "down") + "</span>\n"
                                 "  </div>\n" +
                                 "</td>";
                        htmlName = "<td class='name'>\n" +
                                   "  <span data-container='body' data-toggle='popover' data-placement='bottom' " +
                                   "        data-content='" + (status.comment !== "" ? status.comment : "(no comment)") + "' " +
                                   "        data-trigger='hover'>" + name + "</span></td>";
                        htmlUrls = "";
                        $.each(status.urlmap, function (app, url) {
                            htmlUrls = htmlUrls + "<a href='" + url + "' target='_blank'>" + app + "</a><br/>\n"
                        })
                        htmlUrls = "<td class='links'>" + htmlUrls + "</td>\n"
                        htmlRestart = " <td class='action restart'>\n" +
                                      "   <button class='btn btn-light btn-sm' type='button' data-action='restart' data-stage='" + name + "'>\n" +
                                      "     <span style='white-space: nowrap'><i class='fas fa-sync'></i> Restart</span>\n" +
                                      "   </button>\n"
                                      " </td>"
                        htmlMenu = "<td class='action'>\n" +
                                   "  <div class='dropdown'>\n" +
                                   "    <button type='button' class='btn btn-light btn-sm dropdown-toggle' data-toggle='dropdown' aria-haspopup='true' aria-expanded='false'><span style='white-space: nowrap'>More</span></button>\n" +
                                   "    <div class='dropdown-menu'>\n" +
                                   "      <a class='dropdown-item' href='#dashboard' data-action='start' data-stage='" + name + "'>Start</a>\n" +
                                   "      <a class='dropdown-item' href='#dashboard' data-action='stop' data-stage='" + name + "'>Stop</a>\n" +
                                   "      <a class='dropdown-item' href='#dashboard' data-action='set-properties' data-arguments='expire=%2B7' data-stage='" + name + "'>Expire in 1 week</a>\n" +
                                   "      <a class='dropdown-item' href='mailto:?subject=Stage&body=stage.sharedText(urlMap)}' th:disabled='${urlMap == null}' >\n" +
                                   "        <span style='white-space: nowrap'><i class='fas fa-share'></i> Share</span></a>\n"
                                   "      <a class='dropdown-item' data-toggle='modal' data-target='#logs' data-stage='" + name + "'>Log files ...</a>\n" +
                                   "      <a class='dropdown-item' href='#dashboard' data-action='remove' data-arguments='stop&batch' data-stage='" + name + "'>Remove</a>\n"
                                   "    </div>\n" +
                                   "  </div>\n" +
                                   "</td>\n"

                        newTr = "<tr class='stage' data-name='" + name + "'>\n" +
                                   htmlSt +
                                   htmlName +
                                   htmlUrls +
                                   "<td>" + status.apps + "</td>\n" +
                                   "<td>" + status.expire + "</td>\n" +
                                   "<td>" + status["last-modified-by"] + "</td>\n" +
                                   htmlRestart +
                                   htmlMenu
                                   "</tr>";
                        newHash = String(hashCode(newTr));
                        if (oldTr.length === 0) {
                            // new stage
                            console.log("append " + name)
                            var appended = allStages.append(newTr);
                            appended = appended.find('[data-name="' + name + '"]');
                            appended.attr("data-content-hash", newHash);
                            appended.find('[data-action]').on('click', dashboard.stages.action);
                        } else if ($(oldTr).attr("data-content-hash") !== newHash) {
                            // updated stage
                            console.log("replace " + name)
                            oldTr.replaceWith(newTr); // replaceWith returns the removed elements
                            replaced = allStages.find('[data-name="' + name + '"]');
                            replaced.attr("data-content-hash", newHash);
                            replaced.find('[data-action]').on('click', dashboard.stages.action);
                        } else {
                            console.log("same " + name)
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

            $('#' + stage + ' a.action').addClass('disabled');
            box = $('#progress');
            box.modal('show');
            box.find('.modal-header').html("<h4>" + action + " " + stage + "</h4>");

            if (action == "restart") {
                $.post("/api/stages/" + stage + "/stop").fail(function (r) {
                    box.find('.modal-body').html('<p>failed: ' + r + '</p>');
                    dashboard.stages.reload();
                }).done(function (r) {
                    $.post("/api/stages/" + stage + "/start").fail(function (r) {
                        box.find('.modal-body').html('<p>failed: ' + r + '</p>');
                        dashboard.stages.reload();
                    }).done(function (r) {
                        // TODO: doesn't work if the browser is extremely slow (or busy)
                        // from https://stackoverflow.com/questions/51637199/bootstrap-4-open-modal-a-close-modal-a-open-modal-b-a-not-closing
                        setTimeout( function() { box.modal("hide"); }, 500 );
                        dashboard.stages.reload();
                    });
                });
            } else {
                url = "/api/stages/" + stage + "/" + action;
                if (arguments != null) {
                    url = url + "?" + arguments;
                }
                $.post(url).fail(function (r) {
                    box.find('.modal-body').html('<p>failed: ' + r + '</p>');
                    dashboard.stages.reload();
                }).done(function (r) {
                    // TODO: doesn't work if the browser is extremely slow (or busy)
                    // from https://stackoverflow.com/questions/51637199/bootstrap-4-open-modal-a-close-modal-a-open-modal-b-a-not-closing
                    setTimeout( function() { box.modal("hide"); }, 500 );
                    dashboard.stages.reload();
                });
            }
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
                    $.post("/ui/feedback", { message: text.val()})
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
