require.config({
    baseUrl: "js/app",
    paths: {
        "jquery": "../lib/jquery-1.11.0.min",
        "bootstrap": "../lib/bootstrap.min"
    },
    shim: {
        bootstrap: {
            deps: ['jquery']
        }
    }
});
requirejs(['main']);
