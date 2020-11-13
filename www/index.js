const MODULE_NAME = 'CordovaAndroidScreenshare';

module.exports = {
  startScreenshare: (callback, fps, compression, title, text) => {
    cordova.exec(
      response => callback(null, response),
      error => callback(error),
      MODULE_NAME,
      'startProjection',
      [fps, compression, title, text]
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
  ping: (callback) => {
    cordova.exec(response => callback(response), null, MODULE_NAME, 'ping', null)
  }
}