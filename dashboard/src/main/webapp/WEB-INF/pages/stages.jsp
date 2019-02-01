<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="stages" scope="request" type="java.util.List<net.oneandone.stool.dashboard.StageInfo>"/>
<c:forEach var="stage" items="${stages}">
    <tr id="${stage.name}" data-hash="${stage.hash}" data-origin="${stage.origin}" data-user="${stage.lastModifiedBy}"
        data-status="${stage.running}" data-updateAvailable="${stage.updateAvailable}" class="stage ${stage.category}">

        <td class="status">
            <div class="status label label-${stage.state}">
              <span data-container="body" data-toggle="popover" data-placement="bottom" data-content="${stage.origin}"
                    data-trigger="hover">${stage.running}</span></div>
        </td>
        <td class="name"><span data-container="body" data-toggle="popover" data-placement="bottom" data-content="${stage.comment}"
                               data-trigger="hover">${stage.name}</span></td>
        <td class="links">
            <c:choose>
                <c:when test="${stage.urls != null}">
                    <c:forEach var="url" items="${stage.urls}">
                        <a href="${url.value}" target="_blank">${url.key}</a><br/>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    -
                </c:otherwise>
            </c:choose>

        </td>
        <td class="expire ${stage.expire.isExpired() ? "expired" : ""}">${stage.expire}</td>
        <td class="user">${stage.lastModifiedBy}</td>
        <td class="option refresh">
            <button class="btn ${stage.updateAvailable ? 'btn-primary' : 'btn-secondary'} btn-sm"
                    type="button" data-action="refresh" data-estimate="${stage.stats.avgRefresh}" data-stage="${stage.name}"
                    data-arguments="-build,-autorestart"  <c:if test="${stage.updateAvailable}">data-container="body" data-toggle="popover" data-placement="right"
                    data-title="${stage.changes.exception ? "Warning" : "Update Available"}" data-trigger="hover" data-html="true"
                    data-content="${stage.changes}"</c:if>><i class="fas fa-refresh"></i> Refresh</button>
        <td class="option share">
            <a role="button" href="mailto:?subject=Stage&body=${stage.shareText}" ${stage.urls == null ? "disabled=\"disabled\"" :""}
               class="btn btn-secondary btn-sm share"><i class="fas fa-share"></i> Share</a>
        </td>
        <td class="option">
            <div class="dropdown">
                <button type="button" class="btn btn-secondary btn-sm dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">Actions</button>
                <div class="dropdown-menu">
                    <a class="dropdown-item" href="#dashboard" data-estimate="${stage.stats.avgStart}" data-action="start" data-stage="${stage.name}">Start</a>
                    <a class="dropdown-item" href="#dashboard" data-estimate="${stage.stats.avgStop}" data-action="stop" data-stage="${stage.name}">Stop</a>
                    <a class="dropdown-item" href="#dashboard" data-estimate="${stage.stats.avgRestart}" data-action="restart" data-stage="${stage.name}">Restart</a>
                    <a class="dropdown-item" href="#dashboard" data-estimate="${stage.stats.avgBuild}" data-action="build" data-stage="${stage.name}">Build</a>
                    <a class="dropdown-item" href="#dashboard" data-action="refresh" data-arguments="-restore" data-stage="${stage.name}">Rollback</a>
                    <a class="dropdown-item" href="#dashboard" data-action="cleanup" data-stage="${stage.name}">Cleanup</a>
                    <a class="dropdown-item" href="#dashboard" data-action="config" data-arguments="expire=+7" data-stage="${stage.name}">Expire in 1 week</a>
                </ul>
            </div>
        </td>
    </tr>
</c:forEach>