module.exports = {
  startScreenshare: (callback) => {
    cordova.exec(response => { callback(null, response) }, error => callback(error), "CordovaAndroidScreenshare", 'startProjection', null)
  },
  stopScreenshare: (callback) => {
    cordova.exec(response => { callback(null, response) }, error => callback(error), "CordovaAndroidScreenshare", 'stopProjection', null)
  }
}