require.config({
    baseUrl: "js/app",
    paths: {
        "jquery": "../lib/jquery-1.11.0.min",
        "bootstrap": "../lib/bootstrap.min",
        "shepherd": "../lib/shepherd",
        "driftwood": "../lib/driftwood.min"
    },
    shim: {
        bootstrap: {
            deps: ['jquery']
        },
        logging: {
            deps: ['driftwood']
        }
    }
});
requirejs(['main']);
