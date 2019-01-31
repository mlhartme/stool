<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">

  <link rel="stylesheet" href="/webjars/bootstrap/4.2.1/css/bootstrap.min.css" >
  <link rel="stylesheet" href="css/font-awesome.min.css" type="text/css" id="css">
  <link rel="stylesheet" href="css/style.css" type="text/css"/>

  <title>Stool Dashboard</title>
</head>
<body>
<div id="info">
</div>
<div class="container">
    <div class="navbar navbar-default">
        <div class="navbar-header">
            <a class="navbar-brand" href="#dashboard">Dashboard</a>
        </div>
        <div class="navbar-collapse collapse navbar-responsive-collapse">
            <ul class="nav navbar-nav">
                <li class="dashboard tab"><a href="#dashboard"><i class="fa fa-bookmark"></i> Dashboard</a></li>
                <li class="feedback tab"><a href="#feedback"><i class="fa fa-comments"></i> Feedback</a></li>
                <!--<li class="user-settings tab"><a href="#user-settings"><i class="fa fa-gears"></i> Settings</a></li>-->
            </ul>
            <ul class="nav navbar-nav navbar-right">
                <p class="navbar-text">Hi, ${username}</p>
            </ul>
        </div>
    </div>
    <div class="main-content">
        <div class="progress">
            <div class="progress-bar progress-bar-success" style="width: 35%"></div>
            <div class="progress-bar progress-bar-warning" style="width: 20%"></div>
            <div class="progress-bar progress-bar-danger" style="width: 10%"></div>
        </div>
        <!-- DASHBOARD -->
        <div class="dashboard content" data-title="dashboard">
            <ul class="breadcrumb">
                <li class="active">Dashboard</li>
            </ul>
            <h3>Dashboard</h3>
            <ul class="nav nav-tabs" style="margin-bottom: 15px;">
                <li class="active"><a href="#all" data-toggle="tab">All <span class="badge" id="all-count">0</span></a></li>
                <li><a href="#trunks" data-toggle="tab">Trunks <span class="badge" id="trunks-count">0</span></a></li>
                <li><a href="#branches" data-toggle="tab">Branches <span class="badge" id="branches-count">0</span></a></li>
                <li><a href="#workspaces" data-toggle="tab">Workspaces <span class="badge"
                                                                             id="workspaces-count">0</span></a></li>
                <li class="nav-search"><input tabindex="1" class="form-control col-lg-8" placeholder="Search Stage / Application / User" type="text"
                                              size="35" id="search"/></li>
            </ul>

            <div id="myTabContent" class="tab-content">
                <table class="table table-striped table-hover" width="100%">
                    <thead>
                    <tr>
                        <th style="width: 5%">Status</th>
                        <th style="width: 15%">Name</th>
                        <th style="width: 15%">Application(s)</th>
                        <th style="width: 10%">Expires</th>
                        <th style="width: 10%">Last modified by</th>
                        <th colspan="6" style="width: 6%">Options</th>
                    </tr>
                    </thead>
                    <tbody id="all-stages">

                    <tr>
                        <td colspan="6" id="loading" align="center">
                            <i class="fa fa-spinner fa-spin"></i> Loading Stages ...
                        </td>
                        <td colspan="6" id="empty" align="center" class="hidden">
                            <i class="fa fa-info"></i> No stages found.
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
        <!-- FEEDBACK -->
        <div class="feedback content hidden" data-title="feedback">
            <ul class="breadcrumb">
                <li><a href="#dashboard">Dashboard</a></li>
                <li class="active">Feedback</li>
            </ul>
            <h3>Feedback</h3>

            <form class="form-horizontal" action="#feedback">
                <fieldset>
                    <div class="form-group">
                        <label for="feedback-text" class="col-lg-2 control-label">Message</label>

                        <div class="col-lg-10">
                            <textarea class="form-control" rows="3" id="feedback-text"></textarea>
                        </div>
                    </div>
                    <div class="form-group">
                        <div class="col-lg-10 col-lg-offset-2">
                            <button type="submit" class="btn btn-primary" id="feedback-submit">Submit</button>
                        </div>
                    </div>
                </fieldset>
            </form>
        </div>

        <!-- 404 -->
        <div class="error content hidden" data-title="error page">
            <ul class="breadcrumb">
                <li><a href="#dashboard">Dashboard</a></li>
                <li>System</li>
                <li class="active">404</li>
            </ul>
            <h3>Upps! Something went wrong :-(</h3>

            <div class="alert alert-dismissable alert-danger">
                <strong>Page not found!</strong>
            </div>
        </div>
    </div>

    <div class="modal fade">
        <div class="modal-dialog-lg">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                    <h4 class="modal-title">Modal title</h4>
                </div>
                <div class="modal-body">
                    <pre class="shell"></pre>

                    <p><i class="fa fa-spinner fa-spin"></i></p>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default close" data-dismiss="modal">Close</button>
                </div>
            </div>
            <!-- /.modal-content -->
        </div>
        <!-- /.modal-dialog -->
    </div>
</div>
  <script type="text/javascript" src="/webjars/jquery/3.3.1-1/jquery.min.js" ></script>
  <script type="text/javascript" src="/webjars/popper.js/1.14.3/umd/popper.min.js"></script>
  <script type="text/javascript" src="/webjars/bootstrap/4.2.1/js/bootstrap.min.js"></script>
  <script>
$(function () {
  $('[data-toggle="tooltip"]').tooltip()
})
</script>
<script type="text/javascript" src="js/main.js"></script>
</body>
</html>
