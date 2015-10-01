Driftwood.setServerPath("/error/?payload=");
Driftwood.exceptionLevel("DEBUG");
var log = log || {};
log = {
    logger: {},

    init: function () {
        logger = new Driftwood.logger();
        logger.setServerPath("/error/?payload=");
        logger.exceptionLevel("DEBUG");
    },

    error: function (payload) {
        logger.error(payload);
    },
    debug: function (payload) {
        logger.debug(payload);
    }
}
