var exec = require('cordova/exec');

module.exports = {

    showPage: function (config, successCallback, errorCallback) {
        var params = [
                'closeOnRequestURL', 'closeOnReturnURL', 'closeCompareType', 'fancyCloseButton',
                'scalePageToFit', 'script', 'shouldStopOnPost', 'showToolbar', 'showToolbarWhileLoading',
                'showOpenInExternal', 'url', 'useAsync', 'user', 'password', 'proxy'
        ].map(function(configName){
            return config[configName];
        });

        exec(successCallback, errorCallback, 'InternalBrowser', 'show', [params]);
    },

    dismiss: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'InternalBrowser', 'dismiss', []);
    },

    refresh: function(config, successCallback, errorCallback) {
        var params = {
            url: config.url
        };

        exec(successCallback, errorCallback, 'InternalBrowser', 'refresh', [params]);
    }

};