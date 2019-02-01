<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">

  <link rel="stylesheet" href="/webjars/bootstrap/4.2.1/css/bootstrap.min.css" >
  <link rel="stylesheet" href="https://use.fontawesome.com/releases/v5.7.0/css/all.css" integrity="sha384-lZN37f5QGtY3VHgisS14W3ExzMWZxybE1SJSEsQp9S+oqd12jhcu+A56Ebc1zFSJ" crossorigin="anonymous">
  <link rel="stylesheet" href="css/style.css" type="text/css"/>

  <title>Stool Dashboard</title>
</head>
<body>
<div class="container">
    <div class="navbar navbar-expand-lg navbar-light bg-light">
        <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarNavAltMarkup" aria-controls="navbarNavAltMarkup" aria-expanded="false" aria-label="Toggle navigation">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="navbarNavAltMarkup">
          <div class="navbar-nav">
            <a href="#dashboard"><i class="fa fa-bookmark"></i> Dashboard</a>
            <a href="#feedback"><i class="fa fa-comments"></i> Feedback</a>
          </div>
          <div class="navbar-nav ml-auto">
            <span class="navbar-text">Hi, ${username}</span>
          </div>
        </div>
    </div>
    <div class="main-content">
        <div class="dashboard content" data-title="dashboard">
            <ul class="breadcrumb">
                <li class="active">Dashboard</li>
            </ul>
            <ul class="nav nav-tabs" style="margin-bottom: 15px;">
                <li class="nav-item"><a class="nav-link active" href="#all" data-toggle="tab">All <span class="badge" id="all-count">0</span></a></li>
                <li class="nav-item"><a class="nav-link" href="#trunks" data-toggle="tab">Trunks <span class="badge" id="trunks-count">0</span></a></li>
                <li class="nav-item"><a class="nav-link" href="#branches" data-toggle="tab">Branches <span class="badge" id="branches-count">0</span></a></li>
                <li class="nav-item"><a class="nav-link" href="#workspaces" data-toggle="tab">Workspaces <span class="badge" id="workspaces-count">0</span></a></li>
                <li class="nav-item form-inline">
                  <input tabindex="1" class="form-control form-control-sm" placeholder="Search Stage / Application / User" type="search" size="35" id="search"/>
                </li>
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
                        <td colspan="6" id="empty" align="center" style="display:none;">
                            <i class="fa fa-info"></i> No stages found.
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
        <!-- FEEDBACK -->
        <div class="feedback content" style="display:none;" data-title="feedback">
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
        <div class="error content" style="display:none;" data-title="error page">
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

    <div class="modal fade" tabindex="-1" role="dialog" aria-hidden="true">
        <div class="modal-dialog modal-xl">
            <div class="modal-content">
                <div class="modal-header">
                    <h4 class="modal-title">Modal title</h4>
                </div>
                <div class="modal-body">
                    <pre class="shell"></pre>

                    <p><i class="fa fa-spinner fa-spin"></i></p>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-dismiss="modal" aria-label="Close">Close</button>
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
