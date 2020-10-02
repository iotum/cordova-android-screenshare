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
  }
}