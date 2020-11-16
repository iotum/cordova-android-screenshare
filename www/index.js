const MODULE_NAME = 'CordovaAndroidScreenshare';

module.exports = {
  startScreenshare: (callback, fps, compression) => {
    cordova.exec(
      response => callback(null, response),
      error => callback(error),
      MODULE_NAME,
      'startProjection',
      [fps, compression]
    )
  },
  stopScreenshare: (callback) => {
    cordova.exec(
      response => callback(null, response),
      error => callback(error),
      MODULE_NAME,
      'stopProjection',
      null
    )
  },
  disableWebViewOptimizations: () => {
    cordova.exec(null, null, 'MODULE_NAME', 'disableWebViewOptimizations', null);
  },
  ping: (callback) => {
    cordova.exec(response => callback(response), null, MODULE_NAME, 'ping', null)
  }
}